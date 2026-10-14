package com.campus.lostfound.dto;

import com.campus.lostfound.entity.Custody;
import com.campus.lostfound.entity.ReportType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 发布启事请求体。
 * 分类/地点是否取自词表、以及招领启事的保管方式与移交单号这类条件校验，
 * 放在 Service 层完成，以便返回含字段名的错误消息。
 */
public record CreateReportRequest(
        @NotNull(message = "请选择启事类型（失物/招领）") ReportType type,
        @NotBlank(message = "请填写物品名称") @Size(max = 128, message = "物品名称不超过 128 字") String title,
        @NotBlank(message = "请选择分类") @Size(max = 32, message = "分类取值不合法") String category,
        @NotBlank(message = "请选择地点") @Size(max = 32, message = "地点取值不合法") String location,
        @Size(max = 255, message = "地点补充描述不超过 255 字") String locationDetail,
        @NotNull(message = "请选择事件时间") LocalDateTime eventTime,
        @Size(max = 2000, message = "描述不超过 2000 字") String description,
        Custody custody,
        @Size(max = 64, message = "移交单号不超过 64 字") String handoverNo
) {
}
