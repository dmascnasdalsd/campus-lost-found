package com.campus.lostfound.controller;

import com.campus.lostfound.dto.ApiResponse;
import com.campus.lostfound.dto.ChatSessionView;
import com.campus.lostfound.dto.MessageView;
import com.campus.lostfound.dto.SendMessageRequest;
import com.campus.lostfound.service.ChatService;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 站内会话的 HTTP 入口：只做参数校验、取当前用户 id、转发给 Service。
 * 这些路由均落在 SecurityConfig 的 anyRequest().authenticated() 之下，天然需要登录。
 */
@RestController
@RequestMapping("/api/chats")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /** 我参与的会话列表。 */
    @GetMapping
    public ApiResponse<List<ChatSessionView>> list(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(chatService.list(userId));
    }

    /** 某会话的消息流。 */
    @GetMapping("/{id}/messages")
    public ApiResponse<List<MessageView>> messages(@PathVariable Long id, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(chatService.messages(userId, id));
    }

    /** 发送消息。 */
    @PostMapping("/{id}/messages")
    public ApiResponse<MessageView> send(@PathVariable Long id,
                                         @Valid @RequestBody SendMessageRequest request,
                                         Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(chatService.send(userId, id, request.body()));
    }
}
