package com.campus.lostfound.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 站内会话实体，映射基线表 chat_session。
 * 一个被接受的认领申请对应唯一会话（claim_id 唯一），仅发布者与申请人对双方可见。
 * 只保存外键 id 值，不做 JPA 关联，读取时由 Service 显式装配启事摘要与对方昵称。
 */
@Entity
@Table(name = "chat_session")
@Getter
@Setter
@NoArgsConstructor
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_id", nullable = false)
    private Long claimId;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "applicant_id", nullable = false)
    private Long applicantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ChatSessionStatus status = ChatSessionStatus.OPEN;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
