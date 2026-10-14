package com.campus.lostfound.dto;

import com.campus.lostfound.entity.User;
import com.campus.lostfound.entity.UserRole;
import com.campus.lostfound.entity.UserStatus;

import java.time.LocalDateTime;

/**
 * 返回给前端的当前用户视图，不包含密码哈希。
 */
public record UserView(
        Long id,
        String studentNo,
        String nickname,
        String department,
        String email,
        UserRole role,
        UserStatus status,
        String contactType,
        String contactValue,
        boolean contactPublic,
        LocalDateTime createdAt
) {

    public static UserView from(User user) {
        return new UserView(
                user.getId(),
                user.getStudentNo(),
                user.getNickname(),
                user.getDepartment(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.getContactType(),
                user.getContactValue(),
                user.isContactPublic(),
                user.getCreatedAt()
        );
    }
}
