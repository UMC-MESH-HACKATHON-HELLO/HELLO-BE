package com.mesh.hello.domain.matching.repository;

import com.mesh.hello.domain.matching.domain.MatchingRoom;

import java.util.Optional;

/**
 * 매칭으로 생성된 통화방(MatchingRoom)의 상태 저장소.
 *
 * <p>대기열을 다루는 {@link MatchingQueueRepository}와 책임을 분리한다.
 * 여기서는 "이미 매칭된 방"을 roomId / sessionId 양쪽 키로 조회·삭제한다.</p>
 */
public interface MatchingRoomRepository {

    /** 방을 저장한다. 참가자 2명(sessionId)으로도 역조회가 가능해야 한다. */
    void save(MatchingRoom room);

    Optional<MatchingRoom> findByRoomId(String roomId);

    /** 참가자 sessionId로 그가 속한 방을 찾는다. */
    Optional<MatchingRoom> findBySessionId(String sessionId);

    /** 방과 그 참가자 인덱스를 모두 제거하고, 제거된 방을 반환한다(이미 제거됐다면 empty). */
    Optional<MatchingRoom> deleteByRoomId(String roomId);
}