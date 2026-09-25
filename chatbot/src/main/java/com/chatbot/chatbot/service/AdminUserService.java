package com.chatbot.chatbot.service;

import com.chatbot.chatbot.auth.CurrentUser;
import com.chatbot.chatbot.auth.Roles;
import com.chatbot.chatbot.auth.UserStatus;
import com.chatbot.chatbot.dto.AdminUserVO;
import com.chatbot.chatbot.dto.CreateUserRequest;
import com.chatbot.chatbot.dto.PageVO;
import com.chatbot.chatbot.dto.ResetPasswordRequest;
import com.chatbot.chatbot.dto.ResetPasswordResult;
import com.chatbot.chatbot.dto.UpdateRoleRequest;
import com.chatbot.chatbot.dto.UpdateStatusRequest;
import com.chatbot.chatbot.dto.UserAdminOptionsVO;
import com.chatbot.chatbot.entity.AuditAction;
import com.chatbot.chatbot.entity.User;
import com.chatbot.chatbot.repository.AttachmentRepository;
import com.chatbot.chatbot.repository.ConversationRepository;
import com.chatbot.chatbot.repository.MessageRepository;
import com.chatbot.chatbot.repository.UserRepository;
import com.chatbot.chatbot.repository.UserSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * 管理端的用户增删改查。权限由 AdminUserController 上的类级 @RequireAdmin 保证，
 * 这里不重复判断角色（项目铁律：权限只在 AuthInterceptor 一处），只做「管理员之间互相保护」的业务规则。
 * <p>
 * 每个写方法都要 CurrentUser，目的不是鉴权，而是自我保护：管理员在界面上点错一行，
 * 最坏的后果是把自己踢出系统、再也回不来（唯一的管理员被禁用/降级/删除后没人能改回来）。
 * 这类操作一律 400 挡住，比事后写一段「救援 SQL」划算。
 */
@Service
public class AdminUserService {

    /** 单页条数默认值：20 行在一屏里刚好不用滚。 */
    private static final int DEFAULT_PAGE_SIZE = 20;

    /**
     * 单页上限。挡的是 ?size=999999：sys_user 现在不大，但列表是给管理员反复点的，
     * 不设上限等于把分页白做，以后加了「按用户批量导出」这类调用更容易被顺手传个大数。
     */
    private static final int MAX_PAGE_SIZE = 100;

    /** 随机密码长度。16 位在 56 个字符的字母表下远超暴力破解所需，又还抄得动、粘得进聊天框。 */
    private static final int GENERATED_PASSWORD_LENGTH = 16;

    /**
     * 随机密码的字符表：刻意去掉 0/O、1/l/I 这些易混字符。管理员要把这串密码口头或消息转给用户，
     * 少一个「这是零还是欧」的来回就少一次重置。仍然是「字母+数字」。
     */
    private static final String PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    /** SecureRandom 实例本身线程安全，共享一个省掉每次重置密码都重新播种的开销。 */
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 合法 status 取值，直接引用 UserStatus 的常量而不是写 0、1。角色合法性走 Roles.isKnown()（层级模型）。 */
    private static final Set<Integer> KNOWN_STATUSES = Set.of(UserStatus.ENABLED, UserStatus.DISABLED);

    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final AttachmentRepository attachmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminAuditService adminAuditService;

    public AdminUserService(UserRepository userRepository,
                            ConversationRepository conversationRepository,
                            MessageRepository messageRepository,
                            AttachmentRepository attachmentRepository,
                            PasswordEncoder passwordEncoder,
                            AdminAuditService adminAuditService) {
        this.userRepository = userRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.attachmentRepository = attachmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminAuditService = adminAuditService;
    }

