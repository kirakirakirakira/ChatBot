package com.chatbot.chatbot.config;

import com.chatbot.chatbot.auth.AuthProperties;
import com.chatbot.chatbot.auth.Roles;
import com.chatbot.chatbot.entity.User;
import com.chatbot.chatbot.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 首次启动兜底：sys_user 表为空时创建初始管理员（默认 admin / admin）。
 * 很多人不执行 sql/init.sql、直接靠 ddl-auto=update 启动，那样表建出来但没有账号，谁都登录不进去。
 * 条件只有 count()==0，因此仅对全新库生效，不会覆盖已有账号或重置改过的密码。
 */
@Component
public class AdminUserInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties authProperties;

    public AdminUserInitializer(UserRepository userRepository,
                                PasswordEncoder passwordEncoder,
                                AuthProperties authProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authProperties = authProperties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        User admin = new User();
        admin.setUsername(authProperties.defaultAdminUsername());
        admin.setPassword(passwordEncoder.encode(authProperties.defaultAdminPassword()));
        admin.setRole(Roles.ADMIN);
        userRepository.save(admin);
        // 故意不把密码打进日志：日志会被收集和转发，密码不该出现在里面
        log.warn("sys_user 表为空，已创建初始管理员账号「{}」（密码取自 auth.default-admin-password，默认 admin），"
                + "请登录后立即修改", admin.getUsername());
    }
}