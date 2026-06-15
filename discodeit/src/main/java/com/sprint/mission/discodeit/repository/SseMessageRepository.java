package com.sprint.mission.discodeit.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.stereotype.Repository;

@Repository
public class SseMessageRepository {

  private static final int MAX_CACHE_SIZE = 1000;

  private final ConcurrentLinkedDeque<UUID> eventIdQueue = new ConcurrentLinkedDeque<>();
  private final Map<UUID, SseMessage> messages = new ConcurrentHashMap<>();

  public record SseMessage(UUID eventId, UUID receiverId, String eventName, Object data) {}

  public void save(SseMessage message) {
    eventIdQueue.addLast(message.eventId());
    messages.put(message.eventId(), message);

    if (eventIdQueue.size() > MAX_CACHE_SIZE) {
      UUID oldestId = eventIdQueue.pollFirst();
      if (oldestId != null) {
        messages.remove(oldestId);
      }
    }
  }

  public List<SseMessage> findAllSince(UUID receiverId, UUID lastEventId) {
    List<SseMessage> missedMessages = new ArrayList<>();
    boolean isAfterLastEvent = false;

    for (UUID eventId : eventIdQueue) {
      if (isAfterLastEvent) {
        SseMessage msg = messages.get(eventId);
        if (msg != null && msg.receiverId().equals(receiverId)) {
          missedMessages.add(msg);
        }
      }
      if (eventId.equals(lastEventId)) {
        isAfterLastEvent = true;
      }
    }
    return missedMessages;
  }
}
