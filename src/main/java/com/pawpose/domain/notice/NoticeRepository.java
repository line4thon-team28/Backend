package com.pawpose.domain.notice;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    /** BE-1 upsert 용 */
    Optional<Notice> findByDesertionNo(String desertionNo);
}
