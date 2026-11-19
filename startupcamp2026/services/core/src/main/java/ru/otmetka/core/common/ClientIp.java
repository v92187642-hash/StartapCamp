package ru.otmetka.core.common;

import jakarta.servlet.http.HttpServletRequest;

public final class ClientIp {

    private ClientIp() {
    }

    /**
     * Address nginx puts in X-Real-IP ({@code $remote_addr} after real_ip).
     * X-Forwarded-For is ignored: the browser can send that header itself.
     */
    public static String of(HttpServletRequest request) {
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) {
            return real.trim();
        }
        return request.getRemoteAddr();
    }
}
