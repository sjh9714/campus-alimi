package com.campusalimi.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 공지 한 건. 들어오는 순간부터 검수 대상이다. */
@Entity
@Table(name = "notice")
public class Notice {

    public static final String STATUS_NEEDS_REVIEW = "NEEDS_REVIEW";
    public static final String STATUS_REVIEWED = "REVIEWED";

    /** 날짜를 하나 이상 읽었다. 검수에서 확인만 하면 된다. */
    public static final String REASON_PARSED = "PARSED";
    /** 글자에서 날짜를 못 읽었고 본문에 이미지가 있다. 날짜가 이미지 안에 있을 수 있으니 운영자가 보고 직접 입력한다. */
    public static final String REASON_IMAGE_ONLY = "IMAGE_ONLY";
    /** 글자는 있는데 날짜를 못 읽었다. */
    public static final String REASON_NO_DATE = "NO_DATE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long articleNo;
    private String category;
    private String title;
    private LocalDate postedOn;
    private String writer;
    private String url;
    private String bodyText;
    private boolean hasImages;
    private String attachments;
    private String status = STATUS_NEEDS_REVIEW;
    private String reviewReason;
    private OffsetDateTime firstSeenAt = OffsetDateTime.now();
    private OffsetDateTime reviewedAt;

    protected Notice() {
    }

    public Notice(long articleNo, String category, String title, LocalDate postedOn, String writer,
            String url, String bodyText, boolean hasImages, String attachments, String reviewReason) {
        this.articleNo = articleNo;
        this.category = category;
        this.title = title;
        this.postedOn = postedOn;
        this.writer = writer;
        this.url = url;
        this.bodyText = bodyText;
        this.hasImages = hasImages;
        this.attachments = attachments;
        this.reviewReason = reviewReason;
    }

    public Long getId() {
        return id;
    }

    public Long getArticleNo() {
        return articleNo;
    }

    public String getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public LocalDate getPostedOn() {
        return postedOn;
    }

    public String getUrl() {
        return url;
    }

    public boolean isHasImages() {
        return hasImages;
    }

    public String getStatus() {
        return status;
    }

    public String getReviewReason() {
        return reviewReason;
    }
}
