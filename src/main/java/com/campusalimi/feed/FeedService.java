package com.campusalimi.feed;

import com.campusalimi.config.AppProperties;
import com.campusalimi.domain.AppUser;
import com.campusalimi.domain.DateItem;
import com.campusalimi.domain.DateItemRepository;
import com.campusalimi.domain.Notice;
import com.campusalimi.domain.NoticeRepository;
import com.campusalimi.domain.SavedItem;
import com.campusalimi.domain.SavedItemRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 한 사람의 홈 피드. 홈 화면과 아침 알림이 같은 것을 본다. */
@Service
public class FeedService {

    private final AppProperties props;
    private final DateItemRepository dateItems;
    private final NoticeRepository notices;
    private final SavedItemRepository saved;

    public FeedService(AppProperties props, DateItemRepository dateItems, NoticeRepository notices, SavedItemRepository saved) {
        this.props = props;
        this.dateItems = dateItems;
        this.notices = notices;
        this.saved = saved;
    }

    /** 피드와, 카드가 가리키는 공지. 알림은 공지가 언제 들어왔는지 봐야 한다. */
    public record Built(Feed.View view, Map<Long, Notice> notices) {
    }

    @Transactional(readOnly = true)
    public Built build(AppUser user, LocalDate today) {
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
        return new Built(Feed.build(upcoming, noticeById, audiences, viewer, user != null, savedIds, props::scheduleUrl),
                noticeById);
    }
}
