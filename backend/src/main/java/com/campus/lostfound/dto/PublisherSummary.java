package com.campus.lostfound.dto;

import com.campus.lostfound.entity.User;

/**
 * 详情中展示的发布者摘要。
 * 仅当发布者允许公开联系方式（contactPublic=true）时，才带出 contactType/contactValue；
 * 否则两者为 null，前端据此展示「通过站内会话联系」。
 */
public record PublisherSummary(
        Long id,
        String nickname,
        String contactType,
        String contactValue,
        boolean contactPublic
) {

    public static PublisherSummary from(User user) {
        boolean contactPublic = user.isContactPublic();
        return new PublisherSummary(
                user.getId(),
                user.getNickname(),
                contactPublic ? user.getContactType() : null,
                contactPublic ? user.getContactValue() : null,
                contactPublic
        );
    }
}
