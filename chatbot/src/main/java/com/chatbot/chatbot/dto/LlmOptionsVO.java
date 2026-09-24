package com.chatbot.chatbot.dto;

import java.util.List;

/**
 * 前端模型选择器的数据源。模型列表来自配置 llm.available-models（环境变量 LLM_AVAILABLE_MODELS），
 * 加模型不用改代码、不用发版前端。
 *
 * @param models       可选模型 id 列表，顺序即界面顺序
 * @param defaultModel 服务端默认模型（llm.model）；请求没带 model 时用它
 * @param visionModels models 的子集：能吃图片输入的模型。前端用它决定要不要显示「上传图片」按钮——
 *                     选中的模型不在里面时按钮隐藏，比让用户传完图再收一个 400 友好得多
 */
public record LlmOptionsVO(List<String> models, String defaultModel, List<String> visionModels) {
}
