package com.campus.lostfound.admin;

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
 * 工单 09：管理员内容治理的集成测试。
 * 沿用既有接缝：真实 HTTP + JdbcTemplate 夹具，只断言「返回什么、库中最终留下什么」。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AdminApiTest {

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
    private String adminToken;
    private String userToken;

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

        adminId = insertUser("9000001", "管理员", "ADMIN");
        publisherId = insertUser("2021001", "发布者", "USER");
        adminToken = jwtService.generate(adminId, UserRole.ADMIN);
        userToken = jwtService.generate(publisherId, UserRole.USER);
    }

    @Test
    void 访客访问管理接口返回未授权() {
        HttpResult result = get("/api/admin/reports", null);

        assertThat(result.status()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(result.body().path("code").asInt()).isEqualTo(1003);
    }

    @Test
    void 普通用户访问管理接口返回越权() {
        HttpResult result = get("/api/admin/reports", userToken);

        assertThat(result.status()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(result.body().path("code").asInt()).isEqualTo(1004);
    }

    @Test
    void 待复核筛选只返回待复核启事而全部返回所有启事() {
        saveReport(publisherId, "待复核的启事", ReportStatus.PENDING_REVIEW);
        saveReport(publisherId, "正常招领的启事", ReportStatus.OPEN);

        JsonNode pending = get("/api/admin/reports?status=PENDING_REVIEW", adminToken).body().path("data");
        assertThat(pending.path("total").asLong()).isEqualTo(1);
        assertThat(pending.path("items").get(0).path("title").asText()).isEqualTo("待复核的启事");

        JsonNode all = get("/api/admin/reports", adminToken).body().path("data");
        assertThat(all.path("total").asLong()).isEqualTo(2);
    }

    @Test
    void 非法状态筛选返回非法参数() {
        HttpResult result = get("/api/admin/reports?status=UNKNOWN", adminToken);

        assertThat(result.status()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(result.body().path("code").asInt()).isEqualTo(1001);
    }

    @Test
    void 下架后启事在默认列表不可见而includeClosed可见() throws Exception {
        long reportId = saveReport(publisherId, "违规启事", ReportStatus.OPEN);

        HttpResult taken = post("/api/admin/reports/" + reportId + "/take-down",
                "{\"reason\":\"包含违规内容\"}", adminToken);
        assertThat(taken.status()).isEqualTo(HttpStatus.OK.value());
        assertThat(taken.body().path("data").path("status").asText()).isEqualTo("OFFLINE");

        JsonNode defaultList = get("/api/reports", null).body().path("data").path("items");
        assertThat(titlesOf(defaultList)).doesNotContain("违规启事");

        JsonNode withClosed = get("/api/reports?includeClosed=true", null).body().path("data").path("items");
        assertThat(titlesOf(withClosed)).contains("违规启事");
    }

    @Test
    void 复核通过后启事恢复为OPEN并出现在默认列表() throws Exception {
        long reportId = saveReport(publisherId, "被举报的启事", ReportStatus.PENDING_REVIEW);

        HttpResult approved = post("/api/admin/reports/" + reportId + "/approve", null, adminToken);
        assertThat(approved.status()).isEqualTo(HttpStatus.OK.value());
        assertThat(approved.body().path("data").path("status").asText()).isEqualTo("OPEN");

        JsonNode items = get("/api/reports", null).body().path("data").path("items");
        assertThat(titlesOf(items)).contains("被举报的启事");
    }

    @Test
    void 驳回缺少原因返回非法参数() throws Exception {
        long reportId = saveReport(publisherId, "待驳回启事", ReportStatus.PENDING_REVIEW);

        HttpResult result = post("/api/admin/reports/" + reportId + "/reject",
                "{\"reason\":\"   \"}", adminToken);

        assertThat(result.status()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(result.body().path("code").asInt()).isEqualTo(1001);
        assertThat(actionCount()).isZero();
    }

    @Test
    void 治理动作写入审计记录且字段正确() throws Exception {
        long reportId = saveReport(publisherId, "违规启事", ReportStatus.OPEN);

        assertThat(post("/api/admin/reports/" + reportId + "/take-down",
                "{\"reason\":\"包含违规内容\"}", adminToken).status())
                .isEqualTo(HttpStatus.OK.value());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT operator_id FROM report_action WHERE report_id = ?", Long.class, reportId))
                .isEqualTo(adminId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT action FROM report_action WHERE report_id = ?", String.class, reportId))
                .isEqualTo("TAKE_DOWN");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT reason FROM report_action WHERE report_id = ?", String.class, reportId))
                .isEqualTo("包含违规内容");
    }

    @Test
    void 审计日志返回操作人昵称与动作() throws Exception {
        long reportId = saveReport(publisherId, "待置顶启事", ReportStatus.OPEN);

        assertThat(post("/api/admin/reports/" + reportId + "/pin", null, adminToken).status())
                .isEqualTo(HttpStatus.OK.value());

        JsonNode log = get("/api/admin/audit-logs", adminToken).body().path("data").path("items").get(0);
        assertThat(log.path("operatorNickname").asText()).isEqualTo("管理员");
        assertThat(log.path("action").asText()).isEqualTo("PIN");
        assertThat(log.path("reportId").asLong()).isEqualTo(reportId);
        assertThat(log.path("reportTitle").asText()).isEqualTo("待置顶启事");
    }

    @Test
    void 对不存在的启事治理返回未找到() throws Exception {
        HttpResult result = post("/api/admin/reports/999999/approve", null, adminToken);

        assertThat(result.status()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(result.body().path("code").asInt()).isEqualTo(1002);
    }

    // ---------- 请求辅助 ----------

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

    private int actionCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM report_action", Integer.class);
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
