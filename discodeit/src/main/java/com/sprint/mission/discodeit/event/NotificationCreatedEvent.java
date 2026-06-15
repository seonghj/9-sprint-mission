package com.sprint.mission.discodeit.event;

import com.sprint.mission.discodeit.dto.data.NotificationDto;

public record NotificationCreatedEvent(
    NotificationDto notificationDto
) {}
