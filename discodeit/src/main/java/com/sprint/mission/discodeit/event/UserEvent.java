package com.sprint.mission.discodeit.event;

import com.sprint.mission.discodeit.dto.data.UserDto;

public record UserEvent(
    DomainEventType type,
    UserDto userDto
) {}