package com.sprint.mission.discodeit.event.listener;

import com.sprint.mission.discodeit.event.MessageCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketRequiredEventListener {

  private final SimpMessagingTemplate messagingTemplate;

  @Async("taskExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleMessageCreatedEvent(MessageCreatedEvent event) {
    try {
      String destination = String.format("/sub/channels.%s.messages", event.channelId());

      messagingTemplate.convertAndSend(destination, event.messageDto());

      log.info("메시지 브로드캐스팅 성공: destination={}, messageId={}", destination, event.messageDto().id());
    } catch (Exception e) {
      log.error("메시지 브로드캐스팅 실패", e);
    }
  }
}
