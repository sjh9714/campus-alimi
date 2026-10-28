package com.campusalimi.auth;

import com.campusalimi.config.AppProperties;
import com.campusalimi.domain.AppUser;
import com.campusalimi.domain.AppUserRepository;
import com.campusalimi.domain.LoginToken;
import com.campusalimi.domain.LoginTokenRepository;
import com.campusalimi.domain.SavedItemRepository;
import com.campusalimi.domain.SentNotificationRepository;
import com.campusalimi.domain.UserBenefitRepository;
import com.campusalimi.domain.UserSession;
import com.campusalimi.domain.UserSessionRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이메일 매직링크 로그인 (ADR 005). 비밀번호가 없다.
 * 링크는 15분, 한 번만. 세션은 30일. 둘 다 해시만 저장한다.
 */
@Service
public class AuthService {

    public static final String COOKIE = "ca_session";
    static final Duration LINK_TTL = Duration.ofMinutes(15);
    static final Duration SESSION_TTL = Duration.ofDays(30);
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final AppProperties props;
    private final MailSender mail;
    private final AppUserRepository users;
    private final LoginTokenRepository tokens;
    private final UserSessionRepository sessions;
    private final SavedItemRepository savedItems;
    private final UserBenefitRepository userBenefits;
    private final SentNotificationRepository sentNotifications;

    public AuthService(AppProperties props, MailSender mail, AppUserRepository users, LoginTokenRepository tokens,
            UserSessionRepository sessions, SavedItemRepository savedItems, UserBenefitRepository userBenefits,
            SentNotificationRepository sentNotifications) {
        this.savedItems = savedItems;
        this.userBenefits = userBenefits;
        this.sentNotifications = sentNotifications;
        this.props = props;
        this.mail = mail;
        this.users = users;
        this.tokens = tokens;
        this.sessions = sessions;
    }

    /** 링크를 보낸다. 가입 여부와 상관없이 같은 응답을 준다. 주소가 있는지 밖에서 알 수 없게. */
    @Transactional
    public void requestLogin(String rawEmail) {
        String email = normalize(rawEmail);
        if (!EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("이메일 형식이 아닙니다");
        }
        String token = Tokens.random();
        tokens.save(new LoginToken(email, Tokens.hash(token), OffsetDateTime.now().plus(LINK_TTL)));
        String link = props.apiUrlOrPublic() + "/api/auth/consume?token=" + token;
        mail.send(email, "[캠퍼스 알리미] 로그인 링크",
                "아래 링크를 15분 안에 누르면 로그인됩니다. 한 번만 쓸 수 있습니다.\n\n" + link
                        + "\n\n본인이 요청하지 않았다면 이 메일은 무시해도 됩니다.");
    }

    /** 링크를 쓰고 세션 토큰(쿠키 값)을 돌려준다. */
    @Transactional
    public String consume(String token) {
        if (token == null || token.isBlank()) {
            throw AuthException.unauthorized();
        }
        OffsetDateTime now = OffsetDateTime.now();
        LoginToken login = tokens.findByTokenHash(Tokens.hash(token))
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> new AuthException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                        "링크가 만료됐거나 이미 사용됐습니다"));
        login.markUsed();
        AppUser user = users.findByEmail(login.getEmail()).orElseGet(() -> users.save(new AppUser(login.getEmail())));
        user.touchLogin();
        String session = Tokens.random();
        sessions.save(new UserSession(user.getId(), Tokens.hash(session), now.plus(SESSION_TTL)));
        return session;
    }

    /** 탈퇴. 사용자를 가리키는 것부터 지우고 마지막에 사용자를 지운다. */
    @Transactional
    public void deleteAccount(AppUser user) {
        sessions.deleteByUserId(user.getId());
        savedItems.deleteByUserId(user.getId());
        userBenefits.deleteByUserId(user.getId());
        sentNotifications.deleteByUserId(user.getId());
        tokens.deleteByEmail(user.getEmail());
        users.deleteById(user.getId());
    }

    @Transactional
    public void logout(HttpServletRequest request) {
        sessionToken(request).ifPresent(t -> sessions.deleteByTokenHash(Tokens.hash(t)));
    }

    @Transactional(readOnly = true)
    public Optional<AppUser> currentUser(HttpServletRequest request) {
        OffsetDateTime now = OffsetDateTime.now();
        return sessionToken(request)
                .flatMap(t -> sessions.findByTokenHash(Tokens.hash(t)))
                .filter(s -> s.isAlive(now))
                .flatMap(s -> users.findById(s.getUserId()));
    }

    public AppUser requireUser(HttpServletRequest request) {
        return currentUser(request).orElseThrow(AuthException::unauthorized);
    }

    public AppUser requireAdmin(HttpServletRequest request) {
        AppUser user = requireUser(request);
        if (!props.isAdmin(user.getEmail())) {
            throw AuthException.forbidden();
        }
        return user;
    }

    public boolean isAdmin(AppUser user) {
        return props.isAdmin(user.getEmail());
    }

    public Cookie sessionCookie(String token, boolean clear) {
        Cookie cookie = new Cookie(COOKIE, clear ? "" : token);
        cookie.setHttpOnly(true);
        cookie.setSecure(props.cookieSecure());
        cookie.setPath("/");
        cookie.setMaxAge(clear ? 0 : (int) SESSION_TTL.toSeconds());
        cookie.setAttribute("SameSite", "Lax");
        return cookie;
    }

    private static Optional<String> sessionToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        for (Cookie c : request.getCookies()) {
            if (COOKIE.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                return Optional.of(c.getValue());
            }
        }
        return Optional.empty();
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
