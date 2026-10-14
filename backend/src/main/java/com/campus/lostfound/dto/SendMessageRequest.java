package com.campus.lostfound.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 发送会话消息请求体：内容必填。
 */
public record SendMessageRequest(
        @NotBlank(message = "请输入消息内容") String body
) {
}
