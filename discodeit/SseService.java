@Slf4j
@Service
@RequiredArgsConstructor
public class SseService {

  private static final Long DEFAULT_TIMEOUT = 1000 * 60 * 60L;

  private final SseEmitterRepository emitterRepository;
  private final SseMessageRepository messageRepository;
  private final ObjectMapper objectMapper;

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
    for (UUID receiverId : receiverIds) {
      messageRepository.save(new SseMessageRepository.SseMessage(eventId, receiverId, eventName, data));
      emitterRepository.findAllByUserId(receiverId)
          .forEach(emitter -> sendToEmitter(emitter, eventId, eventName, data));
    }
  }

  public void broadcast(String eventName, Object data) {
    emitterRepository.findAll().keySet().forEach(userId -> send(List.of(userId), eventName, data));
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

      log.info("[SSE] 전송 성공: {}", eventName);
    } catch (Exception e) {
      log.warn("[SSE] 연결 끊김 또는 전송 실패: {}", e.getMessage());
    }
  }
}