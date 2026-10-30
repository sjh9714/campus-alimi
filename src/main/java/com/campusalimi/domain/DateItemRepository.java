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

    /** 주어진 공지 가운데 status가 아닌 날짜 항목이 하나라도 있는 공지. */
    @org.springframework.data.jpa.repository.Query("select distinct d.noticeId from DateItem d where d.noticeId in :ids and d.status <> :status")
    java.util.Set<Long> findNoticeIdsWithItemsNotIn(@org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids,
            @org.springframework.data.repository.query.Param("status") String status);
}
