package com.campus.lostfound.dto;

import com.campus.lostfound.entity.ReportStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 变更启事状态请求体：{@code status} 与（已移交时必填的）{@code handoverNo}。
 * v1 只允许把启事标记为「已归还 RETURNED」或「已移交 HANDED_OVER」，
 * 更复杂的流转与合法性校验放在 Service 层完成。
 */
public record UpdateStatusRequest(
        @NotNull(message = "请选择要变更的状态") ReportStatus status,
        @Size(max = 64, message = "移交单号不超过 64 字") String handoverNo
) {
}
