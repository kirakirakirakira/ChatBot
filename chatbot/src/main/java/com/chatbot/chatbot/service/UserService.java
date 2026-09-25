package com.chatbot.chatbot.service;

import com.chatbot.chatbot.auth.CurrentUser;
import com.chatbot.chatbot.auth.TokenService;
import com.chatbot.chatbot.auth.UserStatus;
import com.chatbot.chatbot.dto.ChangePasswordRequest;
import com.chatbot.chatbot.dto.LoginRequest;
import com.chatbot.chatbot.dto.LoginResponse;
import com.chatbot.chatbot.dto.UpdateSystemPromptRequest;
import com.chatbot.chatbot.dto.UserVO;
import com.chatbot.chatbot.entity.User;
import com.chatbot.chatbot.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    /**
     * 用户名不存在时也拿它做一次 BCrypt 比对：BCrypt 故意做慢，如果「用户不存在」直接返回，
     * 攻击者光看响应快慢就能筛出存在的用户名。哈希在构造时算一次，不写死在代码里。
     */
    private final String dummyHash;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       TokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
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

    /** 全部用户。权限由 UserController 上的 @RequireAdmin 保证，这里不重复判断；systemPrompt 一律不带出。 */
    public List<UserVO> list() {
        return userRepository.findAllByOrderByIdAsc().stream()
                .map(u -> UserVO.from(u, false))
                .toList();
    }

    /**
     * 改自己的系统提示词：目标用户 id 只来自 token，和改密码同一个口径。
     * 全空白视为清除（存 NULL），前端「清空」按钮不用单独走一个接口。
     */
    @Transactional
    public UserVO updateSystemPrompt(CurrentUser currentUser, UpdateSystemPromptRequest request) {
        User user = requireUser(currentUser.id());
        String prompt = (request.systemPrompt() == null) ? null : request.systemPrompt().strip();
        user.setSystemPrompt((prompt == null || prompt.isEmpty()) ? null : prompt);
        userRepository.save(user);
        return UserVO.from(user);
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

    private User requireUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在: " + id));
    }
}