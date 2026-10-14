package com.campus.lostfound.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 统计看板视图：某统计窗口（week / month）内的启事聚合结果。
 * returnRate 为百分比数值（0~100，保留 1 位小数），便于前端直接展示。
 */
public record AdminStatsView(
        String range,
        LocalDate from,
        LocalDate to,
        long totalReports,
        long returnedReports,
        double returnRate,
        List<DailyStatView> daily,
        List<NameCountView> byLocation,
        List<NameCountView> byCategory,
        List<NameCountView> byHour
) {
}
