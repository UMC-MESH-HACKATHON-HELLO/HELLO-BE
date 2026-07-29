package com.mesh.hello.domain.matching.domain;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 매칭 결과 상태(통화방).
 *
 * <p>도움 요청자(helpee)와 도우미(helper) 1:1로 묶인 LiveKit 방 하나를 표현한다.
 * 세션 종료/끊김 시 상대방(counterpart)을 찾아 알림을 보내기 위해 양측 sessionId를 보관한다.</p>
 */
@Getter
public class MatchingRoom {

    private final String roomId;
    private final String helpeeSessionId;
    private final String helperSessionId;
    private final LocalDateTime matchedAt;

    public MatchingRoom(String roomId, String helpeeSessionId, String helperSessionId) {
        this.roomId = roomId;
        this.helpeeSessionId = helpeeSessionId;
        this.helperSessionId = helperSessionId;
        this.matchedAt = LocalDateTime.now();
    }

    /** 해당 sessionId가 이 방의 참가자인지 여부. */
    public boolean contains(String sessionId) {
        return helpeeSessionId.equals(sessionId) || helperSessionId.equals(sessionId);
    }

    /** 주어진 sessionId의 상대방 sessionId. 참가자가 아니면 empty. */
    public Optional<String> counterpartOf(String sessionId) {
        if (helpeeSessionId.equals(sessionId)) {
            return Optional.of(helperSessionId);
        }
        if (helperSessionId.equals(sessionId)) {
            return Optional.of(helpeeSessionId);
        }
        return Optional.empty();
    }

    /** 주어진 sessionId의 역할("helpee"/"helper"). 참가자가 아니면 null. */
    public String roleOf(String sessionId) {
        if (helpeeSessionId.equals(sessionId)) {
            return "helpee";
        }
        if (helperSessionId.equals(sessionId)) {
            return "helper";
        }
        return null;
    }
}