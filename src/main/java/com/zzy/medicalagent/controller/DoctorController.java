package com.zzy.medicalagent.controller;

import com.zzy.medicalagent.assistant.DoctorAgent;
import com.zzy.medicalagent.dto.ChatFormDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "智能家庭医生")
@RestController
@RequestMapping("/doctor")
public class DoctorController {

    @Autowired
    private DoctorAgent doctorAgent;

    @Operation(summary = "对话")
    @PostMapping("/chat")
    public String chat(@RequestBody ChatFormDTO chatForm)  {
        return doctorAgent.chat(chatForm.getMemoryId(), chatForm.getMessage());
    }

}
