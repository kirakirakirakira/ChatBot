package com.chatbot.chatbot.service;

import com.chatbot.chatbot.auth.CurrentUser;
import com.chatbot.chatbot.auth.TokenService;
import com.chatbot.chatbot.auth.UserStatus;
import com.chatbot.chatbot.dto.ChangePasswordRequest;
import com.chatbot.chatbot.dto.LoginRequest;
import com.chatbot.chatbot.dto.LoginResponse;
import com.chatbot.chatbot.dto.UpdateProfileRequest;
import com.chatbot.chatbot.dto.UpdateSystemPromptRequest;
import com.chatbot.chatbot.dto.UserProfileStatsVO;
import com.chatbot.chatbot.dto.UserVO;
import com.chatbot.chatbot.entity.User;
import com.chatbot.chatbot.repository.AttachmentRepository;
import com.chatbot.chatbot.repository.ConversationRepository;
import com.chatbot.chatbot.repository.MessageRepository;
import com.chatbot.chatbot.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 「管自己」的那一半用户业务：登录、/me、改密码、改人设、改资料、看自己的使用统计。
 * 「管别人」的全部在 {@link AdminUserService}。
 * <p>
 * 所有方法的目标用户 id 只来自 {@link CurrentUser}（token 解析结果），一律不接受请求体传 userId：
 * 只要有一个入口能从请求里拿到 id，它就是越权改别人的洞。
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final AttachmentRepository attachmentRepository;

    /**
     * 用户名不存在时也拿它做一次 BCrypt 比对：BCrypt 故意做慢，如果「用户不存在」直接返回，
     * 攻击者光看响应快慢就能筛出存在的用户名。哈希在构造时算一次，不写死在代码里。
     */
    private final String dummyHash;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       TokenService tokenService,
                       ConversationRepository conversationRepository,
                       MessageRepository messageRepository,
                       AttachmentRepository attachmentRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.attachmentRepository = attachmentRepository;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * 登录。@Transactional 是给 touchLastLogin() 那条 @Modifying 更新用的——
     * 修改类 JPQL 没有事务会直接抛 TransactionRequiredException。
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username().trim()).orElse(null);
        String storedHash = (user == null) ? dummyHash : user.getPassword();
        if (!passwordEncoder.matches(request.password(), storedHash)) {
            // 「用户不存在」和「密码错」返回同一句话，不给用户名枚举留口子
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        if (user == null) {
            // 理论不可达：密码匹配上了一个随机 UUID 的哈希。兜成和上面同一句，不新增文案分支
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        // 先验密码、再报禁用：顺序反了的话，「这个用户名存在但被禁用了」就成了一个可枚举的信息
        if (!UserStatus.isEnabled(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "账号已被禁用，请联系管理员");
        }
        userRepository.touchLastLogin(user.getId(), LocalDateTime.now());
        return issueSession(user);
    }

    public UserVO me(CurrentUser currentUser) {
        return UserVO.from(requireUser(currentUser.id()));
    }

    /**
     * 改自己的系统提示词：目标用户 id 只来自 token，和改密码同一个口径。
     * 全空白视为清除（存 NULL），前端「清空」按钮不用单独走一个接口。
     */
    @Transactional
    public UserVO updateSystemPrompt(CurrentUser currentUser, UpdateSystemPromptRequest request) {
        User user = requireUser(currentUser.id());
        user.setSystemPrompt(blankToNull(request.systemPrompt()));
        userRepository.save(user);
        return UserVO.from(user);
    }

    /**
     * 改自己的资料（昵称 / 邮箱 / 手机号）。
     * <p>
     * **不换发 token、也不动 passwordChangedAt**：改资料不是安全事件，不该把人踢下线
     * （改密码和强制下线才需要作废登录态，语义刻意分开）。
     * 返回整份 UserVO，前端拿它覆盖本地登录态，省一次 /me。
     * <p>
     * 不做「昵称唯一」约束：昵称是展示名，两个人都叫「小王」完全正常；
     * 唯一的登录标识是 username，那个已经有唯一索引了。
     */
    @Transactional
    public UserVO updateProfile(CurrentUser currentUser, UpdateProfileRequest request) {
        User user = requireUser(currentUser.id());
        user.setNickname(blankToNull(request.nickname()));
        user.setEmail(blankToNull(request.email()));
        user.setPhone(blankToNull(request.phone()));
        userRepository.save(user);
        return UserVO.from(user);
    }

    /**
     * 我的使用统计。只读事务：三个 count 都是独立查询，不需要写。
     * 先 requireUser 一次：账号刚被管理员删掉时给 404，而不是返回三个 0 让人以为数据没了。
     */
    @Transactional(readOnly = true)
    public UserProfileStatsVO stats(CurrentUser currentUser) {
        Long ownerId = requireUser(currentUser.id()).getId();
        return new UserProfileStatsVO(conversationRepository.countByOwnerId(ownerId),
                messageRepository.countByOwnerId(ownerId),
                attachmentRepository.countByOwnerId(ownerId));
    }

    /**
     * 「退出所有设备」：把 passwordChangedAt 推到当前时间，本人手里所有 token（**包括正在用的这个**）
     * 下一个请求就 401。复用改密码那套失效机制，不需要会话表。
     * <p>
     * 和管理端的「强制下线」是同一个动作、不同的入口：那边是管理员怀疑 token 泄漏时踢别人，
     * 这边是本人怀疑自己在别处忘了退出时踢自己。所以它**不改密码、不置 mustChangePassword**——
     * 本人马上要用原密码重新登回来，被强制改密会莫名其妙。
     * <p>
     * 前端调用成功后必须自己 clearSession()：后端不会替它清 localStorage，
     * 而这个 token 已经废了，不清就是停在一个点什么都 401 的死页面上。
     */
    @Transactional
    public void revokeOwnSessions(CurrentUser currentUser) {
        User user = requireUser(currentUser.id());
        user.setPasswordChangedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    /**
     * 改自己的密码：目标用户 id 只来自 token（CurrentUser），不接受请求体传 userId，否则就是越权改别人密码的洞。
     * 成功后换发新 token：旧 token 的 iat 早于刚写下的 password_changed_at，会被 AuthInterceptor 判为失效。
     */
    @Transactional
    public LoginResponse changePassword(CurrentUser currentUser, ChangePasswordRequest request) {
        User user = requireUser(currentUser.id());
        if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "原密码不正确");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新密码不能与原密码相同");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setPasswordChangedAt(LocalDateTime.now());
        // 管理员重置密码留下的「强制改密」标记，在本人真的改过一次之后就完成使命了
        user.setMustChangePassword(Boolean.FALSE);
        userRepository.save(user);
        return issueSession(user);
    }

    private LoginResponse issueSession(User user) {
        return LoginResponse.of(tokenService.issue(user), tokenService.ttlSeconds(), UserVO.from(user));
    }

    /** 全空白（null / "" / "   "）统一存 NULL：库里不该同时存在「没填」和「填了个空」两种状态。 */
    private static String blankToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String stripped = raw.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    private User requireUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在: " + id));
    }
}
