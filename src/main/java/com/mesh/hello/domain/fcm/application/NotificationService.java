package com.mesh.hello.domain.fcm.application;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.mesh.hello.domain.fcm.dto.NotificationRequest;
import com.mesh.hello.domain.fcm.entity.FcmToken;
import com.mesh.hello.domain.fcm.repository.FcmTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final FcmTokenRepository fcmTokenRepository;

    private void send(
            Long userId,
            NotificationRequest request
    ) {
        List<FcmToken> tokens = fcmTokenRepository.findByUserId(userId);

        for (FcmToken token : tokens) {
            Message message =
                    Message.builder()
                            .setToken(token.getToken())
                            .setNotification(
                                    Notification.builder()
                                            .setTitle(request.title())
                                            .setBody(request.body())
                                            .build()
                            )
                            .build();

            try {
                FirebaseMessaging.getInstance()
                        .send(message);
            } catch (FirebaseMessagingException e) {
                e.printStackTrace();
            }
        }
    }
}
