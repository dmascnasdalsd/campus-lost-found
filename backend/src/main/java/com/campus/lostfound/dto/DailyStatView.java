package com.campus.lostfound.dto;

import java.time.LocalDate;

/**
 * 每日统计：某一天的新增启事数与归还成功数。
 */
public record DailyStatView(
        LocalDate date,
        long created,
        long returned
) {
}
