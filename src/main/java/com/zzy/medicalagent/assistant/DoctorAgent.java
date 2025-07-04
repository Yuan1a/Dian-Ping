package com.zzy.medicalagent.assistant;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "openAiChatModel", //使用模型
        chatMemoryProvider = "chatMemoryProviderDoctor", //记忆存储
        tools = "appointmentTools",
        contentRetriever = "contentRetrieverDoctor")//配置存储向量

public interface DoctorAgent {
    @SystemMessage(fromResource = "doctor-prompt-template.txt")
    String chat(@MemoryId Long memoryId, @UserMessage String userMessage);
}
