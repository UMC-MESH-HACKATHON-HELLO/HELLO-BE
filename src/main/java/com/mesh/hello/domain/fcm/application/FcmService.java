package com.mesh.hello.domain.fcm.application;

import com.mesh.hello.domain.fcm.entity.FcmToken;
import com.mesh.hello.domain.fcm.repository.FcmTokenRepository;
import com.mesh.hello.domain.user.domain.User;
import com.mesh.hello.domain.user.repository.UserRepository;
import com.mesh.hello.global.common.exception.BusinessException;
import com.mesh.hello.global.common.response.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FcmService {

    private final FcmTokenRepository fcmTokenRepository;
    private final UserRepository userRepository;

    @Transactional
    public void saveToken(String username, String token) {
        if (fcmTokenRepository.findByToken(token).isPresent()) {
            return;
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_SESSION));  // 무슨 에러 내야할지 모르겟다

        FcmToken fcmToken = new FcmToken();
        fcmToken.setUserId(user.getId());
        fcmToken.setToken(token);

        fcmTokenRepository.save(fcmToken);
    }

    @Transactional
    public void deleteToken(
            String username
    ) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_SESSION));

        fcmTokenRepository.findByUserId(user.getId())
                .forEach(FcmToken::softDelete);
    }
}