    /**
     * 用户列表：关键字 + 角色 + 状态三个筛选器都是可选的，按 id 升序（初始 admin 永远排第一）。
     * 分页参数越界报 400 而不是像消息列表那样静默收敛：那边是聊天翻页、参数错了不该打断阅读，
     * 这边是管理界面，页码写错还悄悄返回第 0 页会让人以为「筛选没生效」。
     */
    public PageVO<AdminUserVO> search(CurrentUser actor, String keyword, Integer role, Integer status, Integer page, Integer size) {
        Pageable pageable = pageable(page, size);
        Page<User> result = userRepository.findAll(UserSpecifications.search(keyword, role, status), pageable);
        // canManage 按操作者层级算：前端据此禁用行内控件，「能不能点」在打开页面时就知道，不用点了才吃 400
        return PageVO.of(result, user -> AdminUserVO.from(user, actor.rank()));
    }

    /** 角色 / 状态下拉的数据源。角色只返回层级低于操作者的那些（见 UserAdminOptionsVO.forActor）。 */
    public UserAdminOptionsVO options(CurrentUser actor) {
        return UserAdminOptionsVO.forActor(actor.rank());
    }

    /**
     * 建号。用户名按「忽略首尾空格」后的值入库，和登录、改密码用的是同一个口径（见 UserService）。
     * 先查一次重名是为了给出准确的「用户名已存在: xxx」；两个管理员同时建同名号的竞态
     * 由唯一索引兜底，翻译成 400 的处理器见 GlobalExceptionHandler。
     */
    @Transactional
    public AdminUserVO create(CurrentUser actor, CreateUserRequest request) {
        String username = request.username().trim();
        int role = (request.role() == null) ? Roles.USER : request.role();
        int status = (request.status() == null) ? UserStatus.ENABLED : request.status();
        requireKnownRole(role);
        requireKnownStatus(status);
        requireAssignableRole(actor, role);
        if (userRepository.findByUsername(username).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名已存在: " + username);
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(role);
        user.setStatus(status);
        // 管理员建号时密码是管理员定的，但不强制本人改：要不要改由管理员口头约定，
        // 「强制改密」这个标记的语义留给「重置密码」那条路（见 resetPassword）
        user.setMustChangePassword(Boolean.FALSE);
        User saved = userRepository.save(user);
        // 审计与业务同事务：建号失败回滚时这条留痕一起回滚，不留假记录
        adminAuditService.record(actor, AuditAction.CREATE_USER, saved.getId(), saved.getUsername(),
                "role=" + role + ", status=" + status);
        return AdminUserVO.from(saved, actor.rank());
    }

    /** 改角色。可以升级也可以降级，唯一不许的是「把自己从管理员降下去」和「降掉最后一个启用的管理员」。 */
    @Transactional
    public AdminUserVO updateRole(CurrentUser actor, Long id, UpdateRoleRequest request) {
        requireKnownRole(request.role());
        User user = requireUser(id);
        int oldRole = user.getRole();
        boolean demotingAdmin = Roles.isAdmin(user.getRole()) && !Roles.isAdmin(request.role());
        if (isSelf(actor, id)) {
            // 自己的角色在界面上是锁死的（select disabled），这里再兜一道：绕过界面直接调接口也改不了
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能修改自己的角色");
        }
        requireCanManage(actor, user);
        requireAssignableRole(actor, request.role());
        // 只有「启用的管理者」才算数：已经被禁用的管理者本来就进不来，不该占着最后一个名额
        if (demotingAdmin && UserStatus.isEnabled(user.getStatus())) {
            requireNotLastEnabledManager(user);
        }
        user.setRole(request.role());
        AdminUserVO updated = AdminUserVO.from(userRepository.save(user), actor.rank());
        adminAuditService.record(actor, AuditAction.UPDATE_ROLE, user.getId(), user.getUsername(),
                "role " + oldRole + " → " + request.role());
        return updated;
    }

    /**
     * 启用 / 禁用。禁用立刻生效：AuthInterceptor 每个请求都回表查状态，
     * 目标用户的下一个请求就是 401，不用等 token 过期。
     */
    @Transactional
    public AdminUserVO updateStatus(CurrentUser actor, Long id, UpdateStatusRequest request) {
        requireKnownStatus(request.status());
        User user = requireUser(id);
        int oldStatus = user.getStatus();
        if (isSelf(actor, id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能修改自己的账号状态");
        }
        requireCanManage(actor, user);
        boolean disablingManager = !UserStatus.isEnabled(request.status())
                && Roles.canAccessAdmin(user.getRole()) && UserStatus.isEnabled(user.getStatus());
        if (disablingManager) {
            requireNotLastEnabledManager(user);
        }
        user.setStatus(request.status());
        AdminUserVO updated = AdminUserVO.from(userRepository.save(user), actor.rank());
        adminAuditService.record(actor, AuditAction.UPDATE_STATUS, user.getId(), user.getUsername(),
                "status " + oldStatus + " → " + request.status());
        return updated;
    }

    /**
     * 重置别人的密码。generate=true 就用 SecureRandom 生成一个 16 位的，明文只在这一次响应里回显
     * （库里只有哈希，之后谁都查不回来，界面上不抄下来就只能再重置一次）。
     * <p>
     * 顺手把 passwordChangedAt 推到当前时间：目标用户手里所有旧 token 的 iat 都早于它，
     * 下一个请求即 401。这是复用「本人改密码」那套失效机制，不需要额外的会话表。
     */
    @Transactional
    public ResetPasswordResult resetPassword(CurrentUser actor, Long id, ResetPasswordRequest request) {
        User user = requireUser(id);
        if (isSelf(actor, id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能重置自己的密码，请用「修改密码」");
        }
        requireCanManage(actor, user);
        boolean generate = Boolean.TRUE.equals(request.generate());
        String plainPassword = generate ? generatePassword() : requireExplicitPassword(request.newPassword());
        user.setPassword(passwordEncoder.encode(plainPassword));
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setMustChangePassword(Boolean.TRUE);
        userRepository.save(user);
        // 明文不进日志、也不进审计：日志会被收集和转发，密码不该出现在里面（安全红线）
        adminAuditService.record(actor, AuditAction.RESET_PASSWORD, user.getId(), user.getUsername(),
                generate ? "生成随机密码，强制下次改密" : "指定新密码，强制下次改密");
        return new ResetPasswordResult(generate ? plainPassword : null, Boolean.TRUE);
    }

    /**
     * 强制下线：只把 passwordChangedAt 推到当前时间，不动密码。
     * 效果是「所有已登录设备下一个请求就 401」，但本人用原密码还能登回来——
     * 和重置密码的区别就在这：怀疑 token 泄漏时用这个，怀疑密码泄漏时才用重置。
     */
    @Transactional
    public void revoke(CurrentUser actor, Long id) {
        User user = requireUser(id);
        if (isSelf(actor, id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能对自己强制下线");
        }
        requireCanManage(actor, user);
        user.setPasswordChangedAt(LocalDateTime.now());
        userRepository.save(user);
        adminAuditService.record(actor, AuditAction.REVOKE_SESSIONS, user.getId(), user.getUsername(),
                "作废全部登录态（不改密码）");
    }

    /**
     * 删号，连他的会话、消息、附件一起删。
     * <p>
     * 刻意不做「软删除」：sys_user 没有 deleted 标记列，conversation.owner_id 是 NOT NULL + RESTRICT，
     * 想把用户行留着就得给所有会话找一个「已注销」的替身用户，反而造出一个谁都能看见的幽灵账号。
     * 真要留痕，应该是审计日志的事，不是靠留着登录行。
     */
    @Transactional
    public void delete(CurrentUser actor, Long id) {
        User user = requireUser(id);
        if (isSelf(actor, id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能删除自己");
        }
        requireCanManage(actor, user);
        requireNotLastEnabledManager(user);
        // 删除顺序不能换：fk_attachment_conversation / fk_message_conversation / fk_conversation_owner
        // 都是 RESTRICT，先删父行会直接撞 errno 1451。和 ConversationService.delete 是同一套顺序，
        // 只是这里按 owner 一次删完，不用逐个会话循环。
        String targetName = user.getUsername();
        attachmentRepository.deleteByOwnerId(id);
        messageRepository.deleteByOwnerId(id);
        conversationRepository.deleteByOwnerId(id);
        userRepository.delete(user);
        // 目标行已经没了，靠删前抓的名字快照认人（审计表不建外键，见 AdminAuditLog 类注释）
        adminAuditService.record(actor, AuditAction.DELETE_USER, id, targetName,
                "级联删除其名下的会话 / 消息 / 附件");
    }

    private Pageable pageable(Integer page, Integer size) {
        int number = (page == null) ? 0 : page;
        int limit = (size == null) ? DEFAULT_PAGE_SIZE : size;
        if (number < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页参数不合法: page 不能小于 0");
        }
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "分页参数不合法: size 需在 1~" + MAX_PAGE_SIZE + " 之间");
        }
        return PageRequest.of(number, limit, Sort.by(Sort.Direction.ASC, "id"));
    }

    private User requireUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在: " + id));
    }

    private static void requireKnownRole(Integer role) {
        if (!Roles.isKnown(role)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未知角色: " + role);
        }
    }

    /**
     * 层级规则的核心：只能管理层级**严格低于**自己的用户。
     * 管理员因此碰不到管理员 / 超级管理员，超级管理员碰不到超级管理员（含自己）。
     * 自己的行另有 isSelf 的专门文案（界面也把控件锁死），所以这里不包含「等于自己」的情况。
     */
    private static void requireCanManage(CurrentUser actor, User target) {
        if (Roles.rank(target.getRole()) >= actor.rank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "只能管理层级低于自己的用户：对方是「" + Roles.label(target.getRole()) + "」");
        }
    }

    /** 建号 / 改角色时，目标角色必须严格低于操作者：不能造一个和自己平级或更高的账号出来。 */
    private static void requireAssignableRole(CurrentUser actor, Integer role) {
        if (Roles.rank(role) >= actor.rank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "不能设置「" + Roles.label(role) + "」：不能创建或指派层级不低于自己的角色");
        }
    }

