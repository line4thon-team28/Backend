package com.pawpose.domain.match;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MatchRepository extends JpaRepository<Match, Long> {

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
