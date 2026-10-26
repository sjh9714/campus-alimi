package com.campusalimi.notice;

import com.campusalimi.config.AppProperties;
import com.campusalimi.domain.DateItem;
import com.campusalimi.domain.DateItemRepository;
import com.campusalimi.domain.Notice;
import com.campusalimi.domain.NoticeRaw;
import com.campusalimi.domain.NoticeRawRepository;
import com.campusalimi.domain.NoticeRepository;
import com.campusalimi.fetch.PageFetcher;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공지 목록을 1시간마다 읽고, 처음 보는 글만 본문을 가져와 원문·공지·날짜 항목을 저장한다.
 * 모든 공지는 NEEDS_REVIEW로 들어온다. 검수는 사람이 한다.
 */
@Service
public class NoticeCollector {

    private static final Logger log = LoggerFactory.getLogger(NoticeCollector.class);

    private final AppProperties props;
    private final PageFetcher fetcher;
    private final NoticeRepository notices;
    private final NoticeRawRepository raws;
    private final DateItemRepository dateItems;

    public NoticeCollector(AppProperties props, PageFetcher fetcher, NoticeRepository notices,
            NoticeRawRepository raws, DateItemRepository dateItems) {
        this.props = props;
        this.fetcher = fetcher;
        this.notices = notices;
        this.raws = raws;
        this.dateItems = dateItems;
    }

    public record Result(int listed, int fetched, int items, int needsManual) {
        Result plus(Result o) {
            return new Result(listed + o.listed, fetched + o.fetched, items + o.items, needsManual + o.needsManual);
        }
    }

    @Scheduled(cron = "${app.notice.cron}", zone = "Asia/Seoul")
    public void collectScheduled() {
        try {
            Result r = collectAll();
            log.info("notice listed={} fetched={} items={} needsManual={}", r.listed(), r.fetched(), r.items(), r.needsManual());
        } catch (RuntimeException e) {
            log.error("notice collect failed", e);
        }
    }

    public Result collectAll() {
        Result total = new Result(0, 0, 0, 0);
        for (AppProperties.Category category : props.notice().categories()) {
            total = total.plus(collectCategory(category.id()));
        }
        return total;
    }

    public Result collectCategory(int categoryId) {
        String listHtml = fetcher.get(props.noticeListUrl(categoryId));
        List<NoticeSummary> summaries = NoticeListParser.parse(listHtml);
        int fetched = 0;
        int items = 0;
        int needsManual = 0;
        for (NoticeSummary summary : summaries) {
            if (notices.existsByArticleNo(summary.articleNo())) {
                continue;
            }
            String html = fetcher.get(props.noticeViewUrl(summary.articleNo()));
            Saved saved = save(summary, html);
            fetched++;
            items += saved.items();
            if (saved.items() == 0) {
                needsManual++;
            }
        }
        return new Result(summaries.size(), fetched, items, needsManual);
    }

    record Saved(long noticeId, int items, String reason) {
    }

    @Transactional
    public Saved save(NoticeSummary summary, String html) {
        NoticeView view = NoticeViewParser.parse(html);
        List<ExtractedItem> extracted = DateItemExtractor.extract(view);
        // 날짜를 못 읽었는데 그림이 있으면 날짜가 그림 안에 있을 가능성이 크다. 운영자가 그림을 열어 보게 한다.
        String reason = extracted.isEmpty()
                ? (view.hasImages() ? Notice.REASON_IMAGE_ONLY : Notice.REASON_NO_DATE)
                : Notice.REASON_PARSED;

        raws.save(new NoticeRaw(view.articleNo(), html));
        // 상단 고정 글은 본문 페이지의 분류 자리에 "공지"가 찍히므로 목록에서 읽은 분류를 쓴다.
        String category = summary.category() == null || summary.category().isBlank() ? view.category() : summary.category();
        Notice notice = notices.save(new Notice(view.articleNo(), category, view.title(), view.postedOn(),
                view.writer(), props.noticeViewUrl(view.articleNo()), view.bodyText(), view.hasImages(),
                String.join("\n", view.attachments()), reason));

        for (ExtractedItem item : extracted) {
            DateItem fresh = DateItem.fromNotice(notice.getId(), item.naturalKey(view.articleNo()), item.title(),
                    item.range().start(), item.range().end(), item.range().startsAt(), item.range().endsAt(),
                    item.targetGrade());
            if (dateItems.findByNaturalKey(fresh.getNaturalKey()).isEmpty()) {
                dateItems.save(fresh);
            }
        }
        log.info("notice {} [{}] {} -> {} items ({})", view.articleNo(), view.category(), view.title(), extracted.size(), reason);
        return new Saved(notice.getId(), extracted.size(), reason);
    }
}
