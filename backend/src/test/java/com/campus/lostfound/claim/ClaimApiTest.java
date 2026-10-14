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
 * 工单 06：认领申请的集成测试。
 * 沿用既有接缝：真实 HTTP + JdbcTemplate 夹具，只断言「返回什么、库中最终留下什么」。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ClaimApiTest {

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
    private String publisherToken;
    private String applicantToken;

    @BeforeEach
    void 每个用例前清空数据库() {
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
        publisherToken = jwtService.generate(publisherId, UserRole.USER);
        applicantToken = jwtService.generate(applicantId, UserRole.USER);
    }

    @Test
    void 对自己发布的启事发起认领被拒并返回越权码() throws Exception {
        long reportId = saveReport(publisherId, "捡到的校园卡", ReportType.FOUND, ReportStatus.OPEN);

        HttpResult result = postClaim(reportId, validPayload(), publisherToken);

        assertThat(result.status()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(result.body().path("code").asInt()).isEqualTo(1004);
        assertThat(claimCount()).isZero();
    }

    @Test
    void 重复申请被拒并返回冲突错误码() throws Exception {
        long reportId = saveReport(publisherId, "捡到的校园卡", ReportType.FOUND, ReportStatus.OPEN);

        assertThat(postClaim(reportId, validPayload(), applicantToken).status())
                .isEqualTo(HttpStatus.OK.value());

        HttpResult second = postClaim(reportId, validPayload(), applicantToken);
        assertThat(second.status()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(second.body().path("code").asInt()).isEqualTo(1005);
        assertThat(claimCount()).isEqualTo(1);
    }

    @Test
    void 对已归还的启事发起申请被拒() throws Exception {
        long reportId = saveReport(publisherId, "已归还的校园卡", ReportType.LOST, ReportStatus.RETURNED);

        HttpResult result = postClaim(reportId, validPayload(), applicantToken);

        assertThat(result.status()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(result.body().path("code").asInt()).isEqualTo(1001);
        assertThat(result.body().path("code").asInt()).isNotZero();
        assertThat(claimCount()).isZero();
    }

    @Test
    void 在认领中的启事上发起申请被拒() throws Exception {
        long reportId = saveReport(publisherId, "认领中的校园卡", ReportType.LOST, ReportStatus.CLAIMING);

        HttpResult result = postClaim(reportId, validPayload(), applicantToken);

        assertThat(result.status()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(result.body().path("code").asInt()).isEqualTo(1001);
        assertThat(claimCount()).isZero();
    }

    @Test
    void 在待复核的启事上发起申请被拒() throws Exception {
        long reportId = saveReport(publisherId, "待复核的校园卡", ReportType.LOST, ReportStatus.PENDING_REVIEW);

        HttpResult result = postClaim(reportId, validPayload(), applicantToken);

        assertThat(result.status()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(result.body().path("code").asInt()).isEqualTo(1001);
        assertThat(claimCount()).isZero();
    }

    @Test
    void 撤回后可重新申请() throws Exception {
        long reportId = saveReport(publisherId, "捡到的校园卡", ReportType.FOUND, ReportStatus.OPEN);

        HttpResult first = postClaim(reportId, validPayload(), applicantToken);
        assertThat(first.status()).isEqualTo(HttpStatus.OK.value());
        long claimId = first.body().path("data").path("id").asLong();

        HttpResult withdrew = postClaimWithdraw(claimId, applicantToken);
        assertThat(withdrew.status()).isEqualTo(HttpStatus.OK.value());
        assertThat(withdrew.body().path("data").path("status").asText()).isEqualTo("WITHDRAWN");

        HttpResult again = postClaim(reportId, validPayload(), applicantToken);
        assertThat(again.status()).isEqualTo(HttpStatus.OK.value());
        assertThat(again.body().path("data").path("status").asText()).isEqualTo("PENDING");
        assertThat(claimCount()).isEqualTo(2);
    }

    @Test
    void 未登录访问我的认领返回未授权() {
        HttpResult result = getClaims(null, null);

        assertThat(result.status()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(result.body().path("code").asInt()).isEqualTo(1003);
    }

    @Test
    void applied与received分别返回我发起和我收到的申请() throws Exception {
        long reportId = saveReport(publisherId, "捡到的校园卡", ReportType.FOUND, ReportStatus.OPEN);
        assertThat(postClaim(reportId, validPayload(), applicantToken).status())
                .isEqualTo(HttpStatus.OK.value());

        JsonNode applied = getClaims("applied", applicantToken).body().path("data");
        assertThat(applied).hasSize(1);
        assertThat(applied.get(0).path("report").path("title").asText()).isEqualTo("捡到的校园卡");

        JsonNode received = getClaims("received", publisherToken).body().path("data");
        assertThat(received).hasSize(1);
        assertThat(received.get(0).path("applicantNickname").asText()).isEqualTo("申请人");
        assertThat(received.get(0).path("report").path("title").asText()).isEqualTo("捡到的校园卡");

        assertThat(getClaims("applied", publisherToken).body().path("data")).isEmpty();
    }

    @Test
    void 申请方向与字段按启事类型写入并落库() throws Exception {
        long foundReportId = saveReport(publisherId, "捡到的耳机", ReportType.FOUND, ReportStatus.OPEN);
        JsonNode foundClaim = postClaim(foundReportId,
                """
                {"featureDesc":"耳机盒上有一道划痕","expectTime":"2026-10-15T09:00:00","expectPlace":"图书馆一楼"}
                """, applicantToken).body().path("data");
        assertThat(foundClaim.path("direction").asText()).isEqualTo("APPLY_CLAIM");
        assertThat(foundClaim.path("expectPlace").asText()).isEqualTo("图书馆一楼");

        long lostReportId = saveReport(publisherId, "丢失的校园卡", ReportType.LOST, ReportStatus.OPEN);
        JsonNode lostClaim = postClaim(lostReportId, validPayload(), applicantToken).body().path("data");
        assertThat(lostClaim.path("direction").asText()).isEqualTo("APPLY_MATCH");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT direction FROM claim WHERE report_id = ?", String.class, foundReportId))
                .isEqualTo("APPLY_CLAIM");
    }

    @Test
    void 非法role取值被拒() throws Exception {
        HttpResult result = getClaims("unknown", applicantToken);

        assertThat(result.status()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(result.body().path("code").asInt()).isEqualTo(1001);
    }

    @Test
    void 非申请人撤回被拒() throws Exception {
        long reportId = saveReport(publisherId, "捡到的校园卡", ReportType.FOUND, ReportStatus.OPEN);
        long claimId = postClaim(reportId, validPayload(), applicantToken)
                .body().path("data").path("id").asLong();

        HttpResult result = postClaimWithdraw(claimId, publisherToken);

        assertThat(result.status()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(result.body().path("code").asInt()).isEqualTo(1004);
    }

    // ---------- 请求辅助 ----------

    private String validPayload() {
        return """
                {"featureDesc":"校园卡卡号后四位是 1234","expectPlace":"图书馆一楼服务台"}
                """;
    }

    private HttpResult postClaim(long reportId, String payload, String token) throws Exception {
        ClientResponse response = exchange(HttpMethod.POST, "/api/reports/" + reportId + "/claims",
                payload, token);
        return new HttpResult(response.status(), response.body());
    }

    private HttpResult postClaimWithdraw(long claimId, String token) throws Exception {
        ClientResponse response = exchange(HttpMethod.POST, "/api/claims/" + claimId + "/withdraw",
                null, token);
        return new HttpResult(response.status(), response.body());
    }

    private HttpResult getClaims(String role, String token) {
        String path = role == null ? "/api/claims" : "/api/claims?role=" + role;
        HttpHeaders httpHeaders = new HttpHeaders();
        if (token != null) {
            httpHeaders.setBearerAuth(token);
        }
        ResponseEntity<String> response = restTemplate.exchange(
                path, HttpMethod.GET, new HttpEntity<>(httpHeaders), String.class);
        return new HttpResult(response.getStatusCode().value(), readTree(response.getBody()));
    }

    private ClientResponse exchange(HttpMethod method, String path, String payload, String token)
            throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        ResponseEntity<String> response = restTemplate.exchange(
                path, method, new HttpEntity<>(payload, headers), String.class);
        return new ClientResponse(response.getStatusCode().value(), readTree(response.getBody()));
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

    private int claimCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM claim", Integer.class);
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

    private record ClientResponse(int status, JsonNode body) {
    }
}
