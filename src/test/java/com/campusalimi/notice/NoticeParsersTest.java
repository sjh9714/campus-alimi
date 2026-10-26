package com.campusalimi.notice;

import static org.assertj.core.api.Assertions.assertThat;

import com.campusalimi.Fixtures;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoticeParsersTest {

    @Test
    void listParserDedupesPinnedRows() {
        List<NoticeSummary> rows = NoticeListParser.parse(Fixtures.read("notice-list-scholarship.html"));
        assertThat(rows).isNotEmpty();
        assertThat(rows.stream().map(NoticeSummary::articleNo).distinct().count()).isEqualTo(rows.size());
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.category()).isEqualTo("장학");
            assertThat(r.title()).isNotBlank();
            assertThat(r.postedOn()).isAfter(LocalDate.of(2025, 1, 1));
        });
        assertThat(rows).anyMatch(r -> r.articleNo() == 276772L);
    }

    @Test
    void listParserReadsAcademicCategory() {
        List<NoticeSummary> rows = NoticeListParser.parse(Fixtures.read("notice-list-academic.html"));
        assertThat(rows).allSatisfy(r -> assertThat(r.category()).isEqualTo("학사"));
        assertThat(rows).anyMatch(r -> r.articleNo() == 276997L);
    }

    @Test
    void viewParserReadsHeaderAndBody() {
        NoticeView v = NoticeViewParser.parse(Fixtures.read("notice-273306.html"));
        assertThat(v.articleNo()).isEqualTo(273306L);
        assertThat(v.category()).isEqualTo("학사");
        assertThat(v.title()).contains("2026학년도 2학기 수강신청 일정 안내");
        assertThat(v.postedOn()).isEqualTo(LocalDate.of(2026, 6, 29));
        assertThat(v.writer()).isEqualTo("학사지원팀");
        assertThat(v.bodyText()).contains("수강바구니");
        assertThat(v.attachments()).anyMatch(a -> a.contains("예정학년"));
        assertThat(v.cleanTitle()).isEqualTo("2026학년도 2학기 수강신청 일정 안내");
    }

    @Test
    void courseRegistrationTableBecomesEightItems() {
        NoticeView v = NoticeViewParser.parse(Fixtures.read("notice-273306.html"));
        List<ExtractedItem> items = DateItemExtractor.extract(v);
        items.forEach(i -> System.out.println("  " + i));
        // 수강바구니 2행, 학년별 본 수강신청 4행, 변경기간 2행
        assertThat(items).hasSize(8);

        ExtractedItem basket = items.get(0);
        assertThat(basket.title()).contains("수강바구니").contains("재학생");
        assertThat(basket.range().start()).isEqualTo(LocalDate.of(2026, 7, 7));
        assertThat(basket.range().end()).isEqualTo(LocalDate.of(2026, 7, 12));
        assertThat(basket.range().startsAt()).isEqualTo(LocalTime.of(9, 0));
        assertThat(basket.range().endsAt()).isEqualTo(LocalTime.of(23, 59));
        assertThat(basket.targetGrade()).isEqualTo("ALL");

        ExtractedItem fourth = items.get(2);
        assertThat(fourth.title()).contains("본 수강신청").contains("4");
        assertThat(fourth.range().start()).isEqualTo(LocalDate.of(2026, 8, 4));
        assertThat(fourth.range().startsAt()).isEqualTo(LocalTime.of(9, 0));
        assertThat(fourth.range().endsAt()).isEqualTo(LocalTime.of(15, 0));
        assertThat(fourth.targetGrade()).isEqualTo("4");

        ExtractedItem second = items.get(7);
        assertThat(second.title()).contains("변경기간");
        assertThat(second.range().start()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(second.range().end()).isEqualTo(LocalDate.of(2026, 9, 4));
        assertThat(second.range().endsAt()).isEqualTo(LocalTime.of(17, 0));
        assertThat(second.targetGrade()).isEqualTo("ALL");
    }

    @Test
    void imageOnlyNoticeYieldsNothing() {
        NoticeView v = NoticeViewParser.parse(Fixtures.read("notice-274128.html"));
        assertThat(v.hasImages()).isTrue();
        assertThat(v.bodyText().length()).isLessThan(80);
        assertThat(v.attachments()).anyMatch(a -> a.contains("우선 수강신청 과목 목록"));
        assertThat(DateItemExtractor.extract(v)).isEmpty();
    }

    @Test
    void scholarshipTitleCarriesTheDeadline() {
        NoticeView v = NoticeViewParser.parse(Fixtures.read("notice-276772.html"));
        List<ExtractedItem> items = DateItemExtractor.extract(v);
        items.forEach(i -> System.out.println("  " + i));
        assertThat(items).isNotEmpty();
        ExtractedItem first = items.get(0);
        assertThat(first.where()).isEqualTo("title");
        assertThat(first.title()).startsWith("2027년도 두산연강재단");
        assertThat(first.range().start()).isEqualTo(LocalDate.of(2026, 10, 14));
        assertThat(first.range().end()).isEqualTo(LocalDate.of(2026, 11, 13));
        assertThat(first.range().endsAt()).isEqualTo(LocalTime.of(18, 0));
    }

    @Test
    void leaveDeadlineNoticeIsReadable() {
        NoticeView v = NoticeViewParser.parse(Fixtures.read("notice-276997.html"));
        List<ExtractedItem> items = DateItemExtractor.extract(v);
        System.out.println("  body: " + v.bodyText().substring(0, Math.min(300, v.bodyText().length())));
        items.forEach(i -> System.out.println("  " + i));
        assertThat(v.title()).contains("휴학");
    }
}
