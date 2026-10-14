package com.campus.lostfound.repository;

import com.campus.lostfound.entity.ChatSession;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    /** 认领申请对应的唯一会话（claim_id 唯一）。 */
    Optional<ChatSession> findByClaimId(Long claimId);

    /** 我参与的会话（我作为发布者或申请人），按创建时间倒序。 */
    List<ChatSession> findByOwnerIdOrApplicantIdOrderByCreatedAtDesc(Long ownerId, Long applicantId);

    /** 某启事下的全部会话，用于交还完成时统一关闭。 */
    List<ChatSession> findByReportId(Long reportId);
}
