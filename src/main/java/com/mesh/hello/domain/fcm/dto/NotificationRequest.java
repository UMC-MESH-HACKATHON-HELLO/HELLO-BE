package com.mesh.hello.domain.fcm.dto;

public record NotificationRequest(
        String title,
        String body
) {}
