package com.mesh.hello.domain.fcm.application;

import com.mesh.hello.domain.fcm.entity.FcmToken;
import com.mesh.hello.domain.fcm.repository.FcmTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FcmService {

    private final FcmTokenRepository fcmTokenRepository;

    @Transactional
    public void saveToken(Long userId, String token) {
        if (fcmTokenRepository.findByToken(token).isPresent()) {
            return;
        }

        FcmToken fcmToken = new FcmToken();
        fcmToken.setUserId(userId);
        fcmToken.setToken(token);

        fcmTokenRepository.save(fcmToken);
    }
}
