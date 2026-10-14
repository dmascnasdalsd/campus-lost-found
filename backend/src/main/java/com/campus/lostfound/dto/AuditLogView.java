package com.campus.lostfound.dto;

import com.campus.lostfound.entity.ReportActionType;

import java.time.LocalDateTime;

/**
 * 审计日志视图：附带操作人昵称与目标启事标题，便于前端直接展示。
 */
public record AuditLogView(
        Long id,
        Long operatorId,
        String operatorNickname,
        ReportActionType action,
        Long reportId,
        String reportTitle,
        String reason,
        LocalDateTime createdAt
) {
}
