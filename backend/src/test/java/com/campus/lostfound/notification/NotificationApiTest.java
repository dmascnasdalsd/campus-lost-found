package com.campus.lostfound.notification;

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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 工单 08：通知中心的集成测试。
 * 沿用既有接缝：真实 HTTP + JdbcTemplate 夹具，只断言「各业务事件产生什么通知、返回什么」。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class NotificationApiTest {

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
    private long applicantId;
    private long otherApplicantId;
    private long strangerId;
    private String adminToken;
    private String publisherToken;
    private String applicantToken;
    private String otherApplicantToken;
    private String strangerToken;

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
        applicantId = insertUser("2021002", "申请人", "USER");
        otherApplicantId = insertUser("2021003", "其他申请人", "USER");
        strangerId = insertUser("2021004", "路人", "USER");
        adminToken = jwtService.generate(adminId, UserRole.ADMIN);
        publisherToken = jwtService.generate(publisherId, UserRole.USER);
        applicantToken = jwtService.generate(applicantId, UserRole.USER);
        otherApplicantToken = jwtService.generate(otherApplicantId, UserRole.USER);
        strangerToken = jwtService.generate(strangerId, UserRole.USER);
    }

    @Test
    void 认领申请创建后通知发布者且不通知无关用户() throws Exception {
        long reportId = saveReport(publisherId, "捡到的校园卡", ReportType.FOUND, ReportStatus.OPEN);
        long claimId = postClaim(reportId, applicantToken);

        assertThat(typesOf(publisherId)).containsExactly("CLAIM_CREATED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT target_type FROM notification WHERE recipient_id = ?", String.class, publisherId))
                .isEqualTo("CLAIM");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT target_id FROM notification WHERE recipient_id = ?", Long.class, publisherId))
                .isEqualTo(claimId);
        // 申请人与无关用户都不应收到通知（只发给相关用户）。
        assertThat(typesOf(applicantId)).isEmpty();
        assertThat(typesOf(strangerId)).isEmpty();
    }

    @Test
    void 接受申请通知申请人并通知被自动关闭的其他申请人() throws Exception {
        long reportId = saveReport(publisherId, "捡到的校园卡", ReportType.FOUND, ReportStatus.OPEN);
        long claimA = postClaim(reportId, applicantToken);
        long claimB = postClaim(reportId, otherApplicantToken);

        assertThat(post("/api/claims/" + claimA + "/accept", null, publisherToken).status())
                .isEqualTo(HttpStatus.OK.value());

        assertThat(typesOf(applicantId)).containsExactly("CLAIM_ACCEPTED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT target_id FROM notification WHERE recipient_id = ?", Long.class, applicantId))
                .isEqualTo(claimA);
        assertThat(typesOf(otherApplicantId)).containsExactly("CLAIM_CLOSED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT target_id FROM notification WHERE recipient_id = ?", Long.class, otherApplicantId))
                .isEqualTo(claimB);
    }

    @Test
    void 驳回申请通知申请人() throws Exception {
        long reportId = saveReport(publisherId, "捡到的校园卡", ReportType.FOUND, ReportStatus.OPEN);
        long claimId = postClaim(reportId, applicantToken);

        assertThat(post("/api/claims/" + claimId + "/reject",
                "{\"rejectReason\":\"特征不符\"}", publisherToken).status())
                .isEqualTo(HttpStatus.OK.value());

        assertThat(typesOf(applicantId)).containsExactly("CLAIM_REJECTED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT target_id FROM notification WHERE recipient_id = ?", Long.class, applicantId))
                .isEqualTo(claimId);
    }

    @Test
    void 会话发消息通知另一方() throws Exception {
        long reportId = saveReport(publisherId, "捡到的校园卡", ReportType.FOUND, ReportStatus.OPEN);
        long claimId = postClaim(reportId, applicantToken);
        assertThat(post("/api/claims/" + claimId + "/accept", null, publisherToken).status())
                .isEqualTo(HttpStatus.OK.value());
        long sessionId = jdbcTemplate.queryForObject(
                "SELECT id FROM chat_session WHERE claim_id = ?", Long.class, claimId);

        assertThat(post("/api/chats/" + sessionId + "/messages",
                "{\"body\":\"你好\"}", publisherToken).status())
                .isEqualTo(HttpStatus.OK.value());

        // accept 阶段已给申请人发过 CLAIM_ACCEPTED，这里只断言新增了 MESSAGE_RECEIVED。
        assertThat(typesOf(applicantId)).contains("MESSAGE_RECEIVED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT target_type FROM notification WHERE recipient_id = ? AND type = 'MESSAGE_RECEIVED'",
                String.class, applicantId))
                .isEqualTo("SESSION");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT target_id FROM notification WHERE recipient_id = ? AND type = 'MESSAGE_RECEIVED'",
                Long.class, applicantId))
                .isEqualTo(sessionId);
    }

    @Test
    void 管理员下架通知发布者() throws Exception {
        long reportId = saveReport(publisherId, "违规启事", ReportType.FOUND, ReportStatus.OPEN);

        assertThat(post("/api/admin/reports/" + reportId + "/take-down",
                "{\"reason\":\"包含违规内容\"}", adminToken).status())
                .isEqualTo(HttpStatus.OK.value());

        assertThat(typesOf(publisherId)).containsExactly("REPORT_TAKE_DOWN");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT target_type FROM notification WHERE recipient_id = ?", String.class, publisherId))
                .isEqualTo("REPORT");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT target_id FROM notification WHERE recipient_id = ?", Long.class, publisherId))
                .isEqualTo(reportId);
    }

    @Test
    void 管理员驳回通知发布者() throws Exception {
        long reportId = saveReport(publisherId, "待驳回启事", ReportType.FOUND, ReportStatus.PENDING_REVIEW);

        assertThat(post("/api/admin/reports/" + reportId + "/reject",
                "{\"reason\":\"信息不实\"}", adminToken).status())
                .isEqualTo(HttpStatus.OK.value());

        assertThat(typesOf(publisherId)).containsExactly("REPORT_REJECTED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT target_id FROM notification WHERE recipient_id = ?", Long.class, publisherId))
                .isEqualTo(reportId);
    }

    @Test
    void 列表返回未读数并在标记单条与全部已读后归零() throws Exception {
        long reportId = saveReport(publisherId, "捡到的校园卡", ReportType.FOUND, ReportStatus.OPEN);
        postClaim(reportId, applicantToken);
        postClaim(reportId, otherApplicantToken);

        JsonNode list = get("/api/notifications", publisherToken).body().path("data");
        assertThat(list.path("unreadCount").asInt()).isEqualTo(2);
        assertThat(list.path("items")).hasSize(2);
        // 列表按时间倒序。
        assertThat(list.path("items").get(0).path("id").asLong())
                .isGreaterThan(list.path("items").get(1).path("id").asLong());

        long firstId = list.path("items").get(0).path("id").asLong();
        assertThat(post("/api/notifications/" + firstId + "/read", null, publisherToken).status())
                .isEqualTo(HttpStatus.OK.value());

        JsonNode afterOne = get("/api/notifications", publisherToken).body().path("data");
        assertThat(afterOne.path("unreadCount").asInt()).isEqualTo(1);

        assertThat(post("/api/notifications/read-all", null, publisherToken).status())
                .isEqualTo(HttpStatus.OK.value());

        JsonNode afterAll = get("/api/notifications", publisherToken).body().path("data");
        assertThat(afterAll.path("unreadCount").asInt()).isZero();
    }

    @Test
    void 无关用户的通知列表为空且未读数为零() throws Exception {
        long reportId = saveReport(publisherId, "捡到的校园卡", ReportType.FOUND, ReportStatus.OPEN);
        postClaim(reportId, applicantToken);

        JsonNode list = get("/api/notifications", strangerToken).body().path("data");
        assertThat(list.path("items")).isEmpty();
        assertThat(list.path("unreadCount").asInt()).isZero();
    }

    @Test
    void 未登录访问通知列表返回未授权() {
        HttpResult result = get("/api/notifications", null);

        assertThat(result.status()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(result.body().path("code").asInt()).isEqualTo(1003);
    }

    // ---------- 请求辅助 ----------

    private long postClaim(long reportId, String token) throws Exception {
        HttpResult result = post("/api/reports/" + reportId + "/claims",
                "{\"featureDesc\":\"校园卡卡号后四位是 1234\"}", token);
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

    /** 指定收件人的通知类型（按 id 升序）。 */
    private List<String> typesOf(long recipientId) {
        return jdbcTemplate.queryForList(
                "SELECT type FROM notification WHERE recipient_id = ? ORDER BY id", String.class, recipientId);
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
