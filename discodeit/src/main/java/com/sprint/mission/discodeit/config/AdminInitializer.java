package com.sprint.mission.discodeit.config;

import com.sprint.mission.discodeit.entity.Role;
import com.sprint.mission.discodeit.entity.User;
import com.sprint.mission.discodeit.redis.RedisLockProvider;
import com.sprint.mission.discodeit.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final RedisLockProvider redisLockProvider;

  @Value("${admin.username}") private String adminUsername;
  @Value("${admin.password}") private String adminPassword;
  @Value("${admin.email}") private String adminEmail;

  @Override
  public void run(ApplicationArguments args) {
    if (!StringUtils.hasText(adminUsername)
        || !StringUtils.hasText(adminPassword)
        || !StringUtils.hasText(adminEmail)) {
      log.warn("어드민 계정 설정이 누락되어 생성을 건너뜁니다.");
      return;
    }

    String lockKey = "admin-init-lock";

    try {
      redisLockProvider.acquireLock(lockKey);

      if (!userRepository.existsByRole(Role.ADMIN)) {
        User admin = new User(
            adminUsername,
            passwordEncoder.encode(adminPassword),
            adminEmail,
            null
        );
        admin.updateRole(Role.ADMIN);

        userRepository.saveAndFlush(admin);
        log.info("초기 어드민 계정 생성 완료 (username: admin)");
      }

    } catch (RedisLockProvider.RedisLockAcquisitionException e) {
      log.info("다른 인스턴스에서 이미 어드민 계정을 생성 중이므로 패스합니다.");
    } catch (Exception e) {
      log.error("어드민 계정 생성 중 예기치 않은 오류 발생", e);
    } finally {
      try {
        redisLockProvider.releaseLock(lockKey);
      } catch (Exception e) {

      }
    }
  }
}
