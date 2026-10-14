package com.campus.lostfound.stats;

import com.campus.lostfound.entity.UserRole;
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
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 工单 11：统计看板与 CSV 导出的集成测试。
 * 沿用既有接缝：真实 HTTP + JdbcTemplate 夹具，只断言「返回什么」。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class StatsApiTest {

    @Autowired
    private TestRestTemplate restTemplate;

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
        adminToken = jwtService.generate(adminId, UserRole.ADMIN);
        userToken = jwtService.generate(publisherId, UserRole.USER);
    }

    @Test
    void 周统计基于真实数据聚合每日归还率与分布() {
        LocalDate today = LocalDate.now();
        insertReport("图书馆捡到的校园卡", "图书馆", "校园卡", "OPEN",
                today.atTime(9, 0), LocalDateTime.now());
        insertReport("食堂捡到的门禁卡", "食堂", "门禁卡", "OPEN",
                today.atTime(12, 0), LocalDateTime.now());
        insertReport("教学楼捡到的钱包", "教学楼", "钱包", "RETURNED",
                today.atTime(18, 0), LocalDateTime.now());

        JsonNode data = get("/api/admin/stats?range=week", adminToken).body().path("data");

        assertThat(data.path("range").asText()).isEqualTo("week");
        assertThat(data.path("totalReports").asLong()).isEqualTo(3);
        assertThat(data.path("returnedReports").asLong()).isEqualTo(1);
        assertThat(data.path("returnRate").asDouble()).isEqualTo(33.3);

        // 每日序列覆盖窗口内 7 天，今天有 3 条新增、1 条归还。
        JsonNode daily = data.path("daily");
        assertThat(daily.size()).isEqualTo(7);
        JsonNode todayStat = daily.get(daily.size() - 1);
        assertThat(todayStat.path("date").asText()).isEqualTo(today.toString());
        assertThat(todayStat.path("created").asLong()).isEqualTo(3);
        assertThat(todayStat.path("returned").asLong()).isEqualTo(1);

        // 按地点 / 分类分布：各 3 项，计数均为 1。
        assertThat(data.path("byLocation").size()).isEqualTo(3);
        assertThat(countOf(data.path("byLocation"), "图书馆")).isEqualTo(1);
        assertThat(countOf(data.path("byLocation"), "食堂")).isEqualTo(1);
        assertThat(countOf(data.path("byLocation"), "教学楼")).isEqualTo(1);
        assertThat(data.path("byCategory").size()).isEqualTo(3);
        assertThat(countOf(data.path("byCategory"), "校园卡")).isEqualTo(1);
        assertThat(countOf(data.path("byCategory"), "钱包")).isEqualTo(1);

        // 高发时段：0~23 全量，对应小时计数为 1。
        JsonNode byHour = data.path("byHour");
        assertThat(byHour.size()).isEqualTo(24);
        assertThat(countOf(byHour, "09:00")).isEqualTo(1);
        assertThat(countOf(byHour, "12:00")).isEqualTo(1);
        assertThat(countOf(byHour, "18:00")).isEqualTo(1);
        assertThat(countOf(byHour, "00:00")).isZero();
    }

    @Test
    void 月统计可访问且结构一致() {
        insertReport("图书馆捡到的校园卡", "图书馆", "校园卡", "OPEN",
                LocalDate.now().atTime(9, 0), LocalDateTime.now());

        JsonNode data = get("/api/admin/stats?range=month", adminToken).body().path("data");

        assertThat(data.path("range").asText()).isEqualTo("month");
        assertThat(data.path("totalReports").asLong()).isEqualTo(1);
        assertThat(data.path("daily").size()).isEqualTo(30);
        assertThat(data.path("byHour").size()).isEqualTo(24);
    }

    @Test
    void 非法统计窗口返回非法参数() {
        HttpResult result = get("/api/admin/stats?range=xxx", adminToken);

        assertThat(result.status()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(result.body().path("code").asInt()).isEqualTo(1001);
    }

    @Test
    void 窗口外的启事不计入周统计() {
        insertReport("本周的启事", "图书馆", "校园卡", "OPEN",
                LocalDate.now().atTime(9, 0), LocalDateTime.now());
        insertReport("一个月前的启事", "食堂", "门禁卡", "OPEN",
                LocalDateTime.now().minusDays(30), LocalDateTime.now().minusDays(30));

        JsonNode data = get("/api/admin/stats?range=week", adminToken).body().path("data");

        assertThat(data.path("totalReports").asLong()).isEqualTo(1);
    }

    @Test
    void 普通用户访问统计接口返回越权() {
        HttpResult result = get("/api/admin/stats", userToken);

        assertThat(result.status()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(result.body().path("code").asInt()).isEqualTo(1004);
    }

    @Test
    void 访客访问统计接口返回未授权() {
        HttpResult result = get("/api/admin/stats", null);

        assertThat(result.status()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(result.body().path("code").asInt()).isEqualTo(1003);
    }

    @Test
    void 导出CSV含中文表头与启事数据行() {
        insertReport("图书馆捡到的校园卡", "图书馆", "校园卡", "OPEN",
                LocalDate.now().atTime(9, 0), LocalDateTime.now());

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/admin/export.csv", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().toString()).contains("text/csv");

        String body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body).contains("标题");
        assertThat(body).contains("状态");
        assertThat(body).contains("类型");
        assertThat(body).contains("图书馆捡到的校园卡");
        assertThat(body).contains("招领");
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

    private JsonNode readTree(String raw) {
        try {
            return raw == null ? objectMapper.createObjectNode() : objectMapper.readTree(raw);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** 在名称计数数组中查找指定 name 的 count，找不到返回 -1。 */
    private static long countOf(JsonNode array, String name) {
        for (JsonNode item : array) {
            if (name.equals(item.path("name").asText())) {
                return item.path("count").asLong();
            }
        }
        return -1;
    }

    private long insertReport(String title, String location, String category, String status,
                              LocalDateTime eventTime, LocalDateTime createdAt) {
        Timestamp eventTs = eventTime == null ? null : Timestamp.valueOf(eventTime);
        Timestamp createdTs = Timestamp.valueOf(createdAt);
        jdbcTemplate.update("""
                INSERT INTO report (type, title, category, location, event_time, status, publisher_id,
                                    view_count, pinned, created_at, updated_at)
                VALUES ('FOUND', ?, ?, ?, ?, ?, ?, 0, 0, ?, ?)
                """, title, category, location, eventTs, status, publisherId, createdTs, createdTs);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM report WHERE title = ? ORDER BY id DESC LIMIT 1", Long.class, title);
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
