package com.campus.lostfound.support;

/**
 * 文本处理相关的无状态工具。
 */
public final class Texts {

    private Texts() {
    }

    /** 去空白后返回；null 或 trim 后为空均归一为 null，否则返回 trim 结果。 */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
