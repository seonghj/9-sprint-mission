package com.sprint.mission.discodeit.event.listener;

import com.sprint.mission.discodeit.dto.data.BinaryContentDto;
import com.sprint.mission.discodeit.dto.data.ChannelDto;
import com.sprint.mission.discodeit.dto.data.NotificationDto;
import com.sprint.mission.discodeit.dto.data.UserDto;
import com.sprint.mission.discodeit.event.BinaryContentUpdatedEvent;
import com.sprint.mission.discodeit.event.ChannelEvent;
import com.sprint.mission.discodeit.event.NotificationCreatedEvent;
import com.sprint.mission.discodeit.event.UserEvent;
import com.sprint.mission.discodeit.service.SseService;
import com.sprint.mission.discodeit.type.ChannelType;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SseEventListener {

  private final SseService sseService;

  @EventListener
  public void handleNotificationCreated(NotificationCreatedEvent event) {
    NotificationDto dto = event.notificationDto();

    sseService.send(
        List.of(dto.receiverId()),
        "notifications.created",
        dto
    );
    log.info("SSE 발송: notifications.created -> user: {}", dto.receiverId());
  }

  @EventListener
  public void handleBinaryContentUpdated(BinaryContentUpdatedEvent event) {
    BinaryContentDto dto = event.binaryContentDto();

    sseService.send(
        List.of(event.uploaderId()),
        "binaryContents.updated",
        dto
    );
    log.info("SSE 발송: binaryContents.updated -> contentId: {}", dto.id());
  }

  @EventListener
  public void handleChannelEvent(ChannelEvent event) {
    ChannelDto dto = event.channelDto();

    String eventName = "channels." + event.type().name().toLowerCase();

    if (dto.type() == ChannelType.PUBLIC) {
      sseService.broadcast(eventName, dto);
    } else {
      sseService.send(dto.participants().stream()
          .map(UserDto::id).collect(Collectors.toSet()), eventName, dto);
    }
    log.info("SSE 발송: {} -> channelId: {}", eventName, dto.id());
  }

  @EventListener
  public void handleUserEvent(UserEvent event) {
    UserDto dto = event.userDto();

    String eventName = "users." + event.type().name().toLowerCase();

    sseService.broadcast(eventName, dto);

    log.info("SSE 발송: {} -> userId: {}", eventName, dto.id());
  }
}
