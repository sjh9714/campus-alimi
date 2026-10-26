package com.campusalimi.schedule;

import com.campusalimi.domain.DateItem;
import com.campusalimi.domain.DateItemRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 학사일정 한 해치를 date_item에 맞춘다. 한 트랜잭션. 이번에 안 보인 자동 항목은 지운다. */
@Service
public class ScheduleStore {

    private final DateItemRepository dateItems;

    public ScheduleStore(DateItemRepository dateItems) {
        this.dateItems = dateItems;
    }

    public record Result(int seen, int kept, int inserted, int updated, int removed) {
    }

    @Transactional
    public Result apply(int academicYear, String campus, List<ScheduleEntry> entries) {
        Map<String, Integer> ordinals = new HashMap<>();
        Set<String> seen = new HashSet<>();
        int kept = 0;
        int inserted = 0;
        int updated = 0;
        for (ScheduleEntry entry : entries) {
            if (!entry.campuses().contains(campus)) {
                continue;
            }
            kept++;
            int ordinal = ordinals.merge(entry.title(), 1, Integer::sum);
            DateItem fresh = DateItem.fromSchedule(entry.naturalKey(academicYear, ordinal), entry.title(),
                    entry.startsOn(), entry.endsOn(), String.join("+", entry.campuses()));
            seen.add(fresh.getNaturalKey());
            var existing = dateItems.findByNaturalKey(fresh.getNaturalKey());
            if (existing.isEmpty()) {
                dateItems.save(fresh);
                inserted++;
            } else if (existing.get().refreshFrom(fresh)) {
                updated++;
            }
        }
        int removed = 0;
        for (DateItem old : dateItems.findByNaturalKeyStartingWith("schedule:" + academicYear + ":")) {
            if (!seen.contains(old.getNaturalKey()) && DateItem.STATUS_AUTO.equals(old.getStatus())) {
                dateItems.delete(old);
                removed++;
            }
        }
        return new Result(entries.size(), kept, inserted, updated, removed);
    }
}
