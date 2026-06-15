package com.sprint.mission.discodeit.service;

import com.sprint.mission.discodeit.repository.SseEmitterRepository;
import com.sprint.mission.discodeit.repository.SseMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SseService {

  private static final Long DEFAULT_TIMEOUT = 1000 * 60 * 60L;
  private final SseEmitterRepository emitterRepository;
  private final SseMessageRepository messageRepository;

  public SseEmitter connect(UUID receiverId, UUID lastEventId) {
    SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);
    emitterRepository.save(receiverId, emitter);

    emitter.onCompletion(() -> emitterRepository.delete(receiverId, emitter));
    emitter.onTimeout(() -> emitterRepository.delete(receiverId, emitter));
    emitter.onError((e) -> emitterRepository.delete(receiverId, emitter));

    ping(emitter, "connect", "connected!");

    if (lastEventId != null) {
      List<SseMessageRepository.SseMessage> missedMessages =
          messageRepository.findAllSince(receiverId, lastEventId);

      for (SseMessageRepository.SseMessage msg : missedMessages) {
        sendToEmitter(receiverId, emitter, msg.eventId(), msg.eventName(), msg.data());
      }
    }

    return emitter;
  }

  public void send(Collection<UUID> receiverIds, String eventName, Object data) {
    UUID eventId = UUID.randomUUID();

    for (UUID receiverId : receiverIds) {
      messageRepository.save(new SseMessageRepository.SseMessage(eventId, receiverId, eventName, data));

      List<SseEmitter> emitters = emitterRepository.findAllByUserId(receiverId);
      for (SseEmitter emitter : emitters) {
        sendToEmitter(receiverId, emitter, eventId, eventName, data);
      }
    }
  }

  public void broadcast(String eventName, Object data) {
    emitterRepository.findAll().keySet().forEach(userId ->
        send(List.of(userId), eventName, data)
    );
  }

  @Scheduled(fixedDelay = 1000 * 60 * 30)
  public void cleanUp() {
    emitterRepository.findAll().forEach((userId, emitters) -> {
      emitters.forEach(emitter -> {
        if (!ping(emitter, "ping", "heartbeat")) {
          emitterRepository.delete(userId, emitter);
        }
      });
    });
    log.info("SSE 좀비 커넥션 정리 완료");
  }

  private boolean ping(SseEmitter sseEmitter, String eventName, Object data) {
    try {
      sseEmitter.send(SseEmitter.event()
          .name(eventName)
          .data(data));
      return true;
    } catch (IOException | IllegalStateException e) {
      return false;
    }
  }

  private void sendToEmitter(UUID receiverId, SseEmitter emitter, UUID eventId, String eventName, Object data) {
    try {
      emitter.send(SseEmitter.event()
          .id(eventId.toString())
          .name(eventName)
          .data(data));
    } catch (IOException | IllegalStateException e) {
      emitterRepository.delete(receiverId, emitter);
    }
  }
}
