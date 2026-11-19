package ru.otmetka.core.common;

/** Sends an email. Modules use this and never talk to a mail server themselves. */
public interface Mailer {

    void send(String to, String subject, String text);
}
