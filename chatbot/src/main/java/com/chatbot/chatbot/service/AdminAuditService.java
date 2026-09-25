package com.chatbot.chatbot.service;

import com.chatbot.chatbot.auth.CurrentUser;
import com.chatbot.chatbot.dto.AdminAuditLogVO;
import com.chatbot.chatbot.dto.PageVO;
import com.chatbot.chatbot.entity.AdminAuditLog;
import com.chatbot.chatbot.repository.AdminAuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 审计的写与读。
 * <p>
 * 独立成服务而不是塞进 AdminUserService：审计表是**全管理端共用**的（/api/admin/** 下以后的模块
 * 也往这里写），调用方不该关心表长什么样；读接口（/api/admin/audit）也挂在这里。
 */
@Service
public class AdminAuditService {

    /** 与 AdminUserService 同一个上限口径：管理界面的分页参数不该有第二种规则。 */
    private static final int MAX_PAGE_SIZE = 100;

    private final AdminAuditLogRepository auditLogRepository;

    public AdminAuditService(AdminAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * 记一条审计。
     * <p>
     * **调用方必须处在自己的 @Transactional 里**：审计与业务同事务，业务回滚则审计一起回滚。
     * 刻意不提供「事后补记」的入口——失败的操作不留痕是设计（见类注释与 PROJECT_OVERVIEW 九）。
     */
    public void record(CurrentUser actor, String action, Long targetId, String targetName, String detail) {
        AdminAuditLog log = new AdminAuditLog();
        log.setActorId(actor.id());
        log.setActorName(actor.username());
        log.setAction(action);
        log.setTargetId(targetId);
        log.setTargetName(targetName);
        log.setDetail(detail);
        auditLogRepository.save(log);
    }

    /** 审计列表，新的在前（id 倒序）。管理员看自己做过什么、以及别人做过什么。 */
    @Transactional(readOnly = true)
    public PageVO<AdminAuditLogVO> page(Integer page, Integer size) {
        int number = (page == null) ? 0 : page;
        int limit = (size == null) ? 20 : size;
        if (number < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页参数不合法: page 不能小于 0");
        }
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "分页参数不合法: size 需在 1~" + MAX_PAGE_SIZE + " 之间");
        }
        Pageable pageable = PageRequest.of(number, limit, Sort.by(Sort.Direction.DESC, "id"));
        Page<AdminAuditLog> result = auditLogRepository.findAll(pageable);
        return PageVO.of(result, AdminAuditLogVO::from);
    }
}
