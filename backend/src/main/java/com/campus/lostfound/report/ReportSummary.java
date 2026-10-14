package com.campus.lostfound.report;

import java.time.LocalDateTime;

/**
 * 列表卡片所需的启事摘要。
 */
public record ReportSummary(
        Long id,
        ReportType type,
        String title,
        String category,
        String location,
        String locationDetail,
        LocalDateTime eventTime,
        ReportStatus status,
        boolean pinned,
        Custody custody,
        String thumbnailPath,
        int viewCount,
        LocalDateTime createdAt
) {

    public static ReportSummary from(Report report) {
        String thumbnail = report.getImages().isEmpty() ? null : report.getImages().get(0).getPath();
        return new ReportSummary(
                report.getId(),
                report.getType(),
                report.getTitle(),
                report.getCategory(),
                report.getLocation(),
                report.getLocationDetail(),
                report.getEventTime(),
                report.getStatus(),
                report.isPinned(),
                report.getCustody(),
                thumbnail,
                report.getViewCount(),
                report.getCreatedAt()
        );
    }
}
