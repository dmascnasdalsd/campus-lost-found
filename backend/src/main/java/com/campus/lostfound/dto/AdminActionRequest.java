package com.campus.lostfound.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理员治理动作请求体（下架 / 驳回）：原因必填。
 */
public record AdminActionRequest(
        @NotBlank(message = "请填写原因")
        @Size(max = 255, message = "原因不超过 255 字") String reason
) {
}
