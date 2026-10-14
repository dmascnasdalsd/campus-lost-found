package com.campus.lostfound.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 发起认领申请请求体：独有特征说明必填，期望交还时间与地点可选。
 */
public record CreateClaimRequest(
        @NotBlank(message = "请填写独有特征说明")
        @Size(max = 2000, message = "独有特征说明不超过 2000 字") String featureDesc,
        LocalDateTime expectTime,
        @Size(max = 255, message = "期望交还地点不超过 255 字") String expectPlace
) {
}
