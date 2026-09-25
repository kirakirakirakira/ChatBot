package com.chatbot.chatbot.controller;

import com.chatbot.chatbot.auth.CurrentUser;
import com.chatbot.chatbot.auth.RequireAdmin;
import com.chatbot.chatbot.dto.AdminUserVO;
import com.chatbot.chatbot.dto.CreateUserRequest;
import com.chatbot.chatbot.dto.PageVO;
import com.chatbot.chatbot.dto.ResetPasswordRequest;
import com.chatbot.chatbot.dto.ResetPasswordResult;
import com.chatbot.chatbot.dto.UpdateRoleRequest;
import com.chatbot.chatbot.dto.UpdateStatusRequest;
import com.chatbot.chatbot.dto.UserAdminOptionsVO;
import com.chatbot.chatbot.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理接口，整个模块只有管理员能用。
 * <p>
 * {@code @RequireAdmin} 打在类上而不是逐个方法打：这个控制器下不存在「普通用户也该能调」的方法，
 * 漏打一个就是一个越权洞，类级注解不给这种遗漏留机会（AuthInterceptor 两种位置都认）。
 * <p>
 * 挂在 /api/admin/users 而不是复用 /api/users：/api/users 是「管自己」（me/*），
 * /api/admin/** 是「管别人」。前缀分开之后，以后加平级的管理模块（/api/admin/xxx）
 * 和给整个 /api/admin/** 追加限制（审计日志、IP 白名单）都只有一个落点。
 * <p>
 * 写操作方法都声明 CurrentUser 形参：不是为了鉴权，是 service 要做「不能对自己下手」的保护，
 * 而目标用户 id 只能来自 token，不能来自请求体。
 */
@RestController
@RequestMapping("/api/admin/users")
@RequireAdmin
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    /**
     * 用户列表，分页 + 三个可选筛选器。
     *
     * @param keyword 对用户名做忽略大小写模糊匹配；空串或不传 = 不筛
     * @param role    0=普通用户，1=管理员；不传 = 不限
     * @param status  0=启用，1=禁用；不传 = 不限
     * @param page    页码，从 0 起，默认 0；负数 400
     * @param size    每页条数，默认 20，上限 100；越界 400（不像消息列表那样静默收敛）
     */
    @GetMapping
    public PageVO<AdminUserVO> list(@RequestParam(name = "keyword", required = false) String keyword,
                                    @RequestParam(name = "role", required = false) Integer role,
                                    @RequestParam(name = "status", required = false) Integer status,
                                    @RequestParam(name = "page", required = false) Integer page,
                                    @RequestParam(name = "size", required = false) Integer size,
                                    CurrentUser currentUser) {
        return adminUserService.search(currentUser, keyword, role, status, page, size);
    }

    /** 角色 / 状态的可选值，给筛选器和下拉框用；中文标签由后端下发，前端不维护映射。 */
    @GetMapping("/options")
    public UserAdminOptionsVO options(CurrentUser currentUser) {
        return adminUserService.options(currentUser);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserVO create(@Valid @RequestBody CreateUserRequest request, CurrentUser currentUser) {
        return adminUserService.create(currentUser, request);
    }

    @PutMapping("/{id}/role")
    public AdminUserVO updateRole(@PathVariable Long id,
                                  @Valid @RequestBody UpdateRoleRequest request,
                                  CurrentUser currentUser) {
        return adminUserService.updateRole(currentUser, id, request);
    }

    @PutMapping("/{id}/status")
    public AdminUserVO updateStatus(@PathVariable Long id,
                                    @Valid @RequestBody UpdateStatusRequest request,
                                    CurrentUser currentUser) {
        return adminUserService.updateStatus(currentUser, id, request);
    }

    /** 重置密码。响应里的 generatedPassword 只出现这一次，前端必须当场展示给管理员抄走。 */
    @PostMapping("/{id}/password")
    public ResetPasswordResult resetPassword(@PathVariable Long id,
                                             @RequestBody ResetPasswordRequest request,
                                             CurrentUser currentUser) {
        return adminUserService.resetPassword(currentUser, id, request);
    }

    /** 强制下线：作废目标用户当前所有登录态，不改密码。没有响应体，204。 */
    @PostMapping("/{id}/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable Long id, CurrentUser currentUser) {
        adminUserService.revoke(currentUser, id);
    }

    /** 删号，连带删他的会话 / 消息 / 附件（单事务，顺序见 AdminUserService.delete）。 */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, CurrentUser currentUser) {
        adminUserService.delete(currentUser, id);
    }
}
