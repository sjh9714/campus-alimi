package com.campusalimi.api;

import com.campusalimi.auth.AuthService;
import com.campusalimi.config.AppProperties;
import com.campusalimi.domain.AppUser;
import com.campusalimi.domain.DateItem;
import com.campusalimi.domain.DateItemRepository;
import com.campusalimi.domain.Notice;
import com.campusalimi.domain.NoticeRepository;
import com.campusalimi.domain.SavedItem;
import com.campusalimi.domain.SavedItemRepository;
import com.campusalimi.feed.Audience;
import com.campusalimi.feed.Feed;
import com.campusalimi.feed.Regions;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 홈 화면. 누구나 읽는다. 로그인하면 연고 지역·학년·학과로 "내게 맞는 공지"가 생긴다 (ADR 009). */
@RestController
@RequestMapping("/api")
public class FeedController {

    private final AuthService auth;
    private final AppProperties props;
    private final DateItemRepository dateItems;
    private final NoticeRepository notices;
    private final SavedItemRepository saved;
    private final Clock clock;

    public FeedController(AuthService auth, AppProperties props, DateItemRepository dateItems, NoticeRepository notices,
            SavedItemRepository saved, Clock clock) {
        this.clock = clock;
        this.auth = auth;
        this.props = props;
        this.dateItems = dateItems;
        this.notices = notices;
        this.saved = saved;
    }

    public record SidoView(String name, List<String> sigungu) {
    }

    @GetMapping("/feed")
    @Transactional(readOnly = true)
    public Feed.View feed(HttpServletRequest request) {
        LocalDate today = LocalDate.now(clock);
        AppUser user = auth.currentUser(request).orElse(null);
        Feed.Viewer viewer = user == null ? Feed.Viewer.ANONYMOUS
                : new Feed.Viewer(user.grade(today), user.getMajor(), user.getSecondMajor(),
                        Regions.parse(user.getHomeRegion()).orElse(null));
        Set<Long> savedIds = user == null ? Set.of()
                : saved.findByUserId(user.getId()).stream().map(SavedItem::getDateItemId).collect(Collectors.toSet());

        List<DateItem> upcoming = dateItems.findByEndsOnGreaterThanEqual(today);
        Map<Long, Notice> noticeById = new HashMap<>();
        Map<Long, Audience> audiences = new HashMap<>();
        for (Notice n : notices.findAllById(upcoming.stream().map(DateItem::getNoticeId).filter(Objects::nonNull).toList())) {
            noticeById.put(n.getId(), n);
            audiences.put(n.getId(), Audience.of(n));
        }
        return Feed.build(upcoming, noticeById, audiences, viewer, user != null, savedIds, props::scheduleUrl);
    }

    /** 설정 화면의 연고 지역 고르기. */
    @GetMapping("/regions")
    public List<SidoView> regions() {
        return Regions.all().stream().map(s -> new SidoView(s.name(), s.sigungu())).toList();
    }
}
