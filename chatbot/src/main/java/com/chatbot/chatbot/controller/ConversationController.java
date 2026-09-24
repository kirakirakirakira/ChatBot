package com.chatbot.chatbot.controller;

import com.chatbot.chatbot.auth.CurrentUser;
import com.chatbot.chatbot.dto.ConversationVO;
import com.chatbot.chatbot.dto.MessagePageVO;
import com.chatbot.chatbot.dto.RenameConversationRequest;
import com.chatbot.chatbot.service.ConversationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 会话接口。每个方法都声明 CurrentUser 形参（解析见 CurrentUserArgumentResolver），
 * 归属校验在 service 层，控制器不判断角色也不判断归属。
 */
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationVO create(CurrentUser user) {
        return conversationService.create(user);
    }

    @GetMapping
    public List<ConversationVO> list(CurrentUser user) {
        return conversationService.list(user);
    }

    /**
     * 消息分页，从最新往前翻。
     *
     * @param before 游标：只返回 id 小于它的消息，取自上一页响应的 beforeId；不传就是最新一页
     * @param limit  单页条数，默认 50，上限 200（超出按上限截断，不报 400）
     */
    @GetMapping("/{id}/messages")
    public MessagePageVO messages(@PathVariable Long id,
                                  @RequestParam(name = "before", required = false) Long before,
                                  @RequestParam(name = "limit", required = false) Integer limit,
                                  CurrentUser user) {
        return conversationService.messages(id, user, before, limit);
    }

    /** 重命名会话。改名不算「活动」：不刷新 updated_at，列表排序不变。 */
    @PutMapping("/{id}/title")
    public ConversationVO rename(@PathVariable Long id,
                                 @Valid @RequestBody RenameConversationRequest request,
                                 CurrentUser user) {
        return conversationService.rename(id, user, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, CurrentUser user) {
        conversationService.delete(id, user);
    }
}
