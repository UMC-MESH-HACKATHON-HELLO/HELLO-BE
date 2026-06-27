package com.mesh.hello.global.websocket.ping;

import java.security.Principal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

/**
 * 연결 계층 검증용 최소 핑/에코.
 *
 * <p>클라이언트가 {@code /app/ping}으로 SEND하면, 호출자에게만
 * {@code /user/queue/pong}으로 본인 sessionId를 에코한다.
 * 이를 통해 (1) 연결, (2) 익명 Principal 등록, (3) 개인 큐 라우팅이 동시에 검증된다.</p>
 *
 * <p>매칭·통화·토큰 관련 목적지는 의도적으로 두지 않는다(다음 단계 소관).</p>
 */
@Slf4j
@Controller
public class PingController {

    /**
     * {@link SendToUser}는 결과를 {@code /user/{principalName}/queue/pong}으로 보낸다.
     * principalName = sessionId 이므로 호출자 본인에게만 도달한다.
     */
    @MessageMapping("/ping")
    @SendToUser("/queue/pong")
    public PongMessage ping(Principal principal) {
        String sessionId = principal != null ? principal.getName() : "unknown";
        log.debug("ping 수신: sessionId={}", sessionId);
        return new PongMessage(sessionId);
    }

    public record PongMessage(String sessionId) {
    }
}
