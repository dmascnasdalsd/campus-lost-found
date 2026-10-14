package com.campus.lostfound.flag;

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
 * 工单 10：举报闭环的集成测试。
 * 沿用既有接缝：真实 HTTP + JdbcTemplate 夹具，只断言「返回什么、库中最终留下什么」。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class FlagApiTest {

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

    private long adminId;
    private long publisherId;
    private long reporterId;
    private String adminToken;
    private String userToken;
    private String reporterToken;

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

        adminId = insertUser("9000001", "管理员", "ADMIN");
        publisherId = insertUser("2021001", "发布者", "USER");
        reporterId = insertUser("2021009", "举报人", "USER");
        adminToken = jwtService.generate(adminId, UserRole.ADMIN);
        userToken = jwtService.generate(publisherId, UserRole.USER);
        reporterToken = jwtService.generate(reporterId, UserRole.USER);
    }

    @Test
    void 举报启事后启事进入待复核并出现在复核队列() {
        long reportId = saveReport(publisherId, "被举报的启事", ReportStatus.OPEN);

        HttpResult result = post("/api/reports/" + reportId + "/flags",
                "{\"reason\":\"FAKE\",\"detail\":\"疑似虚假信息\"}", reporterToken);

        assertThat(result.status()).isEqualTo(HttpStatus.OK.value());
        assertThat(result.body().path("data").path("targetType").asText()).isEqualTo("REPORT");
        assertThat(result.body().path("data").path("reason").asText()).isEqualTo("FAKE");
        assertThat(result.body().path("data").path("targetSummary").asText()).isEqualTo("被举报的启事");
        assertThat(result.body().path("data").path("reporterNickname").asText()).isEqualTo("举报人");

        assertThat(reportStatus(reportId)).isEqualTo("PENDING_REVIEW");

        JsonNode pending = get("/api/admin/reports?status=PENDING_REVIEW", adminToken)
                .body().path("data");
        assertThat(pending.path("total").asLong()).isEqualTo(1);
        assertThat(pending.path("items").get(0).path("title").asText()).isEqualTo("被举报的启事");
    }

    @Test
    void 非法举报理由返回非法参数() {
        long reportId = saveReport(publisherId, "正常启事", ReportStatus.OPEN);

        HttpResult result = post("/api/reports/" + reportId + "/flags",
                "{\"reason\":\"UNKNOWN\"}", reporterToken);

        assertThat(result.status()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(result.body().path("code").asInt()).isEqualTo(1001);
    }

    @Test
    void 下架处置后举报已处理且启事在默认列表不可见() {
        long reportId = saveReport(publisherId, "违规启事", ReportStatus.OPEN);
        long flagId = flagReport(reportId);

        HttpResult resolved = post("/api/admin/flags/" + flagId + "/resolve",
                "{\"action\":\"TAKE_DOWN\"}", adminToken);

        assertThat(resolved.status()).isEqualTo(HttpStatus.OK.value());
        assertThat(resolved.body().path("data").path("status").asText()).isEqualTo("RESOLVED");
        assertThat(resolved.body().path("data").path("handleResult").asText()).isEqualTo("TAKE_DOWN");
        assertThat(flagColumn(flagId, "status")).isEqualTo("RESOLVED");
        assertThat(flagColumn(flagId, "handle_result")).isEqualTo("TAKE_DOWN");
        assertThat(reportStatus(reportId)).isEqualTo("OFFLINE");

        JsonNode defaultList = get("/api/reports", null).body().path("data").path("items");
        assertThat(titlesOf(defaultList)).doesNotContain("违规启事");

        JsonNode withClosed = get("/api/reports?includeClosed=true", null)
                .body().path("data").path("items");
        assertThat(titlesOf(withClosed)).contains("违规启事");
    }

    @Test
    void 禁用用户处置后违规者被禁用且无法登录() {
        long reportId = saveReport(publisherId, "违规启事", ReportStatus.OPEN);
        long flagId = flagReport(reportId);

        HttpResult resolved = post("/api/admin/flags/" + flagId + "/resolve",
                "{\"action\":\"DISABLE_USER\"}", adminToken);
        assertThat(resolved.status()).isEqualTo(HttpStatus.OK.value());
        assertThat(resolved.body().path("data").path("handleResult").asText()).isEqualTo("DISABLE_USER");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM app_user WHERE id = ?", String.class, publisherId))
                .isEqualTo("DISABLED");

        HttpResult login = post("/api/auth/login",
                "{\"studentNo\":\"2021001\",\"password\":\"whatever\"}", null);
        assertThat(login.status()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(login.body().path("code").asInt()).isEqualTo(1004);
    }

    @Test
    void 驳回举报后标记已驳回且启事状态不变() {
        long reportId = saveReport(publisherId, "待核准启事", ReportStatus.OPEN);
        long flagId = flagReport(reportId);
        assertThat(reportStatus(reportId)).isEqualTo("PENDING_REVIEW");

        HttpResult resolved = post("/api/admin/flags/" + flagId + "/resolve",
                "{\"action\":\"DISMISS\"}", adminToken);

        assertThat(resolved.status()).isEqualTo(HttpStatus.OK.value());
        assertThat(resolved.body().path("data").path("status").asText()).isEqualTo("DISMISSED");
        assertThat(flagColumn(flagId, "status")).isEqualTo("DISMISSED");
        assertThat(flagColumn(flagId, "handle_result")).isEqualTo("DISMISS");
        assertThat(reportStatus(reportId)).isEqualTo("PENDING_REVIEW");
    }

    @Test
    void 重复处置同一举报返回冲突() {
        long reportId = saveReport(publisherId, "违规启事", ReportStatus.OPEN);
        long flagId = flagReport(reportId);

        assertThat(post("/api/admin/flags/" + flagId + "/resolve",
                "{\"action\":\"DISMISS\"}", adminToken).status())
                .isEqualTo(HttpStatus.OK.value());

        HttpResult again = post("/api/admin/flags/" + flagId + "/resolve",
                "{\"action\":\"DISMISS\"}", adminToken);
        assertThat(again.status()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(again.body().path("code").asInt()).isEqualTo(1005);
    }

    @Test
    void 普通用户访问举报列表返回越权而访客未授权() {
        HttpResult asUser = get("/api/admin/flags", userToken);
        assertThat(asUser.status()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(asUser.body().path("code").asInt()).isEqualTo(1004);

        HttpResult asGuest = get("/api/admin/flags", null);
        assertThat(asGuest.status()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(asGuest.body().path("code").asInt()).isEqualTo(1003);
    }

    @Test
    void 举报消息落库为消息类型且下架处置后消息被删除() {
        long messageId = saveMessage(publisherId, "辱骂内容正文一二三四五六七八九十");

        HttpResult result = post("/api/messages/" + messageId + "/flags",
                "{\"reason\":\"ABUSE\"}", reporterToken);
        assertThat(result.status()).isEqualTo(HttpStatus.OK.value());
        assertThat(result.body().path("data").path("targetType").asText()).isEqualTo("MESSAGE");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT target_type FROM report_flag WHERE target_id = ?", String.class, messageId))
                .isEqualTo("MESSAGE");

        long flagId = result.body().path("data").path("id").asLong();
        assertThat(post("/api/admin/flags/" + flagId + "/resolve",
                "{\"action\":\"TAKE_DOWN\"}", adminToken).status())
                .isEqualTo(HttpStatus.OK.value());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM message WHERE id = ?", Integer.class, messageId))
                .isZero();
    }

    // ---------- 请求辅助 ----------

    private long flagReport(long reportId) {
        HttpResult result = post("/api/reports/" + reportId + "/flags",
                "{\"reason\":\"AD\"}", reporterToken);
        assertThat(result.status()).isEqualTo(HttpStatus.OK.value());
        return result.body().path("data").path("id").asLong();
    }

    private HttpResult get(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        ResponseEntity<String> response = restTemplate.exchange(
                path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        return new HttpResult(response.getStatusCode().value(), readTree(response.getBody()));
    }

    private HttpResult post(String path, String payload, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        ResponseEntity<String> response = restTemplate.exchange(
                path, HttpMethod.POST, new HttpEntity<>(payload, headers), String.class);
        return new HttpResult(response.getStatusCode().value(), readTree(response.getBody()));
    }

    private JsonNode readTree(String raw) {
        try {
            return raw == null ? objectMapper.createObjectNode() : objectMapper.readTree(raw);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static java.util.List<String> titlesOf(JsonNode items) {
        java.util.List<String> titles = new java.util.ArrayList<>();
        items.forEach(item -> titles.add(item.path("title").asText()));
        return titles;
    }

    private String reportStatus(long reportId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM report WHERE id = ?", String.class, reportId);
    }

    private String flagColumn(long flagId, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM report_flag WHERE id = ?", String.class, flagId);
    }

    private long saveReport(long ownerId, String title, ReportStatus status) {
        Report report = new Report();
        report.setType(ReportType.FOUND);
        report.setTitle(title);
        report.setCategory("校园卡");
        report.setLocation("图书馆");
        report.setEventTime(LocalDateTime.now().minusHours(1));
        report.setStatus(status);
        report.setPublisherId(ownerId);
        report.setDescription("描述");
        return reportRepository.save(report).getId();
    }

    private long saveMessage(long senderId, String body) {
        long reportId = saveReport(senderId, "会话关联启事", ReportStatus.OPEN);
        jdbcTemplate.update("""
                INSERT INTO claim (report_id, applicant_id, direction, feature_desc, status, created_at, updated_at)
                VALUES (?, ?, 'APPLY_CLAIM', '特征说明', 'PENDING', NOW(6), NOW(6))
                """, reportId, senderId);
        long claimId = jdbcTemplate.queryForObject(
                "SELECT id FROM claim WHERE report_id = ?", Long.class, reportId);
        jdbcTemplate.update("""
                INSERT INTO chat_session (claim_id, report_id, owner_id, applicant_id, status, created_at)
                VALUES (?, ?, ?, ?, 'OPEN', NOW(6))
                """, claimId, reportId, senderId, senderId);
        long sessionId = jdbcTemplate.queryForObject(
                "SELECT id FROM chat_session WHERE claim_id = ?", Long.class, claimId);
        jdbcTemplate.update("""
                INSERT INTO message (session_id, sender_id, body, created_at)
                VALUES (?, ?, ?, NOW(6))
                """, sessionId, senderId, body);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM message WHERE session_id = ?", Long.class, sessionId);
    }

    private long insertUser(String studentNo, String nickname, String role) {
        jdbcTemplate.update("""
                INSERT INTO app_user (student_no, nickname, password_hash, role, status, contact_public, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', 0, NOW(6), NOW(6))
                """, studentNo, nickname, "$2a$10$abcdefghijklmnopqrstuv", role);
        return jdbcTemplate.queryForObject("SELECT id FROM app_user WHERE student_no = ?", Long.class, studentNo);
    }

    private record HttpResult(int status, JsonNode body) {
    }
}
