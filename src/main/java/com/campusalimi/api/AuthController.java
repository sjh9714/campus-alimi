package com.campusalimi.api;

import com.campusalimi.auth.AuthService;
import com.campusalimi.config.AppProperties;
import com.campusalimi.domain.AppUser;
import com.campusalimi.domain.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService auth;
    private final AppProperties props;
    private final AppUserRepository users;

    public AuthController(AuthService auth, AppProperties props, AppUserRepository users) {
        this.auth = auth;
        this.props = props;
        this.users = users;
    }

    public record LoginRequest(String email) {
    }

    public record MeView(String email, Integer entryYear, String major, String secondMajor, Integer grade, boolean admin) {
        static MeView of(AppUser u, boolean admin) {
            return new MeView(u.getEmail(), u.getEntryYear(), u.getMajor(), u.getSecondMajor(), u.grade(LocalDate.now()), admin);
        }
    }

    public record ProfileRequest(Integer entryYear, String major, String secondMajor) {
    }

    @PostMapping("/auth/request")
    public Map<String, String> request(@RequestBody LoginRequest body) {
        auth.requestLogin(body.email());
        return Map.of("message", "메일을 보냈습니다. 15분 안에 링크를 눌러 주세요.");
    }

    /** 메일의 링크. 쿠키를 심고 화면으로 보낸다. */
    @GetMapping("/auth/consume")
    public ResponseEntity<Void> consume(@RequestParam String token, HttpServletResponse response) {
        String session = auth.consume(token);
        response.addCookie(auth.sessionCookie(session, false));
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(props.publicUrl() + "/?login=ok")).build();
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        auth.logout(request);
        response.addCookie(auth.sessionCookie("", true));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public MeView me(HttpServletRequest request) {
        AppUser user = auth.requireUser(request);
        return MeView.of(user, auth.isAdmin(user));
    }

    @PutMapping("/me/profile")
    @Transactional
    public MeView profile(HttpServletRequest request, @RequestBody ProfileRequest body) {
        AppUser user = auth.requireUser(request);
        if (body.entryYear() != null && (body.entryYear() < 1990 || body.entryYear() > LocalDate.now().getYear())) {
            throw new IllegalArgumentException("입학년도가 이상합니다");
        }
        AppUser managed = users.findById(user.getId()).orElseThrow();
        managed.updateProfile(body.entryYear(), body.major(), body.secondMajor());
        return MeView.of(managed, auth.isAdmin(managed));
    }
}
