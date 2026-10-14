package com.campus.lostfound.dto;

/**
 * 名称计数视图：用于地点 / 分类 / 高发时段的分布统计。
 */
public record NameCountView(
        String name,
        long count
) {
}
