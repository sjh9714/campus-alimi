package com.campusalimi.schedule;

import com.campusalimi.config.AppProperties;
import com.campusalimi.domain.DateItem;
import com.campusalimi.domain.DateItemRepository;
import com.campusalimi.fetch.PageFetcher;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 학사일정 표를 하루 한 번 읽어 date_item으로 쌓는다. 지정한 교정 항목만 남긴다. */
@Service
public class ScheduleCollector {

    private static final Logger log = LoggerFactory.getLogger(ScheduleCollector.class);

    private final AppProperties props;
    private final PageFetcher fetcher;
    private final DateItemRepository dateItems;

    public ScheduleCollector(AppProperties props, PageFetcher fetcher, DateItemRepository dateItems) {
        this.props = props;
        this.fetcher = fetcher;
        this.dateItems = dateItems;
    }

    public record Result(int seen, int kept, int inserted, int updated) {
    }

    @Scheduled(cron = "${app.schedule.cron}", zone = "Asia/Seoul")
    public void collectScheduled() {
        for (int year : props.schedule().years()) {
            try {
                Result r = collect(year);
                log.info("schedule {} seen={} kept={} inserted={} updated={}", year, r.seen(), r.kept(), r.inserted(), r.updated());
            } catch (RuntimeException e) {
                log.error("schedule {} failed", year, e);
            }
        }
    }

    @Transactional
    public Result collect(int academicYear) {
        String html = fetcher.get(props.scheduleUrl(academicYear));
        List<ScheduleEntry> entries = ScheduleParser.parse(html, academicYear);
        int kept = 0;
        int inserted = 0;
        int updated = 0;
        for (ScheduleEntry entry : entries) {
            if (!entry.campuses().contains(props.schedule().campus())) {
                continue;
            }
            kept++;
            DateItem fresh = DateItem.fromSchedule(entry.naturalKey(academicYear), entry.title(), entry.startsOn(),
                    entry.endsOn(), String.join("+", entry.campuses()));
            var existing = dateItems.findByNaturalKey(fresh.getNaturalKey());
            if (existing.isEmpty()) {
                dateItems.save(fresh);
                inserted++;
            } else if (existing.get().refreshFrom(fresh)) {
                updated++;
            }
        }
        return new Result(entries.size(), kept, inserted, updated);
    }
}
