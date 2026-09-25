package com.chatbot.chatbot.config;

import com.chatbot.chatbot.auth.AuthProperties;
import com.chatbot.chatbot.auth.Roles;
import com.chatbot.chatbot.auth.UserStatus;
import com.chatbot.chatbot.entity.User;
import com.chatbot.chatbot.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 启动时补齐种子账号。三件事，都是幂等的：
 * <ol>
 *   <li><b>兜底建超管</b>：sys_user 表为空时创建初始超级管理员（默认 admin / admin）。
 *       很多人不执行 sql/init.sql、直接靠 ddl-auto=update 启动，那样表建出来但没有账号，谁都登录不进去。</li>
 *   <li><b>超管自愈</b>：老库里 admin 可能还是「管理员」（层级模型是 2026-09-25 才引入的，
 *       那之前种子账号 role=1）。若系统里**一个启用的超级管理员都没有**，就把种子账号提回超管。
 *       层级规则下「创建 / 提升管理员」只有超管能做，没有超管的系统是一个再也管不动的死局，
 *       所以这里宁可自作主张提回来，也不留一个只能改库才能救的状态。
 *       反之，只要还有别的启用的超管，就**尊重**管理员对种子账号的降级，不会每次重启都改回去。</li>
 *   <li><b>补测试账号</b>：每个角色各一个，密码统一取 auth.test-user-password。
 *       同样只在「用户名还不存在」时创建：不覆盖已有账号、不重置改过的密码、不改被调整过的角色。
 *       关掉开关见 auth.seed-test-users。</li>
 * </ol>
 * 日志里**故意不打印任何密码**（含测试账号的统一密码）：日志会被收集和转发。
 */
@Component
public class SeedUserInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedUserInitializer.class);

    /**
     * 测试账号清单：每个角色一个，方便直接验证层级权限（超管能管管理员、管理员管不了管理员、
     * 访客谁都不该能管）。用户名固定，界面 / 文档 / 手测命令里可以直接引用。
     */
    private static final List<SeedAccount> TEST_ACCOUNTS = List.of(
            new SeedAccount("test_super", Roles.SUPER_ADMIN),
            new SeedAccount("test_admin", Roles.ADMIN),
            new SeedAccount("test_user", Roles.USER),
            new SeedAccount("test_guest", Roles.GUEST));

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties authProperties;

    public SeedUserInitializer(UserRepository userRepository,
                               PasswordEncoder passwordEncoder,
                               AuthProperties authProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authProperties = authProperties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ensureSuperAdmin();
        seedTestAccounts();
    }

    private void ensureSuperAdmin() {
        String username = authProperties.defaultAdminUsername();
        if (userRepository.count() == 0) {
            User admin = new User();
            admin.setUsername(username);
            admin.setPassword(passwordEncoder.encode(authProperties.defaultAdminPassword()));
            // 种子账号必须是**超级管理员**：层级规则下管理员管不到管理员，
            // 若种子只是管理员，系统将永远无法创建 / 提升出超级管理员
            admin.setRole(Roles.SUPER_ADMIN);
            admin.setStatus(UserStatus.ENABLED);
            admin.setMustChangePassword(Boolean.FALSE);
            userRepository.save(admin);
            log.warn("sys_user 表为空，已创建初始**超级管理员**账号「{}」（密码取自 auth.default-admin-password，默认 admin），"
                    + "请登录后立即修改", username);
            return;
        }
        long enabledSuperAdmins = userRepository.countByRoleAndStatus(Roles.SUPER_ADMIN, UserStatus.ENABLED);
        if (enabledSuperAdmins > 0) {
            // 系统里还有能管人的超管：种子账号现在是什么角色，是管理者自己的决定，启动流程不该改回去
            return;
        }
        User seed = userRepository.findByUsername(username).orElse(null);
        if (seed == null) {
            log.warn("系统里没有启用的超级管理员，也找不到种子账号「{}」：请用 SQL 手工把某个账号的 role 改成 {}（超级管理员），"
                            + "否则没人能再创建 / 提升管理员", username, Roles.SUPER_ADMIN);
            return;
        }
        int oldRole = seed.getRole() == null ? -1 : seed.getRole();
        seed.setRole(Roles.SUPER_ADMIN);
        seed.setStatus(UserStatus.ENABLED);
        userRepository.save(seed);
        log.warn("系统里没有启用的超级管理员，已把种子账号「{}」提升为超级管理员（原角色 {}）："
                + "层级规则下只有超级管理员能创建 / 提升管理员，不提回来系统就管不动了", username, oldRole);
    }

    private void seedTestAccounts() {
        if (!authProperties.seedTestUsers()) {
            return;
        }
        String rawPassword = authProperties.testUserPassword();
        if (rawPassword == null || rawPassword.length() < 6 || rawPassword.length() > 64) {
            // 不静默跳过：配置写错了却一声不响，等到要演示时才发现没有测试账号，比启动时报错难查得多
            throw new IllegalStateException("auth.test-user-password 长度需在 6~64 之间，当前配置无法用于创建测试账号");
        }
        // 同一个密码只算一次 BCrypt：BCrypt 故意慢（cost=10 约几十毫秒），四个账号没必要算四遍
        String hash = passwordEncoder.encode(rawPassword);
        List<String> created = new ArrayList<>();
        for (SeedAccount account : TEST_ACCOUNTS) {
            if (userRepository.findByUsername(account.username()).isPresent()) {
                continue;
            }
            User user = new User();
            user.setUsername(account.username());
            user.setPassword(hash);
            user.setRole(account.role());
            user.setStatus(UserStatus.ENABLED);
            // 不置「强制改密」：测试账号存在的意义就是随时能登进去验证权限，
            // 每次都被强制改密会让人干脆把它删掉
            user.setMustChangePassword(Boolean.FALSE);
            userRepository.save(user);
            created.add(account.username() + "(" + Roles.label(account.role()) + ")");
        }
        if (!created.isEmpty()) {
            log.warn("已补齐测试账号（密码统一取自 auth.test-user-password）：{}。生产环境请用 AUTH_SEED_TEST_USERS=false 关掉",
                    String.join("、", created));
        }
    }

    /** 一个待创建的测试账号：用户名 + 角色。密码统一，不放进这个 record（免得被误打进日志）。 */
    private record SeedAccount(String username, int role) {
    }
}
