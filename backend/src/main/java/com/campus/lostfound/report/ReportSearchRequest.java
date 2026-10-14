package com.campus.lostfound.report;

import java.time.LocalDateTime;

/**
 * 列表检索条件：由 Controller 从查询参数绑定，Service 负责解析时间窗与排序。
 */
public record ReportSearchRequest(
        ReportType type,
        String category,
        String location,
        String timeRange,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String keyword,
        String sort,
        Integer page,
        Integer size,
        Boolean includeClosed
) {
}
