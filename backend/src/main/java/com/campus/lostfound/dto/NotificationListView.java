package com.campus.lostfound.dto;

import java.util.List;

/**
 * 通知列表视图：通知条目（时间倒序）与当前未读数。
 */
public record NotificationListView(List<NotificationView> items, int unreadCount) {
}
