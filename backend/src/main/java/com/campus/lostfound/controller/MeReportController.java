package com.campus.lostfound.controller;

import com.campus.lostfound.config.SecurityConfig;
import com.campus.lostfound.dto.ApiResponse;
import com.campus.lostfound.dto.PageResult;
import com.campus.lostfound.dto.ReportSummary;
import com.campus.lostfound.service.ReportService;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 「我的发布」：返回当前登录用户的全部启事（含终态）。
 * 刻意挂载在 {@code /api/me/reports} 而非 {@code /api/reports}，避免命中
 * SecurityConfig 中「GET /api/reports/** 放行」的通配规则。
 */
@RestController
@RequestMapping("/api/me/reports")
public class MeReportController {

    private final ReportService reportService;

    public MeReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    public ApiResponse<PageResult<ReportSummary>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(reportService.listMine(userId, page, size));
    }
}
