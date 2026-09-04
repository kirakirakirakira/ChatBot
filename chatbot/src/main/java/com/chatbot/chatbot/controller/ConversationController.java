package com.chatbot.chatbot.controller;

import com.chatbot.chatbot.dto.ConversationVO;
import com.chatbot.chatbot.dto.MessageVO;
import com.chatbot.chatbot.service.ConversationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationVO create() {
        return conversationService.create();
    }

    @GetMapping
    public List<ConversationVO> list() {
        return conversationService.list();
    }

    @GetMapping("/{id}/messages")
    public List<MessageVO> messages(@PathVariable Long id) {
        return conversationService.messages(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        conversationService.delete(id);
    }
}
