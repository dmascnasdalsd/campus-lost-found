package com.campus.lostfound.entity;

/**
 * 举报理由：虚假信息 / 广告 / 辱骂 / 其他。
 * label 为中文文案，用于下架审计原因等展示场景。
 */
public enum FlagReason {
    FAKE("虚假信息"),
    AD("广告"),
    ABUSE("辱骂"),
    OTHER("其他");

    private final String label;

    FlagReason(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
