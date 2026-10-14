package com.campus.lostfound.dto;

import com.campus.lostfound.entity.FlagReason;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 发起举报请求体：理由取自受控枚举，可选补充说明。
 */
public record CreateFlagRequest(
        @NotNull(message = "请选择举报理由")
        FlagReason reason,
        @Size(max = 255, message = "补充说明不超过 255 字")
        String detail
) {
}
