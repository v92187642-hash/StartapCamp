package ru.otmetka.core.auth;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import ru.otmetka.core.common.TooManyRequestsException;

/** Caps how many accounts one address (client IP) can create. */
@Component
class RateLimits {

    private final StringRedisTemplate redis;
    private final int registerPerIp;
    private final Duration window;

    RateLimits(
            StringRedisTemplate redis,
            @Value("${otmetka.rate-limit.register-per-ip}") int registerPerIp,
            @Value("${otmetka.rate-limit.register-window-minutes}") int windowMinutes) {
        this.redis = redis;
        this.registerPerIp = registerPerIp;
        this.window = Duration.ofMinutes(windowMinutes);
    }

    void checkRegister(String ip) {
        String key = "register:ip:" + ip;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, window);
        }
        if (count != null && count > registerPerIp) {
            throw new TooManyRequestsException("Слишком много регистраций. Попробуйте позже");
        }
    }
}
