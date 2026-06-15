package com.sprint.mission.discodeit.controller;

import com.sprint.mission.discodeit.service.SseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/sse")
@RequiredArgsConstructor
public class SseController {

  private final SseService sseService;

  @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter connect(
      @RequestAttribute("userId") UUID userId,
      @RequestHeader(value = "Last-Event-ID", required = false) String lastEventIdString
  ) {
    UUID lastEventId = null;
    if (lastEventIdString != null && !lastEventIdString.isEmpty()) {
      try {
        lastEventId = UUID.fromString(lastEventIdString);
      } catch (IllegalArgumentException e) {

      }
    }

    return sseService.connect(userId, lastEventId);
  }
}
