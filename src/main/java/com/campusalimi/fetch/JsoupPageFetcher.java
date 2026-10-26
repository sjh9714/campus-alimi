package com.campusalimi.fetch;

import com.campusalimi.config.AppProperties;
import com.campusalimi.domain.FetchLog;
import com.campusalimi.domain.FetchLogRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

/** 학교 서버에 요청 간격을 지키며 페이지를 읽고, 요청마다 fetch_log를 남긴다. */
@Component
public class JsoupPageFetcher implements PageFetcher {

    private static final String USER_AGENT = "campus-alimi/0.1 (student notice collector; contact in README)";

    private final AppProperties props;
    private final FetchLogRepository fetchLogs;
    private long lastRequestAt = 0;

    public JsoupPageFetcher(AppProperties props, FetchLogRepository fetchLogs) {
        this.props = props;
        this.fetchLogs = fetchLogs;
    }

    @Override
    public synchronized String get(String url) {
        waitForInterval();
        String source = url.contains("calendar") ? "SCHEDULE" : "NOTICE";
        try {
            Connection.Response response = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(15_000)
                    .execute();
            fetchLogs.save(FetchLog.ok(source, url, response.statusCode()));
            return response.body();
        } catch (IOException e) {
            fetchLogs.save(FetchLog.failed(source, url, e.getMessage()));
            throw new UncheckedIOException("fetch failed: " + url, e);
        } finally {
            lastRequestAt = System.currentTimeMillis();
        }
    }

    private void waitForInterval() {
        long wait = lastRequestAt + props.fetchIntervalMs() - System.currentTimeMillis();
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
