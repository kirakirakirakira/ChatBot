package com.chatbot.chatbot.service;

import com.chatbot.chatbot.entity.AgentToolCall;
import com.chatbot.chatbot.repository.AgentToolCallRepository;
import com.chatbot.chatbot.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/** 工具调用审计的短事务写入；失败只记日志，不能影响正在进行的聊天。 */
@Service
public class AgentToolCallStore {

    private static final Logger log = LoggerFactory.getLogger(AgentToolCallStore.class);

    private final AgentToolCallRepository repository;
    private final ObjectMapper objectMapper;

    public AgentToolCallStore(AgentToolCallRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void record(Long conversationId, Long userId, String callId, String toolName, String toolLabel,
                       String argumentsJson, ToolResult result, long durationMs) {
        AgentToolCall row = new AgentToolCall();
        row.setConversationId(conversationId);
        row.setUserId(userId);
        row.setCallId(callId);
        row.setToolName(toolName);
        row.setToolLabel(toolLabel);
        row.setArgumentsJson(argumentsJson);
        row.setResultSummary(result == null ? null : result.summary());
        row.setStatus(result == null ? "ERROR" : result.status());
        row.setError(result != null && !result.ok() ? result.summary() : null);
        row.setDurationMs((int) Math.min(Integer.MAX_VALUE, Math.max(0, durationMs)));
        if (result != null) {
            try {
                row.setResultJson(objectMapper.writeValueAsString(result.data()));
            } catch (Exception e) {
                log.debug("工具结果序列化失败 callId={}: {}", callId, e.getMessage());
            }
        }
        repository.save(row);
    }

    /**
     * 助手消息入库后回填 message_id。独立短事务：调用方（ChatService）不包事务，
     * 失败只记日志，不能让「时间线回放」反过来影响已经完成的回答。
     */
    @Transactional
    public int attachToMessage(Long conversationId, Long userId, List<String> callIds, Long messageId) {
        if (callIds == null || callIds.isEmpty() || messageId == null) {
            return 0;
        }
        return repository.attachToMessage(conversationId, userId, callIds, messageId);
    }
}
