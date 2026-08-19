package com.mesh.hello.domain.user.application;

import com.mesh.hello.domain.user.domain.User;
import com.mesh.hello.domain.user.dto.HelperInfoResponse;
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

    /** 로그인된 도우미 본인 정보 조회(인증 principal의 username 기준). */
    @Transactional(readOnly = true)
    public HelperInfoResponse getMyInfo(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        return new HelperInfoResponse(user.getId(), user.getUsername(), user.getNickname(), user.getPoints());
    }

    /**
     * 회원 탈퇴 처리 (소프트 삭제 + 개인정보 익명화).
     *
     * <p>탈퇴 순서:
     * <ol>
     *   <li>활성 계정 조회 — 탈퇴 처리된 계정이면 {@link ErrorCode#ALREADY_WITHDRAWN} 예외</li>
     *   <li>익명화용 비밀번호 생성 — UUID 기반 예측 불가 값을 BCrypt로 인코딩</li>
     *   <li>{@link User#withdraw(String)} 호출 — username·nickname·email·providerId 익명화, deleted = true</li>
     *   <li>JPA 변경 감지로 자동 저장 (트랜잭션 커밋 시점)</li>
     * </ol>
     * </p>
     *
     * <p>카카오 unlink는 W2 단계에서 별도로 추가한다.</p>
     *
     * @param userId 탈퇴시킬 사용자 PK
     */
    @Transactional
    public void withdraw(Long userId) {
        // 1) 활성 계정 조회 — deleted = true이면 findByIdAndDeletedFalse가 empty 반환
        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ALREADY_WITHDRAWN));

        // 2) 익명화용 비밀번호 생성 — UUID 기반의 예측 불가한 값을 BCrypt로 인코딩
        //    null·빈 문자열이 아닌 값을 사용해야 로그인 검증 로직과의 불일치를 방지한다.
        String anonymizedPassword = passwordEncoder.encode(UUID.randomUUID().toString());

        // 3) 도메인 메서드로 개인정보 익명화 및 탈퇴 상태 전환
        user.withdraw(anonymizedPassword);

        // 4) 명시적 save 생략 — @Transactional 범위 안에서 JPA 변경 감지가 처리함

        // 5) 탈퇴 완료 로그 — userId만 기록하고 username·email 등 개인정보는 남기지 않음
        log.info("회원 탈퇴 완료: userId={}", userId);
    }
}
