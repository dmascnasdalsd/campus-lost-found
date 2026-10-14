package com.campus.lostfound.repository;

import com.campus.lostfound.entity.Claim;
import com.campus.lostfound.entity.ClaimStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ClaimRepository extends JpaRepository<Claim, Long> {

    /** 判断某用户对某启事是否已存在指定状态（非终态）的申请，用于去重。 */
    boolean existsByReportIdAndApplicantIdAndStatusIn(Long reportId, Long applicantId,
                                                     Collection<ClaimStatus> statuses);

    /** 我发起的申请，按创建时间倒序。 */
    List<Claim> findByApplicantIdOrderByCreatedAtDesc(Long applicantId);

    /** 我收到的申请：由我发布的启事上的申请，按创建时间倒序。 */
    @Query("select c from Claim c, Report r where c.reportId = r.id and r.publisherId = :publisherId "
            + "order by c.createdAt desc")
    List<Claim> findReceivedByPublisherId(@Param("publisherId") Long publisherId);
}
