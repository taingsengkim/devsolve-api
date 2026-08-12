package kh.edu.istad.ite.devsoleapi.feature.notification.sse;

import kh.edu.istad.ite.devsoleapi.feature.notification.dto.NotificationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class SseEmitterService {

    private final ConcurrentHashMap<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID userId) {
        SseEmitter existingEmitter = emitters.remove(userId);
        if (existingEmitter != null) {
            existingEmitter.complete();
        }

        SseEmitter emitter = new SseEmitter(0L);
        emitter.onCompletion(() -> emitters.remove(userId));
        emitter.onTimeout(() -> emitters.remove(userId));
        emitter.onError(e -> emitters.remove(userId));

        emitters.put(userId, emitter);
        return emitter;
    }

    public void push(UUID userId, NotificationResponse payload) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter != null) {
            try {
                String eventId = payload.id() != null
                        ? payload.id().toString()
                        : "bulk-" + System.currentTimeMillis();
                emitter.send(SseEmitter.event()
                        .id(eventId)
                        .name("notification")
                        .data(payload, MediaType.APPLICATION_JSON));
            } catch (IOException e) {
                log.error("Failed to push notification to user {}", userId, e);
                emitter.completeWithError(e);
                emitters.remove(userId);
            }
        }
    }

    public void pushToMany(Collection<UUID> userIds, NotificationResponse payload) {
        for (UUID userId : userIds) {
            push(userId, payload);
        }
    }

    public void sendHeartbeat() {
        for (ConcurrentHashMap.Entry<UUID, SseEmitter> entry : emitters.entrySet()) {
            UUID userId = entry.getKey();
            SseEmitter emitter = entry.getValue();
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (IOException e) {
                log.error("Failed to send heartbeat to user {}", userId, e);
                emitter.completeWithError(e);
                emitters.remove(userId);
            }
        }
    }
}
