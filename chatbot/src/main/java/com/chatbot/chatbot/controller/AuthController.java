package com.chatbot.chatbot.controller;

import com.chatbot.chatbot.auth.CurrentUser;
import com.chatbot.chatbot.dto.LoginRequest;
import com.chatbot.chatbot.dto.LoginResponse;
import com.chatbot.chatbot.dto.UserVO;
import com.chatbot.chatbot.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /** 登录。全站唯一不需要 token 的接口（白名单见 WebConfig）。 */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return userService.login(request);
    }

    /**
     * 当前登录用户。前端启动时调它验证本地存的 token 是否还有效：有效进主界面，401 就清掉登录态回登录页。
     */
    @GetMapping("/me")
    public UserVO me(CurrentUser currentUser) {
        return userService.me(currentUser);
    }
}