package com.campusalimi;

import static org.assertj.core.api.Assertions.assertThat;

import com.campusalimi.domain.DateItem;
import com.campusalimi.domain.DateItemRepository;
import com.campusalimi.domain.Notice;
import com.campusalimi.domain.NoticeRepository;
import com.campusalimi.fetch.PageFetcher;
import com.campusalimi.notice.NoticeCollector;
import com.campusalimi.notice.NoticeSummary;
import com.campusalimi.schedule.ScheduleCollector;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = { "app.scheduling.enabled=false", "app.fetch-interval-ms=0", "app.schedule.years=2026" })
class CollectorsIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    /** 실제 학교 서버 대신 저장해 둔 페이지를 돌려준다. 목록은 본문 픽스처가 있는 글만 남긴다. */
    @TestConfiguration
    static class FakeFetcherConfig {
        static final Set<String> KNOWN = Set.of("273306", "274128", "276772", "276997");

        @Bean
        @Primary
        PageFetcher fakeFetcher() {
            return url -> {
                if (url.contains("calendar")) {
                    return Fixtures.read("calendar-2026.html");
                }
                if (url.contains("mode=view")) {
                    String no = url.replaceAll(".*articleNo=(\\d+).*", "$1");
                    if (!KNOWN.contains(no)) {
                        throw new IllegalStateException("unexpected fetch " + url);
                    }
                    return Fixtures.read("notice-" + no + ".html");
                }
                String fixture = url.contains("srCategoryId=22") ? "notice-list-scholarship.html" : "notice-list-academic.html";
                Document doc = Jsoup.parse(Fixtures.read(fixture));
                doc.select("tbody > tr").forEach(tr -> {
                    var a = tr.selectFirst("a.b-title[data-article-no]");
                    if (a == null || !KNOWN.contains(a.attr("data-article-no"))) {
                        tr.remove();
                    }
                });
                return doc.outerHtml();
            };
        }
    }

    @Autowired
    NoticeCollector noticeCollector;
    @Autowired
    ScheduleCollector scheduleCollector;
    @Autowired
    NoticeRepository notices;
    @Autowired
    DateItemRepository dateItems;

    @Test
    void noticesAreStoredOnceWithTheirDateItems() {
        // 목록 첫 페이지 픽스처에는 최근 글 둘(276997 학사, 276772 장학)만 있다. 오래된 둘은 본문을 직접 넣는다.
        NoticeCollector.Result first = noticeCollector.collectAll();
        assertThat(first.fetched()).isEqualTo(2);
        noticeCollector.save(new NoticeSummary(273306L, "학사", "", LocalDate.of(2026, 6, 29)), Fixtures.read("notice-273306.html"));
        noticeCollector.save(new NoticeSummary(274128L, "학사", "", LocalDate.of(2026, 7, 27)), Fixtures.read("notice-274128.html"));

        Notice registration = notices.findByArticleNo(273306L).orElseThrow();
        assertThat(registration.getReviewReason()).isEqualTo(Notice.REASON_PARSED);
        assertThat(registration.getStatus()).isEqualTo(Notice.STATUS_NEEDS_REVIEW);
        List<DateItem> items = dateItems.findByNoticeIdOrderByEndsOnAsc(registration.getId());
        assertThat(items).hasSize(8);
        assertThat(items).allSatisfy(i -> {
            assertThat(i.getSourceKind()).isEqualTo(DateItem.SOURCE_NOTICE);
            assertThat(i.getStatus()).isEqualTo(DateItem.STATUS_AUTO);
        });

        Notice priority = notices.findByArticleNo(274128L).orElseThrow();
        assertThat(priority.getReviewReason()).isEqualTo(Notice.REASON_IMAGE_ONLY);
        assertThat(dateItems.findByNoticeIdOrderByEndsOnAsc(priority.getId())).isEmpty();

        NoticeCollector.Result second = noticeCollector.collectAll();
        assertThat(second.fetched()).isZero();
        assertThat(notices.count()).isEqualTo(4);
        assertThat(dateItems.countBySourceKind(DateItem.SOURCE_NOTICE)).isEqualTo(
                dateItems.findAll().stream().filter(i -> i.getSourceKind().equals(DateItem.SOURCE_NOTICE)).count());
    }

    @Autowired
    com.campusalimi.benefit.BenefitSeeder benefitSeeder;
    @Autowired
    com.campusalimi.domain.BenefitRepository benefitRepository;

    @Test
    void benefitsAreSeededFromFileIdempotently() {
        // 앱이 뜰 때 한 번 들어갔다. 다시 돌려도 바뀌는 것이 없어야 한다.
        long count = benefitRepository.count();
        assertThat(count).isGreaterThanOrEqualTo(15);
        var again = benefitSeeder.seed(com.campusalimi.benefit.BenefitSeedFile.load());
        assertThat(again.inserted()).isZero();
        assertThat(again.updated()).isZero();
        assertThat(benefitRepository.count()).isEqualTo(count);
        assertThat(benefitRepository.findBySlug("spotify-student")).isPresent()
                .get().satisfies(b -> assertThat(b.isStale(java.time.LocalDate.of(2026, 10, 26))).isFalse());
    }

    @Test
    void scheduleIsIdempotent() {
        ScheduleCollector.Result first = scheduleCollector.collect(2026);
        assertThat(first.kept()).isGreaterThan(30);
        long stored = dateItems.countBySourceKind(DateItem.SOURCE_SCHEDULE);
        assertThat(stored).isEqualTo(first.kept());

        ScheduleCollector.Result second = scheduleCollector.collect(2026);
        assertThat(second.inserted()).isZero();
        assertThat(second.updated()).isZero();
        assertThat(dateItems.countBySourceKind(DateItem.SOURCE_SCHEDULE)).isEqualTo(stored);
    }
}
