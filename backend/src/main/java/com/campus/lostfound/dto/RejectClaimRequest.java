package com.campus.lostfound.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 驳回认领申请请求体：拒绝原因必填。
 */
public record RejectClaimRequest(
        @NotBlank(message = "请填写驳回原因")
        @Size(max = 255, message = "驳回原因不超过 255 字") String rejectReason
) {
}
