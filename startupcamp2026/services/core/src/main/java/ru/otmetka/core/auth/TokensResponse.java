package ru.otmetka.core.auth;

record TokensResponse(String accessToken, String refreshToken, long expiresIn) {
}
