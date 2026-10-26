package com.campusalimi;

import static org.assertj.core.api.Assertions.assertThat;

import com.campusalimi.auth.ConsoleMailSender;
import com.campusalimi.domain.DateItem;
import com.campusalimi.domain.DateItemRepository;
import com.campusalimi.domain.Notice;
import com.campusalimi.domain.NoticeRepository;
import com.campusalimi.fetch.PageFetcher;
import com.campusalimi.notice.NoticeStore;
import com.campusalimi.notice.NoticeSummary;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 로그인부터 검수까지 실제 HTTP로 한 바퀴. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestPropertySource(properties = { "app.scheduling.enabled=false", "app.fetch-interval-ms=0",
        "app.admin-email=admin@catholic.ac.kr", "app.public-url=http://localhost:3000", "app.mail.mode=console" })
class ApiFlowTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @TestConfiguration
    static class NoNetwork {
        @Bean
        @Primary
        PageFetcher fetcher() {
            return url -> {
                throw new IllegalStateException("no network in this test");
            };
        }
    }

    @Autowired
    TestRestTemplate rest;
    @Autowired
    ConsoleMailSender mail;
    @Autowired
    NoticeStore store;
    @Autowired
    NoticeRepository notices;
    @Autowired
    DateItemRepository dateItems;

    @BeforeEach
    void seedNotices() {
        // consume는 화면 주소로 302를 보낸다. 테스트 클라이언트가 따라가면 안 된다.
        rest.getRestTemplate().setRequestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory(
                java.net.http.HttpClient.newBuilder().followRedirects(java.net.http.HttpClient.Redirect.NEVER).build()));
        if (notices.count() == 0) {
            store.save(new NoticeSummary(273306L, "학사", "", LocalDate.of(2026, 6, 29)), Fixtures.read("notice-273306.html"));
            store.save(new NoticeSummary(274128L, "학사", "", LocalDate.of(2026, 7, 27)), Fixtures.read("notice-274128.html"));
        }
    }

    @Test
    void anyoneCanReadItemsAndBenefits() {
        ResponseEntity<List> items = rest.getForEntity("/api/items?includePast=true", List.class);
        assertThat(items.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(items.getBody()).hasSizeGreaterThanOrEqualTo(8);

        ResponseEntity<List> benefits = rest.getForEntity("/api/benefits", List.class);
        assertThat(benefits.getBody()).hasSizeGreaterThanOrEqualTo(15);

        assertThat(rest.getForEntity("/api/me", String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(rest.getForEntity("/api/admin/review", String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void magicLinkLogsInOnceAndSessionWorks() {
        String cookie = login("student@catholic.ac.kr");

        ResponseEntity<Map> me = rest.exchange("/api/me", HttpMethod.GET, withCookie(cookie, null), Map.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().get("email")).isEqualTo("student@catholic.ac.kr");
        assertThat(me.getBody().get("admin")).isEqualTo(false);

        // 같은 링크를 또 쓰면 안 된다.
        String link = lastLink();
        ResponseEntity<Void> again = rest.getForEntity(URI.create(link), Void.class);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // 프로필을 넣으면 학년이 계산된다.
        ResponseEntity<Map> profile = rest.exchange("/api/me/profile", HttpMethod.PUT,
                withCookie(cookie, Map.of("entryYear", 2023, "major", "컴퓨터정보공학부")), Map.class);
        assertThat(profile.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(profile.getBody().get("grade")).isNotNull();

        // 항목 저장과 혜택 체크
        long itemId = ((Number) ((Map) rest.getForObject("/api/items?includePast=true", List.class).get(0)).get("id")).longValue();
        assertThat(rest.exchange("/api/me/saved/" + itemId, HttpMethod.POST, withCookie(cookie, null), Void.class)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        List<Map> items = rest.exchange("/api/items?includePast=true", HttpMethod.GET, withCookie(cookie, null), List.class).getBody();
        assertThat(items.stream().filter(i -> ((Number) i.get("id")).longValue() == itemId).findFirst().orElseThrow().get("saved"))
                .isEqualTo(true);

        // 관리자 아님
        assertThat(rest.exchange("/api/admin/review", HttpMethod.GET, withCookie(cookie, null), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // 로그아웃 뒤에는 세션이 죽는다.
        rest.exchange("/api/auth/logout", HttpMethod.POST, withCookie(cookie, null), Void.class);
        assertThat(rest.exchange("/api/me", HttpMethod.GET, withCookie(cookie, null), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void adminReviewsAndAddsManualItem() {
        String cookie = login("admin@catholic.ac.kr");

        List<Map> queue = rest.exchange("/api/admin/review", HttpMethod.GET, withCookie(cookie, null), List.class).getBody();
        assertThat(queue).hasSizeGreaterThanOrEqualTo(2);
        Map imageOnly = queue.stream().filter(n -> ((Number) n.get("articleNo")).longValue() == 274128L).findFirst().orElseThrow();
        assertThat(imageOnly.get("reviewReason")).isEqualTo(Notice.REASON_IMAGE_ONLY);
        long noticeId = ((Number) imageOnly.get("id")).longValue();

        // 그림만 있는 공지에 날짜를 손으로 넣는다.
        ResponseEntity<Map> added = rest.exchange("/api/admin/notices/" + noticeId + "/items", HttpMethod.POST,
                withCookie(cookie, Map.of("title", "제1전공생 우선 수강신청", "startsOn", "2026-07-30", "endsOn", "2026-07-31",
                        "endsAt", "17:00", "targetGrade", "ALL", "targetDept", "ALL")), Map.class);
        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(added.getBody().get("sourceKind")).isEqualTo(DateItem.SOURCE_MANUAL);
        assertThat(added.getBody().get("reviewed")).isEqualTo(true);

        // 마감 없는 항목은 거부
        ResponseEntity<String> bad = rest.exchange("/api/admin/notices/" + noticeId + "/items", HttpMethod.POST,
                withCookie(cookie, Map.of("title", "x")), String.class);
        assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // 공지를 검수 완료로
        ResponseEntity<Map> done = rest.exchange("/api/admin/notices/" + noticeId + "/reviewed", HttpMethod.POST,
                withCookie(cookie, null), Map.class);
        assertThat(done.getBody().get("status")).isEqualTo(Notice.STATUS_REVIEWED);
        assertThat(notices.findById(noticeId).orElseThrow().getStatus()).isEqualTo(Notice.STATUS_REVIEWED);

        // 수강신청 공지의 자동 항목 하나를 고치고 태그를 붙인다.
        Map registration = queue.stream().filter(n -> ((Number) n.get("articleNo")).longValue() == 273306L).findFirst().orElseThrow();
        Map firstItem = (Map) ((List) registration.get("items")).get(0);
        long itemId = ((Number) firstItem.get("id")).longValue();
        ResponseEntity<Map> edited = rest.exchange("/api/admin/items/" + itemId, HttpMethod.PUT,
                withCookie(cookie, Map.of("title", "수강바구니 (재학생)", "startsOn", "2026-07-07", "endsOn", "2026-07-12",
                        "targetGrade", "ALL", "targetDept", "ALL")), Map.class);
        assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(edited.getBody().get("reviewed")).isEqualTo(true);
        assertThat(edited.getBody().get("bucket")).isEqualTo(1);
        DateItem stored = dateItems.findById(itemId).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(DateItem.STATUS_REVIEWED);
        assertThat(stored.getTitle()).isEqualTo("수강바구니 (재학생)");
    }

    private String login(String email) {
        ResponseEntity<Map> requested = rest.postForEntity("/api/auth/request", Map.of("email", email), Map.class);
        assertThat(requested.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mail.lastTo()).isEqualTo(email);
        ResponseEntity<Void> consumed = rest.getForEntity(URI.create(lastLink()), Void.class);
        assertThat(consumed.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        String setCookie = consumed.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).startsWith("ca_session=").contains("HttpOnly");
        return setCookie.split(";")[0];
    }

    /** 링크는 public-url로 만들어지니 테스트 서버 주소로 바꿔 부른다. */
    private String lastLink() {
        Matcher m = Pattern.compile("http://localhost:3000(/api/auth/consume\\?token=[A-Za-z0-9_-]+)").matcher(mail.lastBody());
        // api-url을 비워 두면 public-url로 링크가 만들어진다. 테스트 서버 주소로 바꿔 부른다.
        assertThat(m.find()).isTrue();
        return rest.getRootUri() + m.group(1);
    }

    private static HttpEntity<Object> withCookie(String cookie, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, cookie);
        if (body != null) {
            headers.add(HttpHeaders.CONTENT_TYPE, "application/json");
        }
        return new HttpEntity<>(body, headers);
    }
}
