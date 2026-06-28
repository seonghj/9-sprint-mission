package com.sprint.mission.discodeit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprint.mission.discodeit.dto.data.SseEventPayload;
import com.sprint.mission.discodeit.repository.SseEmitterRepository;
import com.sprint.mission.discodeit.repository.SseMessageRepository;
import com.sprint.mission.discodeit.security.JwtRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
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
  private static final String SSE_TOPIC = "sse-events-topic";

  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final SseEmitterRepository emitterRepository;
  private final SseMessageRepository messageRepository;
  private final ObjectMapper objectMapper;
  private final JwtRegistry jwtRegistry;

  public SseEmitter connect(UUID receiverId, UUID lastEventId) {
    emitterRepository.findAllByUserId(receiverId).forEach(emitter -> {
      try {
        emitter.complete();
      } catch (Exception e) {
      }
    });

    SseEmitter emitter = new SseEmitter(0L);
    emitterRepository.save(receiverId, emitter);

    emitter.onCompletion(() -> emitterRepository.delete(receiverId, emitter));
    emitter.onTimeout(() -> emitterRepository.delete(receiverId, emitter));
    emitter.onError((e) -> emitterRepository.delete(receiverId, emitter));

    sendToEmitter(emitter, UUID.randomUUID(), "connect", "connected");

    if (lastEventId != null) {
      messageRepository.findAllSince(receiverId, lastEventId)
          .forEach(msg -> sendToEmitter(emitter, msg.eventId(), msg.eventName(), msg.data()));
    }

    return emitter;
  }

  public void send(Collection<UUID> receiverIds, String eventName, Object data) {
    UUID eventId = UUID.randomUUID();

    String payloadData;
    try {
      payloadData = (data instanceof String) ? (String) data : objectMapper.writeValueAsString(data);
    } catch (Exception e) {
      log.error("SSE 메세지 직렬화 실패", e);
      return;
    }

    for (UUID receiverId : receiverIds) {
      messageRepository.save(new SseMessageRepository.SseMessage(eventId, receiverId, eventName, data));

      SseEventPayload payload = new SseEventPayload(eventId, receiverId, eventName, payloadData);
      kafkaTemplate.send(SSE_TOPIC, payload);
    }
  }


  public void broadcast(String eventName, Object data) {
    List<UUID> activeUserIds = jwtRegistry.getActiveUserIds();

    if (activeUserIds.isEmpty()) {
      return;
    }

    send(activeUserIds, eventName, data);

    log.info("[SSE] 전체 접속자 {}명에게 브로드캐스트 완료: {}", activeUserIds.size(), eventName);
  }


  @KafkaListener(topics = "sse-topic", groupId = "sse-group-#{T(java.util.UUID).randomUUID().toString()}")
  public void consumeSseEvent(SseEventPayload payload) {
    Collection<SseEmitter> localEmitters = emitterRepository.findAllByUserId(payload.receiverId());

    if (localEmitters != null && !localEmitters.isEmpty()) {
      localEmitters.forEach(emitter ->
          sendToEmitter(emitter, payload.eventId(), payload.eventName(), payload.data())
      );
      log.info("[SSE] 카프카 수신 후 로컬 클라이언트 전송 완료: {}", payload.receiverId());
    }
  }

  @Scheduled(fixedDelay = 1000 * 60 * 30)
  public void cleanUp() {
    emitterRepository.findAll().forEach((userId, emitters) -> {
      emitters.forEach(emitter -> {
        if (!ping(emitter)) {
          emitterRepository.delete(userId, emitter);
        }
      });
    });
  }

  private boolean ping(SseEmitter sseEmitter) {
    try {
      sseEmitter.send(SseEmitter.event().comment("heartbeat"));
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  private void sendToEmitter(SseEmitter emitter, UUID eventId, String eventName, Object data) {
    try {
      String payload = (data instanceof String) ? (String) data : objectMapper.writeValueAsString(data);

      emitter.send(SseEmitter.event()
          .id(eventId.toString())
          .name(eventName)
          .data(payload, MediaType.TEXT_EVENT_STREAM));

      log.debug("[SSE] 전송 성공: {}", eventName);
    } catch (Exception e) {
      log.warn("[SSE] 연결 끊김 또는 전송 실패: {}", e.getMessage());
    }
  }
}
