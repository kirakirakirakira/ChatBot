package com.chatbot.chatbot.controller;

import com.chatbot.chatbot.dto.LlmOptionsVO;
import com.chatbot.chatbot.llm.LlmProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模型选项。默认受保护（不在 WebConfig.PUBLIC_PATHS 里）：模型清单不算敏感信息，
 * 但没必要在未登录时暴露部署用了哪些模型。
 */
@RestController
@RequestMapping("/api/llm")
public class LlmController {

    private final LlmProperties llmProperties;

    public LlmController(LlmProperties llmProperties) {
        this.llmProperties = llmProperties;
    }

    @GetMapping("/options")
    public LlmOptionsVO options() {
        return new LlmOptionsVO(llmProperties.availableModels(), llmProperties.model());
    }
}
