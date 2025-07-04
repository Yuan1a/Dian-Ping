package com.zzy.medicalagent.controller;

import com.zzy.medicalagent.assistant.Assistant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {
    @Autowired
    private Assistant assistant;

   @RequestMapping("/chat")
    public String chat(String message){
        String result = assistant.chat(message);
        return result;
    }
    /*    @Autowired
 */

}
