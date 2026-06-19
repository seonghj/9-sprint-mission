package com.sprint.mission.discodeit.dto.data;

import java.util.UUID;

public record SseEventPayload(
    UUID eventId,
    UUID receiverId,
    String eventName,
    String data
) {}
