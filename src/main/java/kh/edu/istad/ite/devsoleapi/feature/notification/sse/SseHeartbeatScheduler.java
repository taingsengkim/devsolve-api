package kh.edu.istad.ite.devsoleapi.feature.notification.sse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SseHeartbeatScheduler {

    private final SseEmitterService sseEmitterService;

    @Scheduled(fixedRate = 30_000)
    public void heartbeat() {
        log.trace("Sending SSE heartbeat");
        sseEmitterService.sendHeartbeat();
    }
}
