package com.campus.lostfound.claim;

import com.campus.lostfound.entity.Report;
import com.campus.lostfound.entity.ReportStatus;
import com.campus.lostfound.entity.ReportType;
import com.campus.lostfound.entity.UserRole;
import com.campus.lostfound.repository.ReportRepository;
import com.campus.lostfound.service.JwtService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 工单 07：认领处理（接受 / 驳回）与交还完成的集成测试。
 * 沿用既有接缝：真实 HTTP + JdbcTemplate 夹具，只断言「返回什么、库中最终留下什么」。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ClaimHandlingApiTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    private long publisherId;
    private long applicantId;
    private long otherApplicantId;
    private long strangerId;
    private String publisherToken;
    private String applicantToken;
    private String otherApplicantToken;
    private String strangerToken;

    @BeforeEach
    void resetDatabase() {
        // 先删引用 app_user 的子表，避免外键约束阻断清理。
        jdbcTemplate.execute("DELETE FROM report_image");
        jdbcTemplate.execute("DELETE FROM message");
        jdbcTemplate.execute("DELETE FROM chat_session");
        jdbcTemplate.execute("DELETE FROM claim");
        jdbcTemplate.execute("DELETE FROM notification");
        jdbcTemplate.execute("DELETE FROM report_action");
        jdbcTemplate.execute("DELETE FROM report_flag");
        jdbcTemplate.execute("DELETE FROM report");
        jdbcTemplate.execute("DELETE FROM app_user");

        publisherId = insertUser("2021001", "发布者");
        applicantId = insertUser("2021002", "申请人");
        otherApplicantId = insertUser("2021003", "另一申请人");
        strangerId = insertUser("2021004", "路人");
        publisherToken = jwtService.generate(publisherId, UserRole.USER);
        applicantToken = jwtService.generate(applicantId, UserRole.USER);
        otherApplicantToken = jwtService.generate(otherApplicantId, UserRole.USER);
        strangerToken = jwtService.generate(strangerId, UserRole.USER);
    }

    @Test
    void 非发布者接受认领被拒并返回越权码() throws Exception {
        long reportId = saveReport(publisherId, "捡到的校园卡", ReportType.FOUND, ReportStatus.OPEN);
        long claimId = createClaim(reportId, applicantToken);

        HttpResult result = postAccept(claimId, strangerToken);

        assertThat(result.status()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(result.body().path("code").asInt()).isEqualTo(1004);
        assertThat(claimStatus(claimId)).isEqualTo("PENDING");
        assertThat(chatSessionCount()).isZero();
    }

    @Test
    void 接受后申请已接受启事认领中其他申请关闭且会话创建() throws Exception {
        long reportId = saveReport(publisherId, "捡到的校园卡", ReportType.FOUND, ReportStatus.OPEN);
        long acceptedClaimId = createClaim(reportId, applicantToken);
        long otherClaimId = createClaim(reportId, otherApplicantToken);

        HttpResult result = postAccept(acceptedClaimId, publisherToken);

        assertThat(result.status()).isEqualTo(HttpStatus.OK.value());
        assertThat(result.body().path("data").path("status").asText()).isEqualTo("ACCEPTED");
        assertThat(claimStatus(acceptedClaimId)).isEqualTo("ACCEPTED");
        assertThat(claimStatus(otherClaimId)).isEqualTo("CLOSED");
        assertThat(reportStatus(reportId)).isEqualTo("CLAIMING");

        assertThat(chatSessionCount()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT owner_id FROM chat_session WHERE claim_id = ?", Long.class, acceptedClaimId))
                .isEqualTo(publisherId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT applicant_id FROM chat_session WHERE claim_id = ?", Long.class, acceptedClaimId))
                .isEqualTo(applicantId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT report_id FROM chat_session WHERE claim_id = ?", Long.class, acceptedClaimId))
                .isEqualTo(reportId);
    }

    @Test
    void 对已接受的申请重复接受返回冲突码() throws Exception {
        long reportId = saveReport(publisherId, "捡到的校园卡", ReportType.FOUND, ReportStatus.OPEN);
        long claimId = createClaim(reportId, applicantToken);
        assertThat(postAccept(claimId, publisherToken).status()).isEqualTo(HttpStatus.OK.value());

        HttpResult second = postAccept(claimId, publisherToken);

        assertThat(second.status()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(second.body().path("code").asInt()).isEqualTo(1005);
    }

    @Test
    void 对已驳回的申请重复驳回返回冲突码() throws Exception {
        long reportId = saveReport(publisherId, "捡到的校园卡", ReportType.FOUND, ReportStatus.OPEN);
        long claimId = createClaim(reportId, applicantToken);
        assertThat(postReject(claimId, "特征不符", publisherToken).status())
                .isEqualTo(HttpStatus.OK.value());

        HttpResult second = postReject(claimId, "再次驳回", publisherToken);

        assertThat(second.status()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(second.body().path("code").asInt()).isEqualTo(1005);
    }

    @Test
    void 驳回缺少原因被拒并返回非法参数码() throws Exception {
        long reportId = saveReport(publisherId, "捡到的校园卡", ReportType.FOUND, ReportStatus.OPEN);
        long claimId = createClaim(reportId, applicantToken);

        HttpResult result = postReject(claimId, "", publisherToken);

        assertThat(result.status()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(result.body().path("code").asInt()).isEqualTo(1001);
        assertThat(claimStatus(claimId)).isEqualTo("PENDING");
    }

    @Test
    void 驳回成功后申请人可重新申请() throws Exception {
        long reportId = saveReport(publisherId, "捡到的校园卡", ReportType.FOUND, ReportStatus.OPEN);
        long claimId = createClaim(reportId, applicantToken);

        HttpResult rejected = postReject(claimId, "特征不符", publisherToken);
        assertThat(rejected.status()).isEqualTo(HttpStatus.OK.value());
        assertThat(rejected.body().path("data").path("status").asText()).isEqualTo("REJECTED");
        assertThat(rejected.body().path("data").path("rejectReason").asText()).isEqualTo("特征不符");
        assertThat(claimStatus(claimId)).isEqualTo("REJECTED");

        HttpResult again = postClaim(reportId, applicantToken);
        assertThat(again.status()).isEqualTo(HttpStatus.OK.value());
        assertThat(again.body().path("data").path("status").asText()).isEqualTo("PENDING");
        assertThat(claimCount()).isEqualTo(2);
    }

    @Test
    void 标记归还后已接受申请关闭且会话关闭() throws Exception {
        long reportId = saveReport(publisherId, "捡到的校园卡", ReportType.FOUND, ReportStatus.OPEN);
        long claimId = createClaim(reportId, applicantToken);
        assertThat(postAccept(claimId, publisherToken).status()).isEqualTo(HttpStatus.OK.value());

        HttpResult returned = putStatus(reportId, "RETURNED", publisherToken);
        assertThat(returned.status()).isEqualTo(HttpStatus.OK.value());
        assertThat(returned.body().path("data").path("status").asText()).isEqualTo("RETURNED");

        assertThat(claimStatus(claimId)).isEqualTo("CLOSED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM chat_session WHERE claim_id = ?", String.class, claimId))
                .isEqualTo("CLOSED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT closed_at FROM chat_session WHERE claim_id = ?",
                java.sql.Timestamp.class, claimId)).isNotNull();
    }

    // ---------- 请求辅助 ----------

    private HttpResult postAccept(long claimId, String token) throws Exception {
        return exchange(HttpMethod.POST, "/api/claims/" + claimId + "/accept", null, token);
    }

    private HttpResult postReject(long claimId, String reason, String token) throws Exception {
        String payload = "{\"rejectReason\":\"" + reason + "\"}";
        return exchange(HttpMethod.POST, "/api/claims/" + claimId + "/reject", payload, token);
    }

    private HttpResult postClaim(long reportId, String token) throws Exception {
        return exchange(HttpMethod.POST, "/api/reports/" + reportId + "/claims",
                "{\"featureDesc\":\"校园卡卡号后四位是 1234\"}", token);
    }

    private HttpResult putStatus(long reportId, String status, String token) throws Exception {
        return exchange(HttpMethod.PUT, "/api/reports/" + reportId + "/status",
                "{\"status\":\"" + status + "\"}", token);
    }

    private long createClaim(long reportId, String token) throws Exception {
        HttpResult result = postClaim(reportId, token);
        assertThat(result.status()).isEqualTo(HttpStatus.OK.value());
        return result.body().path("data").path("id").asLong();
    }

    private HttpResult exchange(HttpMethod method, String path, String payload, String token)
            throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        ResponseEntity<String> response = restTemplate.exchange(
                path, method, new HttpEntity<>(payload, headers), String.class);
        return new HttpResult(response.getStatusCode().value(), readTree(response.getBody()));
    }

    private JsonNode readTree(String raw) {
        try {
            return raw == null ? objectMapper.createObjectNode() : objectMapper.readTree(raw);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private long saveReport(long ownerId, String title, ReportType type, ReportStatus status) {
        Report report = new Report();
        report.setType(type);
        report.setTitle(title);
        report.setCategory("校园卡");
        report.setLocation("图书馆");
        report.setEventTime(LocalDateTime.now().minusHours(1));
        report.setStatus(status);
        report.setPublisherId(ownerId);
        report.setDescription("描述");
        return reportRepository.save(report).getId();
    }

    private String claimStatus(long claimId) {
        return jdbcTemplate.queryForObject("SELECT status FROM claim WHERE id = ?", String.class, claimId);
    }

    private String reportStatus(long reportId) {
        return jdbcTemplate.queryForObject("SELECT status FROM report WHERE id = ?", String.class, reportId);
    }

    private int claimCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM claim", Integer.class);
    }

    private int chatSessionCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chat_session", Integer.class);
    }

    private long insertUser(String studentNo, String nickname) {
        jdbcTemplate.update("""
                INSERT INTO app_user (student_no, nickname, password_hash, role, status, contact_public, created_at, updated_at)
                VALUES (?, ?, ?, 'USER', 'ACTIVE', 0, NOW(6), NOW(6))
                """, studentNo, nickname, "$2a$10$abcdefghijklmnopqrstuv");
        return jdbcTemplate.queryForObject("SELECT id FROM app_user WHERE student_no = ?", Long.class, studentNo);
    }

    private record HttpResult(int status, JsonNode body) {
    }
}
