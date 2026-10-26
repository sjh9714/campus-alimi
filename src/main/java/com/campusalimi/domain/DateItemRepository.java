package com.campusalimi.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DateItemRepository extends JpaRepository<DateItem, Long> {

    Optional<DateItem> findByNaturalKey(String naturalKey);

    List<DateItem> findByNoticeIdOrderByEndsOnAsc(Long noticeId);

    long countBySourceKind(String sourceKind);
}
