package com.pawpose;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pawpose.domain.auth.kakao.KakaoOAuthClient;
import com.pawpose.domain.auth.kakao.KakaoUserInfo;
import com.pawpose.domain.common.Species;
import com.pawpose.domain.lostpet.LostPet;
import com.pawpose.domain.lostpet.LostPetRepository;
import com.pawpose.domain.match.Match;
import com.pawpose.domain.match.MatchFeedback;
import com.pawpose.domain.match.MatchRepository;
import com.pawpose.domain.notice.Notice;
import com.pawpose.domain.notice.NoticeRepository;
import com.pawpose.domain.notice.NoticeStatus;
import com.pawpose.domain.user.User;
import com.pawpose.domain.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired UserRepository userRepository;
    @Autowired NoticeRepository noticeRepository;
    @Autowired LostPetRepository lostPetRepository;
    @Autowired MatchRepository matchRepository;

    @MockitoBean KakaoOAuthClient kakaoOAuthClient;

    // ---------- F-01 ----------

    @Test
    void 가입하면_토큰이_나오고_그_토큰으로_보호API가_통과한다() throws Exception {
        String token = signup(uniqueEmail(), "password1234", "민지");

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("민지"))
                .andExpect(jsonPath("$.message").doesNotExist());
    }

    @Test
    void 중복이메일과_짧은비밀번호에_안내문구가_뜬다() throws Exception {
        String email = uniqueEmail();
        signup(email, "password1234", "민지");

        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(json(email, "password1234", "민지")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_DUPLICATED"))
                .andExpect(jsonPath("$.message").value("이미 가입된 이메일이에요"));

        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(json(uniqueEmail(), "short", "민지")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("비밀번호는 8~64자로 입력해주세요"));
    }

    @Test
    void 이메일_100자_초과와_72바이트_넘는_비밀번호는_400() throws Exception {
        String longEmail = "a".repeat(60) + "@" + "b".repeat(36) + ".com"; // 101자 (형식은 정상)
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(json(longEmail, "password1234", "민지")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("이메일은 100자 이하로 입력해주세요"));

        String koreanPassword = "가".repeat(25); // 25자지만 UTF-8 75바이트
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(json(uniqueEmail(), koreanPassword, "민지")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("비밀번호가 너무 길어요. 조금 더 짧게 입력해주세요"));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + uniqueEmail() + "\",\"password\":\"" + koreanPassword + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
    }

    @Test
    void 비밀번호가_틀리면_어느쪽인지_알려주지_않는다() throws Exception {
        String email = uniqueEmail();
        signup(email, "password1234", "민지");

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"wrongpass1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호를 확인해주세요"));
    }

    @Test
    void 토큰없이_보호API를_부르면_401() throws Exception {
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void 카카오_처음이면_가입되고_두번째는_같은_회원으로_로그인된다() throws Exception {
        String kakaoId = String.valueOf(System.nanoTime());
        given(kakaoOAuthClient.fetchUserInfo("code-1")).willReturn(new KakaoUserInfo(kakaoId, "아주아주긴카카오닉네임입니다"));
        given(kakaoOAuthClient.fetchUserInfo("code-2")).willReturn(new KakaoUserInfo(kakaoId, "아주아주긴카카오닉네임입니다"));

        JsonNode first = kakao("code-1");
        JsonNode second = kakao("code-2");

        assertThat(first.at("/data/newUser").asBoolean()).isTrue();
        assertThat(second.at("/data/newUser").asBoolean()).isFalse();
        assertThat(second.at("/data/userId").asLong()).isEqualTo(first.at("/data/userId").asLong());
        assertThat(first.at("/data/nickname").asText()).hasSize(10);
    }

    // ---------- F-05 ----------

    @Test
    void 공고상세_남은일수와_긴급과_종료배너() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        Notice d7 = noticeRepository.save(notice(today.plusDays(7), NoticeStatus.NOTICE, "02-901-1234"));
        Notice d3 = noticeRepository.save(notice(today.plusDays(3), NoticeStatus.NOTICE, "02-901-1234"));
        Notice returned = noticeRepository.save(notice(today.plusDays(5), NoticeStatus.RETURNED, ""));

        mvc.perform(get("/api/v1/notices/" + d7.getId())) // 비로그인 허용
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deadline.daysLeft").value(7))
                .andExpect(jsonPath("$.data.deadline.urgent").value(false))
                .andExpect(jsonPath("$.data.shelter.tel").value("02-901-1234"));

        mvc.perform(get("/api/v1/notices/" + d3.getId()))
                .andExpect(jsonPath("$.data.deadline.urgent").value(true));

        mvc.perform(get("/api/v1/notices/" + returned.getId()))
                .andExpect(jsonPath("$.data.deadline.closed").value(true))
                .andExpect(jsonPath("$.data.deadline.closedMessage").value("이 공고는 종료되었어요(반환)"))
                .andExpect(jsonPath("$.data.shelter.tel").doesNotExist());

        mvc.perform(get("/api/v1/notices/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTICE_NOT_FOUND"));
    }

    // ---------- F-07 ----------

    @Test
    void 아니에요는_숨겨지고_맞아요는_맨위로_남의후보는_403() throws Exception {
        String email = uniqueEmail();
        String token = signup(email, "password1234", "민지");
        User owner = userRepository.findByEmail(email).orElseThrow();
        LostPet pet = lostPetRepository.save(LostPet.create(owner, Species.CAT));
        LocalDate end = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(7);
        Match high = matchRepository.save(Match.ofNotice(pet, noticeRepository.save(notice(end, NoticeStatus.NOTICE, null)), 92));
        Match mid = matchRepository.save(Match.ofNotice(pet, noticeRepository.save(notice(end, NoticeStatus.NOTICE, null)), 80));
        Match low = matchRepository.save(Match.ofNotice(pet, noticeRepository.save(notice(end, NoticeStatus.NOTICE, null)), 65));

        feedback(token, high.getId(), "no").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feedback").value("NO"));
        feedback(token, low.getId(), "yes").andExpect(status().isOk());

        List<Match> visible = matchRepository.findVisibleByLostPetId(pet.getId(), MatchFeedback.NO, MatchFeedback.YES);
        assertThat(visible).extracting(Match::getId).containsExactly(low.getId(), mid.getId());

        String otherToken = signup(uniqueEmail(), "password1234", "다른사람");
        feedback(otherToken, mid.getId(), "yes")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        feedback(token, mid.getId(), "maybe").andExpect(status().isBadRequest());
    }

    // ---------- helpers ----------

    private org.springframework.test.web.servlet.ResultActions feedback(String token, Long matchId, String result) throws Exception {
        return mvc.perform(post("/api/v1/matches/" + matchId + "/feedback")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"result\":\"" + result + "\"}"));
    }

    private String signup(String email, String password, String nickname) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(json(email, password, nickname)))
                .andExpect(status().isCreated())
                .andReturn();
        return om.readTree(result.getResponse().getContentAsString()).at("/data/accessToken").asText();
    }

    private JsonNode kakao(String code) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/kakao").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return om.readTree(result.getResponse().getContentAsString());
    }

    private static String json(String email, String password, String nickname) {
        return "{\"email\":\"%s\",\"password\":\"%s\",\"nickname\":\"%s\"}".formatted(email, password, nickname);
    }

    private static String uniqueEmail() {
        return UUID.randomUUID().toString().substring(0, 8) + "@test.com";
    }

    private static Notice notice(LocalDate endDate, NoticeStatus status, String tel) {
        return Notice.builder()
                .desertionNo(UUID.randomUUID().toString().substring(0, 20))
                .noticeEndDate(endDate)
                .species(Species.CAT)
                .careName("테스트 보호소")
                .careTel(tel)
                .careAddr("서울 강북구 덕릉로 123")
                .status(status)
                .build();
    }
}
