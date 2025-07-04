package com.zzy.medicalagent.assistant;

import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;

@AiService(wiringMode = AiServiceWiringMode.EXPLICIT,chatModel = "openAiChatModel",chatMemoryProvider = "chatMemoryProvider")
public interface Assistant {
    String chat(String userMessage);
}
