package com.campus.lostfound.report;

import java.time.LocalDateTime;

/**
 * 修改启事请求体（PATCH 语义）。
 * 各字段均可缺省（null 表示不修改）；分类/地点是否取自词表、以及招领启事的
 * 保管方式与移交单号这类条件校验，放在 Service 层完成，以便返回含字段名的错误消息。
 */
public record UpdateReportRequest(
        String title,
        String category,
        String location,
        String locationDetail,
        LocalDateTime eventTime,
        String description,
        Custody custody,
        String handoverNo
) {
}
