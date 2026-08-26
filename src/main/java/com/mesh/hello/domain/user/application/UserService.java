package com.mesh.hello.domain.user.application;

import com.mesh.hello.domain.auth.application.KakaoOAuthService;
import com.mesh.hello.domain.user.domain.User;
import com.mesh.hello.domain.user.dto.ChangePasswordRequest;
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
     * 로그인된 도우미 본인의 비밀번호를 변경한다.
     *
     * <p>검증·처리 순서에 이유가 있다:
     * <ol>
     *   <li>활성 계정 조회 — 없으면 {@link ErrorCode#UNAUTHORIZED}.</li>
     *   <li><b>소셜 계정 차단</b> — LOCAL이 아니면 {@link ErrorCode#SOCIAL_ACCOUNT_PASSWORD_CHANGE_NOT_ALLOWED}.
     *       현재 비밀번호 검사(3)보다 먼저 해야, 비밀번호가 UUID의 BCrypt 해시라 사용자가 알 수 없는
     *       카카오 계정에 "현재 비밀번호가 틀렸다"는 잘못된 안내를 하지 않는다.</li>
     *   <li>현재 비밀번호 일치 확인 — 불일치면 {@link ErrorCode#PASSWORD_MISMATCH}.</li>
     *   <li><b>새 비밀번호가 현재와 동일한지</b> — 동일하면 {@link ErrorCode#SAME_AS_CURRENT_PASSWORD}.
     *       반드시 현재 비밀번호 확인(3) 뒤에 둔다. 앞에 두면 현재 비밀번호를 모르는 사람이 임의의
     *       문자열을 넣어 그게 이 계정의 비밀번호인지 떠볼 수 있다.</li>
     *   <li>새 비밀번호를 BCrypt로 인코딩해 도메인 메서드로 반영. 명시적 save는 하지 않는다
     *       (트랜잭션 커밋 시 JPA 변경 감지로 저장, {@code updatedAt}은 {@code BaseEntity}가 갱신).</li>
     * </ol>
     *
     * @param username 현재 로그인 세션의 username (Principal.getName() 값)
     * @param request  현재/새 비밀번호
     */
    @Transactional
    public void changePasswordByUsername(String username, ChangePasswordRequest request) {
        // 1) 활성 계정 조회
        User user = userRepository.findByUsernameAndDeletedFalse(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        // 2) 소셜 계정 차단 — 현재 비밀번호 검사보다 먼저(카카오 계정에 잘못된 안내 방지)
        if (user.getProvider() != Provider.LOCAL) {
            throw new BusinessException(ErrorCode.SOCIAL_ACCOUNT_PASSWORD_CHANGE_NOT_ALLOWED);
        }

        // 3) 현재 비밀번호 일치 확인
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }

        // 4) 새 비밀번호가 현재와 동일한지 — 반드시 현재 비밀번호 확인(3) 뒤에서 검사(떠보기 방지)
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.SAME_AS_CURRENT_PASSWORD);
        }

        // 5) 반영 — 명시적 save 금지(더티 체킹). updatedAt은 BaseEntity가 갱신한다.
        user.changePassword(passwordEncoder.encode(request.newPassword()));
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
