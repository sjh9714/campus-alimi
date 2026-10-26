package com.campusalimi.notice;

import com.campusalimi.dateparse.DateRange;

/** 공지 한 건에서 뽑은 날짜 항목 하나. targetGrade는 "ALL", "1"~"4", 또는 모르면 null. */
public record ExtractedItem(String title, DateRange range, String targetGrade, String where) {

    public String naturalKey(long articleNo) {
        return "notice:" + articleNo + ":" + where + ":" + title + ":" + range.start() + ":" + range.end();
    }
}
