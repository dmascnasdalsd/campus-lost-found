package com.campus.lostfound.dto;

import com.campus.lostfound.entity.Message;

import java.time.LocalDateTime;

/**
 * 消息视图：发送者昵称由 Service 装配传入。
 */
public record MessageView(
        Long id,
        Long sessionId,
        Long senderId,
        String senderNickname,
        String body,
        LocalDateTime createdAt
) {

    public static MessageView from(Message message, String senderNickname) {
        return new MessageView(
                message.getId(),
                message.getSessionId(),
                message.getSenderId(),
                senderNickname,
                message.getBody(),
                message.getCreatedAt()
        );
    }
}
