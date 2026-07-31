package com.mesh.hello.domain.matching.repository;

import com.mesh.hello.domain.matching.domain.MatchingRoom;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 인메모리 통화방 저장소.
 *
 * <p>roomId → 방 본체, sessionId → roomId 두 인덱스를 함께 유지해
 * roomId/sessionId 어느 쪽으로도 O(1) 조회가 가능하게 한다.</p>
 */
@Repository
public class InMemoryMatchingRoomRepository implements MatchingRoomRepository {

    private final Map<String, MatchingRoom> roomsById = new ConcurrentHashMap<>();
    private final Map<String, String> roomIdBySession = new ConcurrentHashMap<>();

    @Override
    public void save(MatchingRoom room) {
        roomsById.put(room.getRoomId(), room);
        roomIdBySession.put(room.getHelpeeSessionId(), room.getRoomId());
        roomIdBySession.put(room.getHelperSessionId(), room.getRoomId());
    }

    @Override
    public Optional<MatchingRoom> findByRoomId(String roomId) {
        return Optional.ofNullable(roomsById.get(roomId));
    }

    @Override
    public Optional<MatchingRoom> findBySessionId(String sessionId) {
        return Optional.ofNullable(roomIdBySession.get(sessionId))
                .map(roomsById::get);
    }

    @Override
    public Optional<MatchingRoom> deleteByRoomId(String roomId) {
        MatchingRoom room = roomsById.remove(roomId);
        if (room != null) {
            roomIdBySession.remove(room.getHelpeeSessionId());
            roomIdBySession.remove(room.getHelperSessionId());
        }
        return Optional.ofNullable(room);
    }
}