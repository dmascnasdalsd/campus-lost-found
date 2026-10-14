package com.campus.lostfound.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求：学号 + 密码。
 */
public record LoginRequest(
        @NotBlank(message = "学号不能为空")
        String studentNo,

        @NotBlank(message = "密码不能为空")
        String password
) {
}
