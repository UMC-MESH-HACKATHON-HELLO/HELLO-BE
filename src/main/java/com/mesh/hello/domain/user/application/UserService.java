package com.mesh.hello.domain.user.application;

import com.mesh.hello.domain.auth.application.KakaoOAuthService;
import com.mesh.hello.domain.user.domain.User;
import com.mesh.hello.domain.user.dto.HelperInfoResponse;
import com.mesh.hello.domain.user.enums.Provider;
import com.mesh.hello.domain.user.repository.UserRepository;
import com.mesh.hello.global.common.exception.BusinessException;
import com.mesh.hello.global.common.response.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final KakaoOAuthService kakaoOAuthService;

    /** 로그인된 도우미 본인 정보 조회(인증 principal의 username 기준). */
    @Transactional(readOnly = true)
    public HelperInfoResponse getMyInfo(String username) {
        User user = userRepository.findByUsernameAndDeletedFalse(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        return new HelperInfoResponse(user.getId(), user.getUsername(), user.getNickname(), user.getPoints());
    }

    /**
     * username 기반 회원 탈퇴 — 컨트롤러에서 Principal.getName()으로 호출하는 진입점.
     *
     * <p>username → userId 변환 후 {@link #withdraw(Long)}에 위임한다.
     * deleted = true인 계정은 {@code findByUsernameAndDeletedFalse}가 empty를 반환하므로
     * {@link ErrorCode#UNAUTHORIZED}를 던진다. 이후 {@link #withdraw}에서
     * {@link ErrorCode#ALREADY_WITHDRAWN}을 추가로 방어한다.</p>
     *
     * @param username 현재 로그인 세션의 username (Principal.getName() 값)
     */
    @Transactional
    public void withdrawByUsername(String username) {
        User user = userRepository.findByUsernameAndDeletedFalse(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        withdraw(user.getId());
    }

    /**
     * 회원 탈퇴 처리 (소프트 삭제 + 개인정보 익명화).
     *
     * <p>탈퇴 순서:
     * <ol>
     *   <li>활성 계정 조회 — 탈퇴 처리된 계정이면 {@link ErrorCode#ALREADY_WITHDRAWN} 예외</li>
     *   <li>카카오 계정인 경우 unlink 호출 — 반드시 익명화({@link User#withdraw}) 이전에 실행.
     *       익명화 후에는 providerId가 null이 되어 unlink 대상을 식별할 수 없다.</li>
     *   <li>익명화용 비밀번호 생성 — UUID 기반 예측 불가 값을 BCrypt로 인코딩</li>
     *   <li>{@link User#withdraw(String)} 호출 — username·nickname·email·providerId 익명화, deleted = true</li>
     *   <li>JPA 변경 감지로 자동 저장 (트랜잭션 커밋 시점)</li>
     * </ol>
     * </p>
     *
     * <p>⚠️ 설계 결정 (A안): unlink HTTP 호출이 트랜잭션 안에 포함된다.
     * W1 자가 점검 ④("트랜잭션 안에 외부 호출 없음")가 여기서 의도적으로 깨진다.
     * connect {@code 2}초 / read {@code 3}초의 짧은 타임아웃으로 트랜잭션 점유 시간을 제한했다.
     * unlink 실패(타임아웃·4xx·5xx 포함)는 탈퇴 흐름을 막지 않으며 log.warn으로만 기록한다.</p>
     *
     * @param userId 탈퇴시킬 사용자 PK
     */
    @Transactional
    public void withdraw(Long userId) {
        // 1) 활성 계정 조회 — deleted = true이면 findByIdAndDeletedFalse가 empty 반환
        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ALREADY_WITHDRAWN));

        // 2) 카카오 계정인 경우 unlink 호출 (익명화 이전 — providerId가 살아 있는 상태에서 호출)
        if (Provider.KAKAO.equals(user.getProvider())) {
            try {
                kakaoOAuthService.unlink(user.getProviderId());
                log.info("카카오 unlink 완료: userId={}", userId);
            } catch (Exception e) {
                // unlink 실패는 탈퇴를 막지 않는다. 카카오 장애·타임아웃·4xx·5xx 모두 여기서 흡수.
                // providerId(카카오 회원번호)는 개인정보이므로 로그에 남기지 않는다.
                log.warn("카카오 unlink 실패 (탈퇴는 계속 진행): userId={}, reason={}", userId, e.getMessage());
            }
        }

        // 3) 익명화용 비밀번호 생성 — UUID 기반의 예측 불가한 값을 BCrypt로 인코딩
        //    null·빈 문자열이 아닌 값을 사용해야 로그인 검증 로직과의 불일치를 방지한다.
        String anonymizedPassword = passwordEncoder.encode(UUID.randomUUID().toString());

        // 4) 도메인 메서드로 개인정보 익명화 및 탈퇴 상태 전환
        user.withdraw(anonymizedPassword);

        // 5) 명시적 save 생략 — @Transactional 범위 안에서 JPA 변경 감지가 처리함

        // 6) 탈퇴 완료 로그 — userId만 기록하고 username·email 등 개인정보는 남기지 않음
        log.info("회원 탈퇴 완료: userId={}", userId);
    }
}
