package com.pawpose.global.init;

import com.pawpose.domain.common.Sex;
import com.pawpose.domain.common.Species;
import com.pawpose.domain.lostpet.LostPet;
import com.pawpose.domain.lostpet.LostPetRepository;
import com.pawpose.domain.match.Match;
import com.pawpose.domain.match.MatchRepository;
import com.pawpose.domain.notice.Notice;
import com.pawpose.domain.notice.NoticeRepository;
import com.pawpose.domain.notice.NoticeStatus;
import com.pawpose.domain.user.User;
import com.pawpose.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * local 프로필에서만 실행되는 테스트 데이터. (H2 메모리 DB 라 서버 끌 때마다 초기화됨)
 * 로그인: demo@pawpose.com / password1234
 * 공고 1 = D-7 일반, 공고 2 = D-2 긴급, 공고 3 = 반환 종료
 * match 1, 2 = demo 계정의 실종 등록(보리) 후보
 */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class LocalDataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final NoticeRepository noticeRepository;
    private final LostPetRepository lostPetRepository;
    private final MatchRepository matchRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        User demo = userRepository.save(User.local("demo@pawpose.com", passwordEncoder.encode("password1234"), "민지"));

        Notice normal = noticeRepository.save(notice("448000202600231", "서울-강북-2026-00231", today.plusDays(7),
                "강북구 동물보호센터", "02-901-1234", "서울 강북구 덕릉로 123", NoticeStatus.NOTICE));
        Notice urgent = noticeRepository.save(notice("448000202600198", "서울-동대문-2026-00198", today.plusDays(2),
                "동대문구 동물보호센터", "02-123-4567", "서울 동대문구 장안동 1", NoticeStatus.NOTICE));
        noticeRepository.save(notice("448000202600150", "서울-성북-2026-00150", today.plusDays(5),
                "성북구 동물보호센터", null, "서울 성북구 정릉로 1", NoticeStatus.RETURNED));

        LostPet bori = lostPetRepository.save(LostPet.create(demo, Species.CAT));
        matchRepository.save(Match.ofNotice(bori, normal, 92));
        matchRepository.save(Match.ofNotice(bori, urgent, 71));

        log.info("[local] 테스트 데이터 준비 완료. demo@pawpose.com / password1234");
    }

    private Notice notice(String desertionNo, String noticeNo, LocalDate endDate,
                          String careName, String careTel, String careAddr, NoticeStatus status) {
        return Notice.builder()
                .desertionNo(desertionNo)
                .noticeNo(noticeNo)
                .noticeStartDate(endDate.minusDays(10))
                .noticeEndDate(endDate)
                .happenDate(endDate.minusDays(10))
                .happenPlace("주택가 골목")
                .species(Species.CAT)
                .breed("코리안 숏헤어")
                .color("치즈")
                .sex(Sex.FEMALE)
                .age("2024(년생)")
                .weight("3.8(Kg)")
                .specialMark("붉은색 목줄 착용, 사람을 잘 따름")
                .careName(careName)
                .careTel(careTel)
                .careAddr(careAddr)
                .orgName("서울특별시")
                .imageUrl("https://placehold.co/600x600?text=cat")
                .status(status)
                .build();
    }
}
