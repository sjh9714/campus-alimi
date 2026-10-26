package com.campusalimi.schedule;

import java.time.LocalDate;
import java.util.List;

/** 학사일정 표의 한 줄. */
public record ScheduleEntry(String title, LocalDate startsOn, LocalDate endsOn, List<String> campuses) {

    public String naturalKey(int academicYear) {
        return "schedule:" + academicYear + ":" + startsOn + ":" + endsOn + ":" + title + ":" + String.join("+", campuses);
    }
}
