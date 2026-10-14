package com.campus.lostfound.service;

import java.util.List;

/**
 * 分类与地点的受控词表：前端下拉与后端校验共用同一套取值。
 * 注意「体育馆/操场」这类取值原样传输，不做任何替换或拆分。
 */
public final class ReportVocabulary {

    public static final List<String> CATEGORIES = List.of(
            "校园卡", "身份证", "钥匙", "耳机", "手机", "笔记本电脑", "充电宝",
            "水杯", "书籍", "雨伞", "衣物", "运动器材", "其他");

    public static final List<String> LOCATIONS = List.of(
            "教学楼", "图书馆", "食堂", "宿舍楼", "体育馆/操场", "校医院", "校门", "其他");

    public static boolean isValidCategory(String category) {
        return category != null && CATEGORIES.contains(category);
    }

    public static boolean isValidLocation(String location) {
        return location != null && LOCATIONS.contains(location);
    }

    private ReportVocabulary() {
    }
}
