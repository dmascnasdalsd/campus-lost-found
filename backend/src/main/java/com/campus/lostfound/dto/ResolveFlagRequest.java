package com.campus.lostfound.dto;

import com.campus.lostfound.entity.FlagHandleResult;

import jakarta.validation.constraints.NotNull;

/**
 * 管理员处置举报请求体：动作必填。
 */
public record ResolveFlagRequest(
        @NotNull(message = "请选择处置动作")
        FlagHandleResult action
) {
}
