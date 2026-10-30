package com.campusalimi.notice;

import com.campusalimi.config.AppProperties;
import com.campusalimi.domain.DateItemRepository;
import com.campusalimi.domain.ImageTranscript;
import com.campusalimi.domain.ImageTranscriptRepository;
import com.campusalimi.domain.Notice;
import com.campusalimi.domain.NoticeRawRepository;
import com.campusalimi.domain.NoticeRepository;
import com.campusalimi.feed.FeedService;
import com.campusalimi.fetch.JsoupPageFetcher;
import com.campusalimi.fetch.PageFetcher;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 날짜가 그림에만 있는 공지의 그림을 AI가 받아 적고, 그 글을 공지 날짜 규칙에 그대로 넣는다 (ADR 010).
 * AI는 글자만 옮긴다. 날짜는 규칙이 뽑고, 뽑은 날짜는 후보로 저장해 운영자가 승인해야 학생에게 나간다.
 * 대상은 홈의 "날짜 확인 중"에 있는 공지와 같다. 그림 주소마다 한 번만 받아 적고, 하루 상한을 넘기지 않는다.
 */
@Service
public class ImageNoticeReader {

    private static final Logger log = LoggerFactory.getLogger(ImageNoticeReader.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    /** 공지 하나에서 받아 적는 그림 수의 상한. 그림이 수십 장인 공지(강의 변경표 묶음)가 하루치를 다 쓰지 않게. */
    static final int MAX_IMAGES_PER_NOTICE = 6;

    private final AppProperties props;
    private final NoticeRepository notices;
    private final NoticeRawRepository raws;
    private final DateItemRepository dateItems;
    private final ImageTranscriptRepository transcripts;
    private final PageFetcher fetcher;
    private final ImageTranscriber transcriber;
    private final NoticeStore store;
    private final Clock clock;

    public ImageNoticeReader(AppProperties props, NoticeRepository notices, NoticeRawRepository raws,
            DateItemRepository dateItems, ImageTranscriptRepository transcripts, PageFetcher fetcher,
            ImageTranscriber transcriber, NoticeStore store, Clock clock) {
        this.props = props;
        this.notices = notices;
        this.raws = raws;
        this.dateItems = dateItems;
        this.transcripts = transcripts;
        this.fetcher = fetcher;
        this.transcriber = transcriber;
        this.store = store;
        this.clock = clock;
    }

    public record Result(int notices, int newTranscripts, int candidates, boolean limitReached) {
    }

    @Scheduled(cron = "${app.transcribe.cron}", zone = "Asia/Seoul")
    public void readScheduled() {
        try {
            Result r = readPending(LocalDate.now(clock.withZone(SEOUL)));
            if (r.notices() > 0) {
                log.info("image notices={} newTranscripts={} candidates={} limitReached={}", r.notices(),
                        r.newTranscripts(), r.candidates(), r.limitReached());
            }
        } catch (RuntimeException e) {
            log.error("image reading failed", e);
        }
    }

    public Result readPending(LocalDate today) {
        if (!transcriber.enabled()) {
            return new Result(0, 0, 0, false);
        }
        int seen = 0;
        int fresh = 0;
        int candidates = 0;
        for (Notice n : notices.findByStatusAndReviewReasonAndPostedOnGreaterThanEqual(Notice.STATUS_NEEDS_REVIEW,
                Notice.REASON_IMAGE_ONLY, today.minusDays(FeedService.UNDATED_DAYS))) {
            if (!FeedService.undatedCategory(n) || dateItems.existsByNoticeId(n.getId())) {
                continue;
            }
            seen++;
            try {
                Read read = read(n, today);
                fresh += read.newTranscripts();
                candidates += read.candidates();
                if (read.limitReached()) {
                    return new Result(seen, fresh, candidates, true);
                }
            } catch (RuntimeException e) {
                log.warn("image notice {} failed: {}", n.getArticleNo(), e.getMessage());
            }
        }
        return new Result(seen, fresh, candidates, false);
    }

    private record Read(int newTranscripts, int candidates, boolean limitReached) {
    }

    private Read read(Notice n, LocalDate today) {
        NoticeView view = NoticeViewParser.parse(raws.findById(n.getArticleNo()).orElseThrow().getHtml());
        URI base = URI.create(props.baseUrl() + "/");
        List<String> urls = view.body().select("img").eachAttr("src").stream()
                .filter(s -> !s.isBlank() && !s.startsWith("data:"))
                .map(s -> base.resolve(s.trim()).toString())
                .distinct().limit(MAX_IMAGES_PER_NOTICE).toList();
        List<String> texts = new ArrayList<>();
        int fresh = 0;
        for (String url : urls) {
            Optional<ImageTranscript> cached = transcripts.findById(url);
            if (cached.isPresent()) {
                texts.add(cached.get().getText());
                continue;
            }
            if (transcripts.countByCreatedAtAfter(today.atStartOfDay(SEOUL).toOffsetDateTime()) >= props.transcribe().dailyLimit()) {
                return new Read(fresh, 0, true);
            }
            ImageTranscriber.Result r = transcribe(url);
            transcripts.save(new ImageTranscript(url, n.getArticleNo(), r.text(), r.model(), r.inputTokens(),
                    r.outputTokens()));
            texts.add(r.text());
            fresh++;
        }
        return new Read(fresh, store.saveImageItems(n.getId(), view.articleNo(), extractFromTranscripts(view, texts)),
                false);
    }

    /** 받아 적은 글을 공지 본문 자리에 넣고 글 공지와 같은 규칙으로 날짜를 뽑는다. 품질 백테스트도 이 메서드를 쓴다. */
    public static List<ExtractedItem> extractFromTranscripts(NoticeView view, List<String> texts) {
        String html = String.join("\n", texts).replace("```html", "").replace("```", "");
        NoticeView transcribed = new NoticeView(view.articleNo(), view.category(), view.title(), view.postedOn(),
                view.writer(), Jsoup.parseBodyFragment(html).body(), html, false, List.of());
        return DateItemExtractor.extract(transcribed);
    }

    /**
     * 학교 밖 주소, 받지 않는 형식, 너무 큰 그림은 빈 글로 기록해 다시 시도하지 않는다.
     * 받아 적기가 실패해도 기록한다. 요금이 나갔을 수 있으니 매시간 다시 부르지 않고, 하루 상한에도 센다.
     * 그림을 받지 못한 것(학교 서버 문제)은 요금이 없으니 기록하지 않고 다음 차례에 다시 시도한다.
     */
    private ImageTranscriber.Result transcribe(String url) {
        if (!onSchoolSite(url, props.baseUrl())) {
            return new ImageTranscriber.Result("", "skipped:host", 0, 0);
        }
        String mediaType = mediaType(url);
        if (mediaType == null) {
            return new ImageTranscriber.Result("", "skipped:type", 0, 0);
        }
        byte[] bytes = fetcher.getBytes(url);
        if (bytes.length > JsoupPageFetcher.MAX_IMAGE_BYTES) {
            return new ImageTranscriber.Result("", "skipped:size", 0, 0);
        }
        try {
            return transcriber.transcribe(bytes, mediaType);
        } catch (RuntimeException e) {
            log.warn("transcribe {} failed: {}", url, e.getMessage());
            return new ImageTranscriber.Result("", "failed", 0, 0);
        }
    }

    /** 공지 본문의 그림 주소는 학교가 아닌 곳을 가리킬 수 있다. 서버가 아무 주소나 부르지 않게 학교 주소만 받는다. */
    static boolean onSchoolSite(String url, String baseUrl) {
        URI u = URI.create(url);
        URI base = URI.create(baseUrl);
        return ("https".equals(u.getScheme()) || "http".equals(u.getScheme()))
                && base.getHost().equalsIgnoreCase(u.getHost()) && u.getPort() == base.getPort() && u.getUserInfo() == null;
    }

    static String mediaType(String url) {
        String path = URI.create(url).getPath().toLowerCase(Locale.ROOT);
        if (path.endsWith(".png")) {
            return "image/png";
        }
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (path.endsWith(".gif")) {
            return "image/gif";
        }
        if (path.endsWith(".webp")) {
            return "image/webp";
        }
        return null;
    }
}
