package com.chatbot.chatbot.controller;

import com.chatbot.chatbot.auth.CurrentUser;
import com.chatbot.chatbot.dto.ChangePasswordRequest;
import com.chatbot.chatbot.dto.LoginResponse;
import com.chatbot.chatbot.dto.UpdateProfileRequest;
import com.chatbot.chatbot.dto.UpdateSystemPromptRequest;
import com.chatbot.chatbot.dto.UserProfileStatsVO;
import com.chatbot.chatbot.dto.UserVO;
import com.chatbot.chatbot.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/users 只管「自己」：路径里全部是 me/*，没有任何一处接受 userId 参数。
 * 「管别人」的接口全在 {@link AdminUserController}（/api/admin/users，类级 @RequireAdmin）。
 * 这个控制器**刻意不打 @RequireAdmin**：普通用户当然要能改自己的密码和资料。
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 改自己的密码，返回新的登录态（token + 用户信息），前端直接换上、不用重新登录。
     * 路径里的 me 就是「只能是自己」：不接受 userId 参数。
     */
    @PutMapping("/me/password")
    public LoginResponse changePassword(CurrentUser currentUser,
                                        @Valid @RequestBody ChangePasswordRequest request) {
        return userService.changePassword(currentUser, request);
    }

    /**
     * 改自己的系统提示词，返回更新后的用户信息，前端直接覆盖本地登录态里的 currentUser。
     * 不换发 token：改人设不作废登录态，和改密码的语义刻意不同。
     */
    @PutMapping("/me/system-prompt")
    public UserVO updateSystemPrompt(CurrentUser currentUser,
                                     @Valid @RequestBody UpdateSystemPromptRequest request) {
        return userService.updateSystemPrompt(currentUser, request);
    }

    /**
     * 改自己的资料（昵称 / 邮箱 / 手机号），返回整份用户信息。
     * 和改人设一样不换发 token：改资料不是安全事件。
     */
    @PutMapping("/me/profile")
    public UserVO updateProfile(CurrentUser currentUser,
                                @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(currentUser, request);
    }

    /**
     * 我的使用统计（会话数 / 消息数 / 图片数）。
     * 单独一个接口而不是并进 /me：消息数要在 LONGTEXT 大表上 count，
     * 不该拖慢登录和每次导航都会跑的 /me（理由见 UserProfileStatsVO）。
     */
    @GetMapping("/me/stats")
    public UserProfileStatsVO stats(CurrentUser currentUser) {
        return userService.stats(currentUser);
    }

    /**
     * 退出所有设备（作废自己的全部登录态，含当前这个）。204 无响应体。
     * <p>
     * 用 POST 而不是 DELETE：它删的不是某个资源，而是「让所有 token 失效」这个动作，
     * 和管理端 /{id}/revoke 的口径保持一致。调用方拿到 204 后必须自己清登录态。
     */
    @PostMapping("/me/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeOwnSessions(CurrentUser currentUser) {
        userService.revokeOwnSessions(currentUser);
    }
}
