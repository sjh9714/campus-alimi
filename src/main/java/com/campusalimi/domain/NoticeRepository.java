package com.campusalimi.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    Optional<Notice> findByArticleNo(long articleNo);

    boolean existsByArticleNo(long articleNo);

    long countByReviewReason(String reviewReason);
}
