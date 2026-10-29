package com.campusalimi.domain;

import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageTranscriptRepository extends JpaRepository<ImageTranscript, String> {

    long countByCreatedAtAfter(OffsetDateTime since);
}
