package com.pawpose.domain.match;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MatchRepository extends JpaRepository<Match, Long> {

    List<Match> findByLostPetId(Long lostPetId);

    String VISIBLE = " from Match m left join m.notice n where m.lostPet.id = :petId and m.eligible = true and m.feedback <> com.pawpose.domain.match.MatchFeedback.NO "
            + "and (m.modelVersion is null or exists (select s.id from MatchingSource s where s.active = true and s.status = com.pawpose.domain.match.pipeline.ProcessingStatus.READY and s.modelVersion = m.modelVersion and ((s.kind = com.pawpose.domain.match.pipeline.SourceKind.NOTICE and s.sourceId = n.id) or (s.kind = com.pawpose.domain.match.pipeline.SourceKind.SIGHTING and s.sourceId = m.sightingId)))) "
            + "and (m.type = com.pawpose.domain.match.MatchType.SIGHTING or (n.status = com.pawpose.domain.notice.NoticeStatus.NOTICE and (n.noticeEndDate is null or n.noticeEndDate >= :today))) ";
    String PINNED = " order by case when m.feedback = com.pawpose.domain.match.MatchFeedback.YES then 0 else 1 end, ";

    @Query("select count(m)" + VISIBLE)
    long countVisible(@Param("petId") Long petId, @Param("today") LocalDate today);

    @Query("select count(m) from Match m where m.sightingId = :sightingId and m.eligible = true "
            + "and m.feedback <> com.pawpose.domain.match.MatchFeedback.NO "
            + "and m.lostPet.status = com.pawpose.domain.lostpet.LostPetStatus.SEARCHING "
            + "and m.modelVersion = :version")
    long countVisibleSightingMatches(@Param("sightingId") Long sightingId, @Param("version") String version);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "notice")
    @Query(value = "select m" + VISIBLE + PINNED + "m.similarity desc, m.id asc", countQuery = "select count(m)" + VISIBLE)
    Page<Match> visibleBySimilarity(@Param("petId") Long petId, @Param("today") LocalDate today, Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "notice")
    @Query(value = "select m" + VISIBLE + PINNED + "case when n.noticeEndDate is null then 1 else 0 end, n.noticeEndDate asc, m.similarity desc, m.id asc", countQuery = "select count(m)" + VISIBLE)
    Page<Match> visibleByDeadline(@Param("petId") Long petId, @Param("today") LocalDate today, Pageable pageable);

    /**
     * BE-2(F-04 후보 목록)에서 쓰라고 만들어 둔 조회.
     * "아니에요"(NO) 는 빼고, "맞아요"(YES) 를 맨 위에, 나머지는 유사도 높은 순.
     * 사용: findVisibleByLostPetId(lostPetId, MatchFeedback.NO, MatchFeedback.YES)
     */
    @Query("""
            select m from Match m
            where m.lostPet.id = :lostPetId
              and m.feedback <> :hidden
            order by case when m.feedback = :pinned then 0 else 1 end, m.similarity desc
            """)
    List<Match> findVisibleByLostPetId(@Param("lostPetId") Long lostPetId,
                                       @Param("hidden") MatchFeedback hidden,
                                       @Param("pinned") MatchFeedback pinned);
}
