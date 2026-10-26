package com.campusalimi.api;

import com.campusalimi.auth.AuthService;
import com.campusalimi.domain.AppUser;
import com.campusalimi.domain.Benefit;
import com.campusalimi.domain.BenefitRepository;
import com.campusalimi.domain.DateItem;
import com.campusalimi.domain.DateItemRepository;
import com.campusalimi.domain.Notice;
import com.campusalimi.domain.NoticeRepository;
import com.campusalimi.domain.SavedItem;
import com.campusalimi.domain.SavedItemRepository;
import com.campusalimi.domain.UserBenefit;
import com.campusalimi.domain.UserBenefitRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 읽기는 누구나. 저장·체크는 로그인한 사람만. */
@RestController
@RequestMapping("/api")
public class ItemsController {

    private final AuthService auth;
    private final DateItemRepository dateItems;
    private final NoticeRepository notices;
    private final BenefitRepository benefits;
    private final SavedItemRepository saved;
    private final UserBenefitRepository userBenefits;

    public ItemsController(AuthService auth, DateItemRepository dateItems, NoticeRepository notices,
            BenefitRepository benefits, SavedItemRepository saved, UserBenefitRepository userBenefits) {
        this.auth = auth;
        this.dateItems = dateItems;
        this.notices = notices;
        this.benefits = benefits;
        this.saved = saved;
        this.userBenefits = userBenefits;
    }

    public record NoticeRef(long articleNo, String title, String url, String category, String reviewReason, String status) {
    }

    /**
     * bucket. 0 = 내 프로필과 맞음, 1 = 전체 대상, 2 = 대상 확인 필요. 숨기는 것은 없다 (ADR 008).
     * reviewed = 사람이 확인한 항목. 아니면 "검수 전"으로 보여준다.
     */
    public record ItemView(long id, String title, LocalDate startsOn, LocalDate endsOn, LocalTime startsAt,
            LocalTime endsAt, String sourceKind, boolean reviewed, String targetGrade, String targetDept,
            String targetNote, String campus, NoticeRef notice, int bucket, boolean saved) {
    }

    public record BenefitView(long id, String slug, String name, String category, String summary, String condition,
            String howTo, String price, String sourceName, String sourceUrl, LocalDate verifiedOn, String renewalRule,
            LocalDate endsOn, boolean stale, boolean checked) {
    }

    @GetMapping("/items")
    @Transactional(readOnly = true)
    public List<ItemView> items(HttpServletRequest request,
            @RequestParam(defaultValue = "false") boolean includePast) {
        LocalDate today = LocalDate.now();
        AppUser user = auth.currentUser(request).orElse(null);
        Integer grade = user == null ? null : user.grade(today);
        String major = user == null ? null : user.getMajor();
        String second = user == null ? null : user.getSecondMajor();
        Set<Long> savedIds = user == null ? Set.of()
                : saved.findByUserId(user.getId()).stream().map(SavedItem::getDateItemId).collect(Collectors.toSet());

        List<DateItem> items = includePast ? dateItems.findAll() : dateItems.findByEndsOnGreaterThanEqual(today);
        Map<Long, Notice> noticeById = new HashMap<>();
        for (Notice n : notices.findAllById(items.stream().map(DateItem::getNoticeId).filter(id -> id != null).toList())) {
            noticeById.put(n.getId(), n);
        }
        return items.stream()
                .map(i -> toView(i, noticeById.get(i.getNoticeId()), grade, major, second, savedIds.contains(i.getId())))
                .sorted(Comparator.comparingInt(ItemView::bucket).thenComparing(ItemView::endsOn)
                        .thenComparing(ItemView::title))
                .toList();
    }

    static ItemView toView(DateItem i, Notice n, Integer grade, String major, String second, boolean isSaved) {
        NoticeRef ref = n == null ? null
                : new NoticeRef(n.getArticleNo(), n.getTitle(), n.getUrl(), n.getCategory(), n.getReviewReason(), n.getStatus());
        return new ItemView(i.getId(), i.getTitle(), i.getStartsOn(), i.getEndsOn(), i.getStartsAt(), i.getEndsAt(),
                i.getSourceKind(), DateItem.STATUS_REVIEWED.equals(i.getStatus()), i.getTargetGrade(), i.getTargetDept(),
                i.getTargetNote(), i.getCampus(), ref, bucket(i, grade, major, second), isSaved);
    }

    static int bucket(DateItem i, Integer grade, String major, String second) {
        String g = i.getTargetGrade();
        String d = i.getTargetDept();
        if (g == null || d == null) {
            return 2;
        }
        boolean gradeMatch = "ALL".equals(g) || (grade != null && g.equals(String.valueOf(grade)));
        boolean deptMatch = "ALL".equals(d) || (major != null && d.contains(major)) || (second != null && d.contains(second));
        if (gradeMatch && deptMatch && !("ALL".equals(g) && "ALL".equals(d))) {
            return 0;
        }
        if ("ALL".equals(g) && "ALL".equals(d)) {
            return 1;
        }
        return gradeMatch && deptMatch ? 0 : 1;
    }

    @GetMapping("/benefits")
    @Transactional(readOnly = true)
    public List<BenefitView> benefits(HttpServletRequest request) {
        LocalDate today = LocalDate.now();
        AppUser user = auth.currentUser(request).orElse(null);
        Set<Long> checked = user == null ? Set.of()
                : userBenefits.findByUserId(user.getId()).stream().map(UserBenefit::getBenefitId).collect(Collectors.toSet());
        return benefits.findByActiveTrueOrderByCategoryAscNameAsc().stream()
                .map(b -> new BenefitView(b.getId(), b.getSlug(), b.getName(), b.getCategory(), b.getSummary(),
                        b.getCondition(), b.getHowTo(), b.getPrice(), b.getSourceName(), b.getSourceUrl(),
                        b.getVerifiedOn(), b.getRenewalRule(), b.getEndsOn(), b.isStale(today), checked.contains(b.getId())))
                .toList();
    }

    @PostMapping("/me/saved/{itemId}")
    @Transactional
    public ResponseEntity<Void> save(HttpServletRequest request, @PathVariable long itemId) {
        AppUser user = auth.requireUser(request);
        dateItems.findById(itemId).orElseThrow();
        saved.save(new SavedItem(user.getId(), itemId));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me/saved/{itemId}")
    @Transactional
    public ResponseEntity<Void> unsave(HttpServletRequest request, @PathVariable long itemId) {
        AppUser user = auth.requireUser(request);
        saved.deleteById(new SavedItem.Key(user.getId(), itemId));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/benefits/{benefitId}")
    @Transactional
    public ResponseEntity<Void> check(HttpServletRequest request, @PathVariable long benefitId) {
        AppUser user = auth.requireUser(request);
        Benefit b = benefits.findById(benefitId).orElseThrow();
        userBenefits.save(new UserBenefit(user.getId(), b.getId()));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me/benefits/{benefitId}")
    @Transactional
    public ResponseEntity<Void> uncheck(HttpServletRequest request, @PathVariable long benefitId) {
        AppUser user = auth.requireUser(request);
        userBenefits.deleteById(new UserBenefit.Key(user.getId(), benefitId));
        return ResponseEntity.noContent().build();
    }
}
