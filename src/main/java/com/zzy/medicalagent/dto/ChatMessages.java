package com.zzy.medicalagent.dto;



import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document("chat_message")
public class ChatMessages {
    //唯一标识,映射到MongoDB文档的 _id字段
    @Id
    private ObjectId id;
    private String messageId;
    private String content; //存储当前聊天列表的json字符串
}
