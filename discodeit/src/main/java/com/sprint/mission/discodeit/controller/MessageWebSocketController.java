package com.sprint.mission.discodeit.controller;

import com.sprint.mission.discodeit.dto.request.MessageCreateRequest;
import com.sprint.mission.discodeit.service.MessageService;
import jakarta.validation.Valid;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class MessageWebSocketController {

  private final MessageService messageService;

  @MessageMapping("/messages")
  public void sendMessage(@Payload @Valid MessageCreateRequest request) {

    log.info("웹소켓 메시지 수신: userId={}, channelId={}", request.authorId(), request.channelId());

    messageService.create(request, Collections.emptyList());

    log.debug("웹소켓 메시지 처리 완료: userId={}, channelId={}", request.authorId(), request.channelId());
  }
}