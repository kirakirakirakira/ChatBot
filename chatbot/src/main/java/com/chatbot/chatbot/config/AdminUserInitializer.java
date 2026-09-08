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
 * 首次启动兜底：sys_user 表一个用户都没有时，创建初始管理员（默认 admin / admin）。
 * <p>
 * sql/init.sql 里已经有一条等价的 INSERT IGNORE，两者留一个就够。之所以还要这段代码：
 * 项目跑的是 spring.jpa.hibernate.ddl-auto=update，很多人不执行 init.sql 直接启动，
 * 那样表会被 Hibernate 建出来、但里面没有 admin，结果谁都登录不进去。
 * <p>
 * 判断条件是 count() == 0，所以它只在「全新库」上生效：
 * 既不会覆盖已有用户，也不会把谁改过的密码重置回 admin。
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
        // 故意不把密码打进日志：日志会被收集、会被转发，密码不该出现在里面
        log.warn("sys_user 表为空，已创建初始管理员账号「{}」（密码取自 auth.default-admin-password，默认 admin），"
                + "请登录后立即修改", admin.getUsername());
    }
}