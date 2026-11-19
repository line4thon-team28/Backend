package com.pawpose.domain.notification;

import com.pawpose.domain.common.Species;
import com.pawpose.domain.lostpet.LostPet;
import com.pawpose.domain.lostpet.LostPetRepository;
import com.pawpose.domain.match.Match;
import com.pawpose.domain.match.MatchFeedback;
import com.pawpose.domain.match.MatchRepository;
import com.pawpose.domain.match.pipeline.CandidateNotification;
import com.pawpose.domain.match.pipeline.CandidateNotificationRepository;
import com.pawpose.domain.notice.Notice;
import com.pawpose.domain.notice.NoticeRepository;
import com.pawpose.domain.notice.NoticeStatus;
import com.pawpose.domain.user.User;
import com.pawpose.domain.user.UserRepository;
import com.pawpose.global.security.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 스케줄러는 끄고 deliverPending 을 직접 호출해서 결과를 확인합니다. */
@SpringBootTest(properties = "pawpose.notification.enabled=false")
@AutoConfigureMockMvc
class NotificationTest {

    @Autowired NotificationService service;
    @Autowired NotificationRepository notifications;
    @Autowired CandidateNotificationRepository outbox;
    @MockitoSpyBean MatchRepository matches;
    @Autowired LostPetRepository pets;
    @Autowired NoticeRepository notices;
    @Autowired UserRepository users;
    @Autowired JwtProvider jwt;
    @Autowired MockMvc mvc;

    private User owner;
    private LostPet pet;

    @BeforeEach
    void setup() {
        owner = users.save(User.local(UUID.randomUUID() + "@test.com", "unused", "보호자"));
        pet = pets.save(LostPet.create(owner, Species.DOG));
    }

    /** 매칭(F-04)이 새 후보를 만들 때와 같은 상태: match 저장 + outbox 한 줄 */
    private Match newCandidate(double similarity) {
        Match match = matches.save(Match.ofSighting(pet, System.nanoTime(), similarity));
        outbox.save(CandidateNotification.create(match.getId(), owner.getId(), pet.getId()));
        return match;
    }

