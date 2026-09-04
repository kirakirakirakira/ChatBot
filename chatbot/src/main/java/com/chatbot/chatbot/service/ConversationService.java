package com.chatbot.chatbot.service;

import com.chatbot.chatbot.dto.ConversationVO;
import com.chatbot.chatbot.dto.MessageVO;
import com.chatbot.chatbot.entity.Conversation;
import com.chatbot.chatbot.repository.ConversationRepository;
import com.chatbot.chatbot.repository.MessageRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    public ConversationService(ConversationRepository conversationRepository,
                               MessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    public ConversationVO create() {
        Conversation conversation = new Conversation();
        conversation.setTitle("新的对话");
        return ConversationVO.from(conversationRepository.save(conversation));
    }

    public List<ConversationVO> list() {
        return conversationRepository.findAllByOrderByUpdatedAtDesc().stream()
                .map(ConversationVO::from)
                .toList();
    }

    public List<MessageVO> messages(Long id) {
        require(id);
        return messageRepository.findByConversationIdOrderByIdAsc(id).stream()
                .map(MessageVO::from)
                .toList();
    }

    @Transactional
    public void delete(Long id) {
        Conversation conversation = require(id);
        messageRepository.deleteByConversationId(conversation.getId());
        conversationRepository.delete(conversation);
    }

    /** 供 ChatService 复用：找不到会话直接 404。 */
    public Conversation require(Long id) {
        return conversationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在: " + id));
    }
}
