package com.campus.lostfound.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 账号接口的集成测试：对真实运行的容器发 HTTP 请求，断言外部可观察行为。
 * 只断言「发请求后返回什么、库里最终留下什么」。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthApiTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

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
    }

    @Test
    void 注册后可用学号密码登录并查询当前用户() throws Exception {
        HttpResult register = post("/api/auth/register", """
                {"studentNo":"2021001","nickname":"张三","password":"secret123","department":"计算机学院"}
                """, null);
        assertThat(register.status()).isEqualTo(200);
        assertThat(register.body().path("code").asInt()).isZero();
        assertThat(register.body().path("data").path("studentNo").asText()).isEqualTo("2021001");
        assertThat(register.body().path("data").path("role").asText()).isEqualTo("USER");
        assertThat(register.body().path("data").path("status").asText()).isEqualTo("ACTIVE");

        // 密码以 BCrypt 哈希存储，库中不存在明文。
        String hash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM app_user WHERE student_no = ?", String.class, "2021001");
        assertThat(hash).startsWith("$2").isNotEqualTo("secret123");

        HttpResult login = post("/api/auth/login", """
                {"studentNo":"2021001","password":"secret123"}
                """, null);
        assertThat(login.status()).isEqualTo(200);
        assertThat(login.body().path("code").asInt()).isZero();
        assertThat(login.body().path("data").path("user").path("nickname").asText()).isEqualTo("张三");
        String token = login.body().path("data").path("token").asText();
        assertThat(token).isNotBlank();

        HttpResult me = get("/api/auth/me", token);
        assertThat(me.body().path("code").asInt()).isZero();
        assertThat(me.body().path("data").path("studentNo").asText()).isEqualTo("2021001");
        assertThat(me.body().path("data").path("department").asText()).isEqualTo("计算机学院");
    }

    @Test
    void 重复学号注册返回冲突错误码() throws Exception {
        String payload = """
                {"studentNo":"2021002","nickname":"李四","password":"secret123"}
                """;
        assertThat(post("/api/auth/register", payload, null).body().path("code").asInt()).isZero();

        HttpResult second = post("/api/auth/register", payload, null);
        assertThat(second.status()).isEqualTo(409);
        assertThat(second.body().path("code").asInt()).isEqualTo(1005);
    }

    @Test
    void 错误密码登录返回未授权错误码() throws Exception {
        post("/api/auth/register", """
                {"studentNo":"2021003","nickname":"王五","password":"secret123"}
                """, null);

        HttpResult login = post("/api/auth/login", """
                {"studentNo":"2021003","password":"wrong-pass"}
                """, null);
        assertThat(login.status()).isEqualTo(401);
        assertThat(login.body().path("code").asInt()).isEqualTo(1003);
    }

    @Test
    void 无token访问受保护接口返回未授权() throws Exception {
        HttpResult me = get("/api/auth/me", null);
        assertThat(me.status()).isEqualTo(401);
        assertThat(me.body().path("code").asInt()).isEqualTo(1003);
    }

    @Test
    void 被禁用用户登录被拒绝() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO app_user (student_no, nickname, password_hash, role, status, contact_public, created_at, updated_at)
                VALUES (?, ?, ?, 'USER', 'DISABLED', 0, NOW(6), NOW(6))
                """, "2021004", "被禁用用户", passwordEncoder.encode("secret123"));

        HttpResult login = post("/api/auth/login", """
                {"studentNo":"2021004","password":"secret123"}
                """, null);
        assertThat(login.status()).isEqualTo(403);
        assertThat(login.body().path("code").asInt()).isEqualTo(1004);
    }

    @Test
    void 退出登录后token失效() throws Exception {
        post("/api/auth/register", """
                {"studentNo":"2021005","nickname":"赵六","password":"secret123"}
                """, null);
        String token = post("/api/auth/login", """
                {"studentNo":"2021005","password":"secret123"}
                """, null).body().path("data").path("token").asText();

        assertThat(get("/api/auth/me", token).body().path("code").asInt()).isZero();

        post("/api/auth/logout", null, token);

        HttpResult afterLogout = get("/api/auth/me", token);
        assertThat(afterLogout.status()).isEqualTo(401);
        assertThat(afterLogout.body().path("code").asInt()).isEqualTo(1003);
    }

    private HttpResult post(String path, String json, String token) throws Exception {
        HttpURLConnection connection = open(path, "POST", token);
        if (json != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            try (OutputStream out = connection.getOutputStream()) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
            }
        }
        return read(connection);
    }

    private HttpResult get(String path, String token) throws Exception {
        return read(open(path, "GET", token));
    }

    private HttpURLConnection open(String path, String method, String token) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://localhost:" + port + path).openConnection();
        connection.setRequestMethod(method);
        if (token != null) {
            connection.setRequestProperty("Authorization", "Bearer " + token);
        }
        return connection;
    }

    private HttpResult read(HttpURLConnection connection) throws Exception {
        int status = connection.getResponseCode();
        InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String raw = stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        connection.disconnect();
        return new HttpResult(status, raw.isEmpty() ? objectMapper.createObjectNode() : objectMapper.readTree(raw));
    }

    private record HttpResult(int status, JsonNode body) {
    }
}
