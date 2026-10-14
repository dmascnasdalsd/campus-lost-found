package com.campus.lostfound.support;

import org.springframework.security.core.Authentication;

/**
 * 认证信息相关的无状态工具：供 controller 判断当前用户角色。
 */
public final class AuthSupport {

    private AuthSupport() {
    }

    /** 当前认证用户是否具备管理员角色（ROLE_ADMIN）；认证为空时返回 false。 */
    public static boolean isAdmin(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
