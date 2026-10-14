package com.campus.lostfound.report;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 启事详情：包含图片列表与发布者摘要。
 * 浏览量自增与公开联系方式属于工单 04，这里只返回库中已有字段。
 */
public record ReportDetail(
        Long id,
        ReportType type,
        String title,
        String category,
        String location,
        String locationDetail,
        LocalDateTime eventTime,
        String description,
        ReportStatus status,
        boolean pinned,
        Custody custody,
        String handoverNo,
        int viewCount,
        PublisherSummary publisher,
        List<ReportImageInfo> images,
        String rejectReason,
        LocalDateTime reviewedAt,
        LocalDateTime createdAt
) {

    public static ReportDetail from(Report report, PublisherSummary publisher) {
        List<ReportImageInfo> images = report.getImages().stream()
                .map(ReportImageInfo::from)
                .toList();
        return new ReportDetail(
                report.getId(),
                report.getType(),
                report.getTitle(),
                report.getCategory(),
                report.getLocation(),
                report.getLocationDetail(),
                report.getEventTime(),
                report.getDescription(),
                report.getStatus(),
                report.isPinned(),
                report.getCustody(),
                report.getHandoverNo(),
                report.getViewCount(),
                publisher,
                images,
                report.getRejectReason(),
                report.getReviewedAt(),
                report.getCreatedAt()
        );
    }
}
