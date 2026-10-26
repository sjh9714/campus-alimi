package com.campusalimi.dateparse;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class KoreanDateParserTest {

    private final KoreanDateParser parser = new KoreanDateParser(LocalDate.of(2026, 10, 1));

    @Test
    void fullRangeWithTimes() {
        List<DateRange> r = parser.parse("2026. 7. 7.(화) ~ 7. 12.(일) / 09:00 ~ 23:59");
        assertThat(r).hasSize(1);
        assertThat(r.get(0).start()).isEqualTo(LocalDate.of(2026, 7, 7));
        assertThat(r.get(0).end()).isEqualTo(LocalDate.of(2026, 7, 12));
        assertThat(r.get(0).startsAt()).isEqualTo(LocalTime.of(9, 0));
        assertThat(r.get(0).endsAt()).isEqualTo(LocalTime.of(23, 59));
    }

    @Test
    void singleDayWithTimeWindow() {
        List<DateRange> r = parser.parse("2026. 8. 4.(화) 09:00 ~ 15:00");
        assertThat(r).hasSize(1);
        assertThat(r.get(0).isSingleDay()).isTrue();
        assertThat(r.get(0).startsAt()).isEqualTo(LocalTime.of(9, 0));
        assertThat(r.get(0).endsAt()).isEqualTo(LocalTime.of(15, 0));
    }

    @Test
    void titleRangeWithoutYearUsesBaseYear() {
        List<DateRange> r = parser.parse("두산연강재단 대학 장학생 선발 안내(10. 14.(수) ~ 11. 13.(금) 18:00까지)");
        assertThat(r).hasSize(1);
        assertThat(r.get(0).start()).isEqualTo(LocalDate.of(2026, 10, 14));
        assertThat(r.get(0).end()).isEqualTo(LocalDate.of(2026, 11, 13));
        assertThat(r.get(0).endsAt()).isEqualTo(LocalTime.of(18, 0));
    }

    @Test
    void endOnly() {
        List<DateRange> r = parser.parse("북한이탈청소년 장학생 선발 안내(~10.26)");
        assertThat(r).hasSize(1);
        assertThat(r.get(0).start()).isNull();
        assertThat(r.get(0).end()).isEqualTo(LocalDate.of(2026, 10, 26));
    }

    @Test
    void slashRangeInParens() {
        List<DateRange> r = parser.parse("국가근로장학금 추가신청기간 안내(10/21~10/29)");
        assertThat(r).hasSize(1);
        assertThat(r.get(0).start()).isEqualTo(LocalDate.of(2026, 10, 21));
        assertThat(r.get(0).end()).isEqualTo(LocalDate.of(2026, 10, 29));
    }

    @Test
    void koreanUnits() {
        List<DateRange> r = parser.parse("접수 기간: 2026년 10월 30일(금) 18:00까지");
        assertThat(r).hasSize(1);
        assertThat(r.get(0).end()).isEqualTo(LocalDate.of(2026, 10, 30));
        assertThat(r.get(0).startsAt()).isEqualTo(LocalTime.of(18, 0));
    }

    @Test
    void yearWrapsForNextSpring() {
        List<DateRange> r = parser.parse("신청: 1. 5.(월) ~ 1. 9.(금)");
        assertThat(r).hasSize(1);
        assertThat(r.get(0).start()).isEqualTo(LocalDate.of(2027, 1, 5));
    }

    @Test
    void loneMonthDayWithoutWeekdayIsIgnored() {
        assertThat(parser.parse("수업일수 1/4 경과")).isEmpty();
        assertThat(parser.parse("직전 학기 평점 3.0 이상")).isEmpty();
        assertThat(parser.parse("문의 02-2164-4149")).isEmpty();
        assertThat(parser.parse("2026학년도 2학기")).isEmpty();
        assertThat(parser.parse("동일 과목 1,000번 클릭")).isEmpty();
    }

    @Test
    void multipleLinesYieldMultipleRanges() {
        List<DateRange> r = parser.parse("2026. 8. 31.(월) 09:00 ~ 17:00 2026. 9. 1.(화) 09:00 ~ 17:00 2026. 9. 4.(금) 09:00 ~ 17:00");
        assertThat(r).hasSize(3);
        assertThat(r.get(2).end()).isEqualTo(LocalDate.of(2026, 9, 4));
    }
}
