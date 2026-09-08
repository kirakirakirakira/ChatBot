package com.chatbot.chatbot.controller;

import com.chatbot.chatbot.auth.CurrentUser;
import com.chatbot.chatbot.auth.RequireAdmin;
import com.chatbot.chatbot.dto.ChangePasswordRequest;
import com.chatbot.chatbot.dto.LoginResponse;
import com.chatbot.chatbot.dto.UserVO;
import com.chatbot.chatbot.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 改自己的密码，返回新的登录态（token + 用户信息），前端直接换上即可，不用重新登录。
     * 路径里的 me 就是「只能是自己」：不接受 userId 参数。
     */
    @PutMapping("/me/password")
    public LoginResponse changePassword(CurrentUser currentUser,
                                        @Valid @RequestBody ChangePasswordRequest request) {
        return userService.changePassword(currentUser, request);
    }

    /** 用户列表，仅管理员（role=1）；普通用户拿到 403。 */
    @GetMapping
    @RequireAdmin
    public List<UserVO> list() {
        return userService.list();
    }
}