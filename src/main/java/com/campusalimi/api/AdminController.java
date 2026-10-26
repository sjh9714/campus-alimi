package com.campusalimi.api;

import com.campusalimi.auth.AuthService;
import com.campusalimi.domain.AppUser;
import com.campusalimi.domain.DateItem;
import com.campusalimi.domain.DateItemRepository;
import com.campusalimi.domain.Notice;
import com.campusalimi.domain.NoticeRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 검수 화면의 API. 관리자 이메일 한 명만. 날짜 항목을 확인·수정·추가하고 대상 태그를 붙인다 (ADR 003, 008). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AuthService auth;
    private final NoticeRepository notices;
    private final DateItemRepository dateItems;

    public AdminController(AuthService auth, NoticeRepository notices, DateItemRepository dateItems) {
        this.auth = auth;
        this.notices = notices;
        this.dateItems = dateItems;
    }

    public record ReviewNotice(long id, long articleNo, String category, String title, LocalDate postedOn, String url,
            String reviewReason, boolean hasImages, String attachments, String status, List<ItemsController.ItemView> items) {
    }

    public record ItemEdit(String title, LocalDate startsOn, LocalDate endsOn, LocalTime startsAt, LocalTime endsAt,
            String targetGrade, String targetDept, String targetNote) {
        void validate() {
            if (title == null || title.isBlank()) {
                throw new IllegalArgumentException("제목이 비었습니다");
            }
            if (endsOn == null) {
                throw new IllegalArgumentException("마감일이 비었습니다. 모르면 항목을 지우고 공지만 검수 완료로 두세요");
            }
            if (startsOn != null && startsOn.isAfter(endsOn)) {
                throw new IllegalArgumentException("시작이 마감보다 늦습니다");
            }
            if (targetGrade != null && !List.of("ALL", "1", "2", "3", "4").contains(targetGrade)) {
                throw new IllegalArgumentException("학년은 ALL, 1, 2, 3, 4 중 하나");
            }
        }
    }

    @GetMapping("/review")
    @Transactional(readOnly = true)
    public List<ReviewNotice> review(HttpServletRequest request) {
        auth.requireAdmin(request);
        return notices.findByStatusOrderByPostedOnDesc(Notice.STATUS_NEEDS_REVIEW).stream()
                .map(this::toReview)
                .toList();
    }

    @GetMapping("/notices/{noticeId}")
    @Transactional(readOnly = true)
    public ReviewNotice one(HttpServletRequest request, @PathVariable long noticeId) {
        auth.requireAdmin(request);
        return toReview(notices.findById(noticeId).orElseThrow());
    }

    @PostMapping("/notices/{noticeId}/reviewed")
    @Transactional
    public ReviewNotice markReviewed(HttpServletRequest request, @PathVariable long noticeId) {
        AppUser admin = auth.requireAdmin(request);
        Notice notice = notices.findById(noticeId).orElseThrow();
        notice.markReviewed(admin.getEmail());
        // 공지를 검수 완료로 두면 그 아래 자동 항목도 확인한 것으로 본다.
        for (DateItem item : dateItems.findByNoticeIdOrderByEndsOnAsc(noticeId)) {
            if (DateItem.STATUS_AUTO.equals(item.getStatus())) {
                item.markReviewed(admin.getEmail());
            }
        }
        return toReview(notice);
    }

    @PutMapping("/items/{itemId}")
    @Transactional
    public ItemsController.ItemView editItem(HttpServletRequest request, @PathVariable long itemId,
            @RequestBody ItemEdit body) {
        AppUser admin = auth.requireAdmin(request);
        body.validate();
        DateItem item = dateItems.findById(itemId).orElseThrow();
        item.review(body.title(), body.startsOn(), body.endsOn(), body.startsAt(), body.endsAt(), body.targetGrade(),
                body.targetDept(), body.targetNote(), admin.getEmail());
        Notice n = item.getNoticeId() == null ? null : notices.findById(item.getNoticeId()).orElse(null);
        return ItemsController.toView(item, n, null, null, null, false);
    }

    @PostMapping("/notices/{noticeId}/items")
    @Transactional
    public ItemsController.ItemView addItem(HttpServletRequest request, @PathVariable long noticeId,
            @RequestBody ItemEdit body) {
        AppUser admin = auth.requireAdmin(request);
        body.validate();
        Notice notice = notices.findById(noticeId).orElseThrow();
        DateItem item = dateItems.save(DateItem.manual(notice.getId(), body.title(), body.startsOn(), body.endsOn(),
                body.startsAt(), body.endsAt(), body.targetGrade(), body.targetDept(), body.targetNote(), admin.getEmail()));
        return ItemsController.toView(item, notice, null, null, null, false);
    }

    @DeleteMapping("/items/{itemId}")
    @Transactional
    public ResponseEntity<Void> deleteItem(HttpServletRequest request, @PathVariable long itemId) {
        auth.requireAdmin(request);
        dateItems.deleteById(itemId);
        return ResponseEntity.noContent().build();
    }

    private ReviewNotice toReview(Notice n) {
        List<ItemsController.ItemView> items = dateItems.findByNoticeIdOrderByEndsOnAsc(n.getId()).stream()
                .map(i -> ItemsController.toView(i, n, null, null, null, false))
                .toList();
        return new ReviewNotice(n.getId(), n.getArticleNo(), n.getCategory(), n.getTitle(), n.getPostedOn(), n.getUrl(),
                n.getReviewReason(), n.isHasImages(), n.getAttachments(), n.getStatus(), items);
    }
}
