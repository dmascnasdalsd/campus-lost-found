package com.campus.lostfound.entity;

/**
 * 认领申请方向：
 * APPLY_CLAIM —— 针对招领（FOUND）启事，申请人主张自己是被拾获物品的失主；
 * APPLY_MATCH —— 针对失物（LOST）启事，申请人主张自己拾获了该物品。
 */
public enum ClaimDirection {
    APPLY_CLAIM,
    APPLY_MATCH
}
