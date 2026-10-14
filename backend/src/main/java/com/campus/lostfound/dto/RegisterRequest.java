package com.campus.lostfound.dto;

import com.campus.lostfound.exception.GlobalExceptionHandler;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 注册请求。字段级校验失败由 GlobalExceptionHandler 汇总为中文提示。
 */
public record RegisterRequest(
        @NotBlank(message = "学号不能为空")
        @Size(max = 32, message = "学号长度不能超过 32")
        String studentNo,

        @NotBlank(message = "昵称不能为空")
        @Size(max = 64, message = "昵称长度不能超过 64")
        String nickname,

        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 64, message = "密码长度至少 8 位")
        String password,

        @Size(max = 128, message = "院系长度不能超过 128")
        String department,

        @Email(message = "邮箱格式不正确")
        @Size(max = 128, message = "邮箱长度不能超过 128")
        String email
) {
}
