package com.mesh.hello.domain.matching.domain;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

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

    // 통화 종료(플러시) 절차가 시작됐는지 여부. true가 된 이후로는 새 STT 세션 시작을 거부한다.
    private final AtomicBoolean closing = new AtomicBoolean(false);

    /** 어르신(helpee)이 publish 중인 카메라/화면공유 트랙 상태. 값이 없으면 OFF로 간주한다. */
    private final Map<MediaType, MediaState> mediaStates = new ConcurrentHashMap<>();

    public MatchingRoom(String roomId, String helpeeSessionId, String helperSessionId) {
        this.roomId = roomId;
        this.helpeeSessionId = helpeeSessionId;
        this.helperSessionId = helperSessionId;
        this.matchedAt = LocalDateTime.now();
    }

    /** VC101(카메라로 보여주기)/SC101(내 화면 보여주기)가 다루는 부가 영상 트랙 종류. */
    public enum MediaType {
        CAMERA, SCREEN_SHARE
    }

    /** 부가 영상 트랙의 상태. PERMISSION_DENIED는 권한 거부로 음성 통화만 유지 중임을 뜻한다. */
    public enum MediaState {
        OFF, ON, PERMISSION_DENIED
    }

    /** 주어진 트랙 종류의 현재 상태. 한 번도 갱신된 적 없으면 OFF. */
    public MediaState getMediaState(MediaType type) {
        return mediaStates.getOrDefault(type, MediaState.OFF);
    }

    public void setMediaState(MediaType type, MediaState state) {
        mediaStates.put(type, state);
    }

    /** 종료 절차 시작을 표시한다. 이미 표시돼 있었다면 false. */
    public boolean markClosing() {
        return closing.compareAndSet(false, true);
    }

    public boolean isClosing() {
        return closing.get();
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