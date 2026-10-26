package com.campusalimi.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String baseUrl,
        long fetchIntervalMs,
        String runOnce,
        Schedule schedule,
        Notice notice) {

    public record Schedule(String cron, String campus, List<Integer> years) {
    }

    public record Notice(String cron, int pageSize, List<Category> categories) {
    }

    /** 공지 게시판 분류. 학교 페이지의 srCategoryId 값과 짝이다. */
    public record Category(String name, int id) {
    }

    public String noticeListUrl(int categoryId) {
        return baseUrl + "/ko/campuslife/notice.do?mode=list&srCategoryId=" + categoryId
                + "&articleLimit=" + notice.pageSize();
    }

    public String noticeViewUrl(long articleNo) {
        return baseUrl + "/ko/campuslife/notice.do?mode=view&articleNo=" + articleNo;
    }

    public String scheduleUrl(int year) {
        return baseUrl + "/ko/support/calendar2024_list.do?mode=list&siteId=ko&selectedYear=" + year;
    }
}
