package com.campus.lostfound.controller;

import com.campus.lostfound.dto.AdminActionRequest;
import com.campus.lostfound.dto.ApiResponse;
import com.campus.lostfound.dto.AuditLogView;
import com.campus.lostfound.dto.PageResult;
import com.campus.lostfound.dto.ReportDetail;
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
 * 管理员内容治理入口。角色限制由 SecurityConfig 的 /api/admin/** 规则统一负责，
 * 这里只取 principal（用户 id）并转发 service。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    /** 复核队列：status 为空→全部启事；PENDING_REVIEW 等合法状态→按状态过滤。 */
    @GetMapping("/reports")
    public ApiResponse<PageResult<ReportDetail>> listReports(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(adminService.listReports(status, page, size));
    }

    @PostMapping("/reports/{id}/approve")
    public ApiResponse<ReportDetail> approve(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.ok(adminService.approve(adminId(authentication), id));
    }

    @PostMapping("/reports/{id}/reject")
    public ApiResponse<ReportDetail> reject(@PathVariable Long id,
                                            @Valid @RequestBody AdminActionRequest request,
                                            Authentication authentication) {
        return ApiResponse.ok(adminService.reject(adminId(authentication), id, request.reason()));
    }

    @PostMapping("/reports/{id}/take-down")
    public ApiResponse<ReportDetail> takeDown(@PathVariable Long id,
                                              @Valid @RequestBody AdminActionRequest request,
                                              Authentication authentication) {
        return ApiResponse.ok(adminService.takeDown(adminId(authentication), id, request.reason()));
    }

    @PostMapping("/reports/{id}/pin")
    public ApiResponse<ReportDetail> pin(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.ok(adminService.pin(adminId(authentication), id));
    }

    @PostMapping("/reports/{id}/unpin")
    public ApiResponse<ReportDetail> unpin(@PathVariable Long id, Authentication authentication) {
        return ApiResponse.ok(adminService.unpin(adminId(authentication), id));
    }

    @GetMapping("/audit-logs")
    public ApiResponse<PageResult<AuditLogView>> auditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(adminService.auditLogs(page, size));
    }

    private static Long adminId(Authentication authentication) {
        return (Long) authentication.getPrincipal();
    }
}
