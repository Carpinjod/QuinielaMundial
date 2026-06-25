package quinielamundial.notification;

/**
 * Common interface for email senders.
 * Implementations: {@link MailSender} (SMTP), {@link GoogleMailSender} (Gmail API OAuth2).
 */
public interface Sender {
    boolean isConfigured();
    void send(String to, String subject, String body);
}
