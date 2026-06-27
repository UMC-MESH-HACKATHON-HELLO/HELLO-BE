package com.mesh.hello.domain.user.application;

import com.mesh.hello.domain.user.domain.User;
import com.mesh.hello.domain.user.dto.HelperInfoResponse;
import com.mesh.hello.domain.user.repository.UserRepository;
import com.mesh.hello.global.common.exception.BusinessException;
import com.mesh.hello.global.common.response.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /** 로그인된 도우미 본인 정보 조회(인증 principal의 username 기준). */
    @Transactional(readOnly = true)
    public HelperInfoResponse getMyInfo(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        return new HelperInfoResponse(user.getId(), user.getUsername(), user.getNickname(), user.getPoints());
    }
}
