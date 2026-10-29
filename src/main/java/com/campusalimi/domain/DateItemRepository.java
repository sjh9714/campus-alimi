package com.campusalimi.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DateItemRepository extends JpaRepository<DateItem, Long> {

    Optional<DateItem> findByNaturalKey(String naturalKey);

    List<DateItem> findByNoticeIdOrderByEndsOnAsc(Long noticeId);

    List<DateItem> findByNaturalKeyStartingWith(String prefix);

    List<DateItem> findByEndsOnGreaterThanEqual(java.time.LocalDate day);

    /** 학생 화면용. 그림에서 읽은 후보(CANDIDATE)는 운영자가 승인하기 전까지 빼고 본다. */
    List<DateItem> findByEndsOnGreaterThanEqualAndStatusNot(java.time.LocalDate day, String status);

    List<DateItem> findByStatusNot(String status);

    long countBySourceKind(String sourceKind);

    boolean existsByNoticeId(Long noticeId);

    boolean existsByNoticeIdAndStatusNot(Long noticeId, String status);
}