    /** 공고 후보: match + outbox */
    private Match noticeCandidate(LocalDate endDate, NoticeStatus status) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        Notice notice = notices.save(Notice.builder().desertionNo(UUID.randomUUID().toString().substring(0, 25))
                .species(Species.DOG).noticeStartDate(today.minusDays(3)).noticeEndDate(endDate).happenDate(today.minusDays(3))
                .imageUrl("https://www.animal.go.kr/test.png").orgName("서울특별시 강북구").status(status).build());
        Match match = matches.save(Match.ofNotice(pet, notice, 80));
        outbox.save(CandidateNotification.create(match.getId(), owner.getId(), pet.getId()));
        return match;
    }

    private List<Notification> mine() {
        return notifications.findAll().stream().filter(n -> n.isOwnedBy(owner.getId())).toList();
    }

    private boolean delivered(Match match) {
        return outbox.findAll().stream().filter(r -> r.getMatchId().equals(match.getId()))
                .allMatch(CandidateNotification::isDelivered);
    }

    private String token(User user) {
        return "Bearer " + jwt.createAccessToken(user.getId());
    }

    @Test
    void 새_후보가_생기면_알림이_한번만_생기고_outbox는_처리완료된다() {
        Match match = newCandidate(72.5);

        service.deliverPending();
        service.deliverPending();

        assertThat(mine()).hasSize(1);
        Notification n = mine().get(0);
        assertThat(n.getMatchId()).isEqualTo(match.getId());
        assertThat(n.getLostPetId()).isEqualTo(pet.getId());
        assertThat(n.getSimilarity()).isEqualTo(72.5);
        assertThat(n.isRead()).isFalse();
        assertThat(outbox.findAll()).filteredOn(r -> r.getMatchId().equals(match.getId()))
                .allMatch(CandidateNotification::isDelivered);
    }

    @Test
    void 찾음_처리된_등록은_알림을_만들지_않는다() {
        Match match = newCandidate(80);
        pet.markFound();
        pets.save(pet);

        service.deliverPending();

        assertThat(mine()).isEmpty();
        assertThat(outbox.findAll()).filteredOn(r -> r.getMatchId().equals(match.getId()))
                .allMatch(CandidateNotification::isDelivered);
    }

    @Test
    void 아니에요로_숨긴_후보는_알림을_만들지_않는다() {
        Match match = newCandidate(85);
        match.applyFeedback(MatchFeedback.NO);
        matches.save(match);

        service.deliverPending();

        assertThat(mine()).isEmpty();
        assertThat(delivered(match)).isTrue();
    }

    @Test
    void 마감됐거나_종료된_공고_후보는_알림을_만들지_않는다() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        Match open = noticeCandidate(today.plusDays(3), NoticeStatus.NOTICE);
        Match expired = noticeCandidate(today.minusDays(1), NoticeStatus.NOTICE);
        Match adopted = noticeCandidate(today.plusDays(3), NoticeStatus.ADOPTED);

        service.deliverPending();

        assertThat(mine()).extracting(Notification::getMatchId).containsExactly(open.getId());
        assertThat(delivered(expired)).isTrue();
        assertThat(delivered(adopted)).isTrue();
    }

    @Test
    void 한_건이_실패해도_나머지는_알림이_생기고_실패한_건은_다음에_다시_시도한다() {
        Match broken = newCandidate(70);
        Match ok = newCandidate(80);
        doThrow(new RuntimeException("boom")).when(matches).findById(broken.getId());

        service.deliverPending();

        assertThat(mine()).extracting(Notification::getMatchId).containsExactly(ok.getId());
        assertThat(delivered(broken)).isFalse();
        assertThat(delivered(ok)).isTrue();

        reset(matches);
        service.deliverPending();

        assertThat(mine()).extracting(Notification::getMatchId)
                .containsExactlyInAnyOrder(broken.getId(), ok.getId());
        assertThat(delivered(broken)).isTrue();
    }

    @Test
    void 알림_목록과_뱃지와_읽음처리() throws Exception {
        newCandidate(70);
        newCandidate(90);
        service.deliverPending();

        mvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", token(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(2));

        // 최신순: 나중에 생긴 90% 후보가 먼저
        mvc.perform(get("/api/v1/notifications").header("Authorization", token(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.items[0].similarity").value(90.0))
                .andExpect(jsonPath("$.data.items[0].lostPetId").value(pet.getId()))
                .andExpect(jsonPath("$.data.items[0].matchType").value("SIGHTING"))
                .andExpect(jsonPath("$.data.items[0].read").value(false));

        Long firstId = mine().stream().mapToLong(Notification::getId).max().orElseThrow();
        mvc.perform(patch("/api/v1/notifications/" + firstId + "/read").header("Authorization", token(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(true));

        mvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", token(owner)))
                .andExpect(jsonPath("$.data.count").value(1));

        mvc.perform(patch("/api/v1/notifications/read-all").header("Authorization", token(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updatedCount").value(1));

        mvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", token(owner)))
                .andExpect(jsonPath("$.data.count").value(0));
    }

    @Test
    void 다른_사람의_알림은_읽음처리할_수_없다() throws Exception {
        newCandidate(75);
        service.deliverPending();
        Long id = mine().get(0).getId();
        User stranger = users.save(User.local(UUID.randomUUID() + "@test.com", "unused", "남"));

        mvc.perform(patch("/api/v1/notifications/" + id + "/read").header("Authorization", token(stranger)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mvc.perform(patch("/api/v1/notifications/999999999/read").header("Authorization", token(owner)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
    }

    @Test
    void 로그인하지_않으면_알림을_볼_수_없다() throws Exception {
        mvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 잘못된_페이지_크기는_입력오류() throws Exception {
        mvc.perform(get("/api/v1/notifications").param("size", "0").header("Authorization", token(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
}
