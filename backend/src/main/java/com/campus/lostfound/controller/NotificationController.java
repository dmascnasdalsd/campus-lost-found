package com.campus.lostfound.controller;

import com.campus.lostfound.dto.ApiResponse;
import com.campus.lostfound.dto.NotificationListView;
import com.campus.lostfound.dto.NotificationView;
import com.campus.lostfound.service.NotificationService;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内通知的 HTTP 入口：只做取当前用户 id 并转发 Service。
 * 这些路由均落在 SecurityConfig 的 anyRequest().authenticated() 之下，天然需要登录。
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** 我的通知列表与未读数。 */
    @GetMapping
    public ApiResponse<NotificationListView> list(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(notificationService.list(userId));
    }

    /** 标记单条通知已读。 */
    @PostMapping("/{id}/read")
    public ApiResponse<NotificationView> markRead(@PathVariable Long id, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(notificationService.markRead(userId, id));
    }

    /** 一键全部标记已读，返回本次标记数量。 */
    @PostMapping("/read-all")
    public ApiResponse<Integer> markAllRead(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(notificationService.markAllRead(userId));
    }
}
