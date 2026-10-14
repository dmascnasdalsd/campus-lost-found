package com.campus.lostfound.report;

import com.campus.lostfound.auth.User;

/**
 * 详情中展示的发布者摘要（不含联系方式，联系方式由工单 04 负责）。
 */
public record PublisherSummary(Long id, String nickname) {

    public static PublisherSummary from(User user) {
        return new PublisherSummary(user.getId(), user.getNickname());
    }
}