    private static void requireKnownStatus(Integer status) {
        if (status == null || !KNOWN_STATUSES.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未知状态: " + status);
        }
    }

    /**
     * 「系统不能失去管理者」这道闸，两层：
     * ① 启用的管理者（管理员+超级管理员）至少留一个，否则没人能再进管理端；
     * ② 启用的超级管理员至少留一个，否则没人能再创建 / 提升管理员（层级规则下管理员管不到管理员）。
     * 判定用 count 而不是「除了他还有没有别人」：一条 SQL 就能算，不用把名单拉进内存。
     * <=1 而不是 ==0：此刻目标用户自己还在计数里，他就是那 1 个。
     */
    private void requireNotLastEnabledManager(User target) {
        if (!Roles.canAccessAdmin(target.getRole()) || !UserStatus.isEnabled(target.getStatus())) {
            return;
        }
        long enabledManagers = userRepository.countByRoleAndStatus(Roles.ADMIN, UserStatus.ENABLED)
                + userRepository.countByRoleAndStatus(Roles.SUPER_ADMIN, UserStatus.ENABLED);
        if (enabledManagers <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "系统至少需要保留一个启用的管理员");
        }
        if (Roles.isSuperAdmin(target.getRole())
                && userRepository.countByRoleAndStatus(Roles.SUPER_ADMIN, UserStatus.ENABLED) <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "系统至少需要保留一个启用的超级管理员");
        }
    }

    private static boolean isSelf(CurrentUser actor, Long id) {
        return id != null && id.equals(actor.id());
    }

    private static String requireExplicitPassword(String newPassword) {
        // 密码不 trim：空格是合法密码字符，「  abc123  」和「abc123」是两个不同的密码
        if (newPassword == null || newPassword.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请提供新密码，或改用「随机生成」");
        }
        if (newPassword.length() < 6 || newPassword.length() > 64) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新密码长度需在 6~64 之间");
        }
        return newPassword;
    }

    private static String generatePassword() {
        StringBuilder password = new StringBuilder(GENERATED_PASSWORD_LENGTH);
        for (int i = 0; i < GENERATED_PASSWORD_LENGTH; i++) {
            password.append(PASSWORD_ALPHABET.charAt(RANDOM.nextInt(PASSWORD_ALPHABET.length())));
        }
        return password.toString();
    }
}
