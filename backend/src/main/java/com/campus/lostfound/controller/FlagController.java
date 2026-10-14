package com.campus.lostfound.controller;

import com.campus.lostfound.dto.ApiResponse;
import com.campus.lostfound.dto.CreateFlagRequest;
import com.campus.lostfound.dto.FlagView;
import com.campus.lostfound.service.FlagService;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 举报入口：只做参数校验、取当前用户 id、转发 service。
 * 两条路由不在 SecurityConfig 放行名单内，自动要求登录。
 */
@RestController
@RequestMapping("/api")
public class FlagController {

    private final FlagService flagService;

    public FlagController(FlagService flagService) {
        this.flagService = flagService;
    }

    /** 举报启事。 */
    @PostMapping("/reports/{id}/flags")
    public ApiResponse<FlagView> flagReport(@PathVariable Long id,
                                            @Valid @RequestBody CreateFlagRequest request,
                                            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(flagService.createReportFlag(userId, id, request));
    }

    /** 举报会话消息。 */
    @PostMapping("/messages/{id}/flags")
    public ApiResponse<FlagView> flagMessage(@PathVariable Long id,
                                              @Valid @RequestBody CreateFlagRequest request,
                                              Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(flagService.createMessageFlag(userId, id, request));
    }
}
