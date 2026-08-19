package com.mesh.hello.domain.stt.application;

/**
 * STT로 인식된 발화에서 금지어가 감지됐을 때 발행되는 이벤트.
 *
 * <p>{@code stt} 도메인이 {@code matching} 도메인을 직접 참조하지 않도록(반대 방향 의존은
 * {@link TranscribeService}가 이미 가지고 있음) 강제 종료 처리는 이 이벤트를 구독하는
 * 쪽(MatchingService)에서 수행한다.</p>
 */
public record ForbiddenWordDetectedEvent(
        String roomId,
        String sessionId,
        String role,
        String matchedWord,
        String utterance
) {
}
