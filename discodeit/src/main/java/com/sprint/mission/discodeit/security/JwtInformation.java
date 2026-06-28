package com.sprint.mission.discodeit.security;

import com.sprint.mission.discodeit.dto.data.UserDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class JwtInformation {
  private UserDto userDto;
  private String accessToken;
  private String refreshToken;

  public void rotate(String newAccessToken, String newRefreshToken) {
    this.accessToken = newAccessToken;
    this.refreshToken = newRefreshToken;
  }
}
