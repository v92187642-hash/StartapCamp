package ru.otmetka.core.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.ErrorResponseException;

@Configuration
class MailConfig {

    private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

    /** Local development: the email is written to the log, where the developer reads the code. */
    @Bean
    @ConditionalOnProperty(name = "otmetka.mail.enabled", havingValue = "false", matchIfMissing = true)
    Mailer logMailer() {
        return (to, subject, text) -> log.info("Email to {} | {} | {}", to, subject, text);
    }

    /** Real delivery through the SMTP server configured with MAIL_HOST and friends. */
    @Bean
    @ConditionalOnProperty(name = "otmetka.mail.enabled", havingValue = "true")
    Mailer smtpMailer(JavaMailSender sender, @Value("${otmetka.mail.from}") String from) {
        return (to, subject, text) -> {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            try {
                sender.send(message);
            } catch (MailException e) {
                log.error("Email to {} was not sent", to, e);
                throw new ErrorResponseException(HttpStatus.SERVICE_UNAVAILABLE, ProblemDetail.forStatusAndDetail(
                        HttpStatus.SERVICE_UNAVAILABLE, "Не удалось отправить письмо. Попробуйте позже"), e);
            }
        };
    }
}
