package com.campus.lostfound.auth;

import com.campus.lostfound.common.BusinessException;
import io.jsonwebtoken.JwtException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 账号相关的业务逻辑：注册、登录、当前用户、退出登录。
 * 明文密码只在此处短暂存在，落库前一律 BCrypt 哈希。
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenBlacklist tokenBlacklist;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       TokenBlacklist tokenBlacklist) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.tokenBlacklist = tokenBlacklist;
    }

    @Transactional
    public UserView register(RegisterRequest request) {
        String studentNo = request.studentNo().trim();
        if (userRepository.existsByStudentNo(studentNo)) {
            throw BusinessException.conflict("该学号已注册");
        }

        User user = new User();
        user.setStudentNo(studentNo);
        user.setNickname(request.nickname().trim());
        user.setDepartment(trimToNull(request.department()));
        user.setEmail(trimToNull(request.email()));
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setContactPublic(false);

        return UserView.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByStudentNo(request.studentNo().trim())
                .orElseThrow(() -> BusinessException.unauthorized("学号或密码不正确"));

        if (user.getStatus() == UserStatus.DISABLED) {
            throw BusinessException.forbidden("账号已被禁用，请联系管理员");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw BusinessException.unauthorized("学号或密码不正确");
        }

        String token = jwtService.generate(user.getId(), user.getRole());
        return new AuthResponse(token, UserView.from(user));
    }

    @Transactional(readOnly = true)
    public UserView currentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("用户不存在"));
        return UserView.from(user);
    }

    /**
     * 退出登录：把当前 token 记入吊销集合，使其在过期前不再可用。
     */
    public void logout(String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);
        if (token == null) {
            return;
        }
        try {
            tokenBlacklist.revoke(token, jwtService.parse(token).expiresAt());
        } catch (JwtException | IllegalArgumentException ex) {
            // token 已非法或过期，无需登记。
        }
    }

    private static String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        return token.isEmpty() ? null : token;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
