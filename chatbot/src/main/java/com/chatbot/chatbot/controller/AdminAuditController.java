package com.chatbot.chatbot.controller;

import com.chatbot.chatbot.auth.RequireAdmin;
import com.chatbot.chatbot.dto.AdminAuditLogVO;
import com.chatbot.chatbot.dto.PageVO;
import com.chatbot.chatbot.service.AdminAuditService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端操作审计的读取入口。
 * <p>
 * 挂在 /api/admin/audit 而不是 /api/admin/users/audit：审计表是全管理端共用的，
 * 以后的管理模块往同一张表写动作，读取入口不该绑死在用户管理一个模块上。
 */
@RestController
@RequestMapping("/api/admin/audit")
@RequireAdmin
public class AdminAuditController {

    private final AdminAuditService adminAuditService;

    public AdminAuditController(AdminAuditService adminAuditService) {
        this.adminAuditService = adminAuditService;
    }

    /** 审计列表，id 倒序（新的在前）。page 从 0 起，size 默认 20、上限 100。 */
    @GetMapping
    public PageVO<AdminAuditLogVO> list(@RequestParam(name = "page", required = false) Integer page,
                                        @RequestParam(name = "size", required = false) Integer size) {
        return adminAuditService.page(page, size);
    }
}
