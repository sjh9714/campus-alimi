package com.campusalimi.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

/** 화면에 나가는 날짜 한 줄. 학사일정, 공지에서 뽑은 것, 운영자가 손으로 넣은 것이 모두 같은 모양이다. */
@Entity
@Table(name = "date_item")
public class DateItem {

    public static final String SOURCE_SCHEDULE = "SCHEDULE";
    public static final String SOURCE_NOTICE = "NOTICE";
    public static final String SOURCE_MANUAL = "MANUAL";

    public static final String STATUS_AUTO = "AUTO";
    public static final String STATUS_REVIEWED = "REVIEWED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String sourceKind;
    private Long noticeId;
    private String naturalKey;
    private String title;
    private LocalDate startsOn;
    private LocalDate endsOn;
    private LocalTime startsAt;
    private LocalTime endsAt;
    private String campus;
    private String targetGrade;
    private String targetDept;
    private String targetNote;
    private String status = STATUS_AUTO;
    private OffsetDateTime createdAt = OffsetDateTime.now();
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    protected DateItem() {
    }

    public static DateItem fromSchedule(String naturalKey, String title, LocalDate startsOn, LocalDate endsOn,
            String campus) {
        DateItem item = new DateItem();
        item.sourceKind = SOURCE_SCHEDULE;
        item.naturalKey = naturalKey;
        item.title = title;
        item.startsOn = startsOn;
        item.endsOn = endsOn;
        item.campus = campus;
        item.targetGrade = "ALL";
        item.targetDept = "ALL";
        return item;
    }

    public static DateItem fromNotice(Long noticeId, String naturalKey, String title, LocalDate startsOn,
            LocalDate endsOn, LocalTime startsAt, LocalTime endsAt, String targetGrade) {
        DateItem item = new DateItem();
        item.sourceKind = SOURCE_NOTICE;
        item.noticeId = noticeId;
        item.naturalKey = naturalKey;
        item.title = title;
        item.startsOn = startsOn;
        item.endsOn = endsOn;
        item.startsAt = startsAt;
        item.endsAt = endsAt;
        item.targetGrade = targetGrade;
        return item;
    }

    /** 검수 전(AUTO) 항목만 수집 결과로 덮어쓴다. 사람이 고친 것은 건드리지 않는다. */
    public boolean refreshFrom(DateItem fresh) {
        if (!STATUS_AUTO.equals(status)) {
            return false;
        }
        boolean changed = !fresh.title.equals(title)
                || !java.util.Objects.equals(fresh.startsOn, startsOn)
                || !fresh.endsOn.equals(endsOn)
                || !java.util.Objects.equals(fresh.startsAt, startsAt)
                || !java.util.Objects.equals(fresh.endsAt, endsAt);
        if (changed) {
            title = fresh.title;
            startsOn = fresh.startsOn;
            endsOn = fresh.endsOn;
            startsAt = fresh.startsAt;
            endsAt = fresh.endsAt;
            updatedAt = OffsetDateTime.now();
        }
        return changed;
    }

    public Long getId() {
        return id;
    }

    public String getSourceKind() {
        return sourceKind;
    }

    public Long getNoticeId() {
        return noticeId;
    }

    public String getNaturalKey() {
        return naturalKey;
    }

    public String getTitle() {
        return title;
    }

    public LocalDate getStartsOn() {
        return startsOn;
    }

    public LocalDate getEndsOn() {
        return endsOn;
    }

    public LocalTime getStartsAt() {
        return startsAt;
    }

    public LocalTime getEndsAt() {
        return endsAt;
    }

    public String getCampus() {
        return campus;
    }

    public String getTargetGrade() {
        return targetGrade;
    }

    public String getStatus() {
        return status;
    }
}
