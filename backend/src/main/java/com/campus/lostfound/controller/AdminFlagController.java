package com.campus.lostfound.controller;

import com.campus.lostfound.dto.ApiResponse;
import com.campus.lostfound.dto.FlagView;
import com.campus.lostfound.dto.PageResult;
import com.campus.lostfound.dto.ResolveFlagRequest;
import com.campus.lostfound.service.AdminService;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员举报处置入口。角色限制由 SecurityConfig 的 /api/admin/** 规则统一负责，
 * 这里只取 principal（用户 id）并转发 service。
 */
@RestController
@RequestMapping("/api/admin/flags")
public class AdminFlagController {

    private final AdminService adminService;

    public AdminFlagController(AdminService adminService) {
        this.adminService = adminService;
    }

    /** 举报列表：status 为空→全部；PENDING 等合法状态→按状态过滤。 */
    @GetMapping
    public ApiResponse<PageResult<FlagView>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(adminService.listFlags(status, page, size));
    }

    /** 处置举报：驳回 / 下架内容 / 禁用用户。 */
    @PostMapping("/{id}/resolve")
    public ApiResponse<FlagView> resolve(@PathVariable Long id,
                                         @Valid @RequestBody ResolveFlagRequest request,
                                         Authentication authentication) {
        Long adminId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(adminService.resolveFlag(adminId, id, request.action()));
    }
}
