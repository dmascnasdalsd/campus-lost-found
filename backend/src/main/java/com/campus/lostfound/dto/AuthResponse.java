package com.campus.lostfound.dto;

/**
 * 登录成功响应：颁发的 access token 与当前用户信息。
 */
public record AuthResponse(String token, UserView user) {
}
