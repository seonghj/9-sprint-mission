package com.sprint.mission.discodeit.security;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationChannelInterceptor implements ChannelInterceptor {

  private final JwtTokenProvider jwtTokenProvider;
  private final JwtRegistry jwtRegistry;
  private final UserDetailsService userDetailsService;

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

    if (StompCommand.CONNECT.equals(accessor.getCommand())) {
      try {
        List<String> authorization = accessor.getNativeHeader("Authorization");

        if (authorization != null && !authorization.isEmpty()) {
          String bearerToken = authorization.get(0);

          if (bearerToken.startsWith("Bearer ")) {
            String token = bearerToken.substring(7).trim();

            if (jwtRegistry.hasActiveJwtInformationByAccessToken(token)) {

              String username = jwtTokenProvider.getUsername(token);
              UserDetails userDetails = userDetailsService.loadUserByUsername(username);

              UsernamePasswordAuthenticationToken authentication =
                  new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

              SecurityContextHolder.getContext().setAuthentication(authentication);
              accessor.setUser(authentication);

            } else {
              throw new IllegalArgumentException("Redis에 존재하지 않는 만료된 토큰입니다.");
            }
          }
        } else {
          log.warn("웹소켓 연결 시도 중 Authorization 헤더가 없습니다.");
        }
      } catch (Exception e) {
        log.error("웹소켓 JWT 인증 실패: ", e);
        throw new MessageDeliveryException("웹소켓 인증 에러: " + e.getMessage());
      }
    }
    return message;
  }
}
