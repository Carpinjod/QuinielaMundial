package quinielamundial.notification;

import quinielamundial.logging.Logger;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.util.Properties;

/**
 * SMTP email sender.
 * <p>
 * Configured via environment variables: SMTP_HOST, SMTP_PORT, SMTP_USER, SMTP_PASS, SMTP_FROM.
 * Defaults to Gmail SMTP on port 587 with STARTTLS.
 */
public class MailSender implements Sender {

    private static final Logger LOG = new Logger("MailSender");

    private final String smtpHost;
    private final int smtpPort;
    private final String smtpUser;
    private final String smtpPass;
    private final String fromAddress;

    public MailSender() {
        this(
            System.getenv().getOrDefault("SMTP_HOST", "smtp.gmail.com"),
            Integer.parseInt(System.getenv().getOrDefault("SMTP_PORT", "587")),
            System.getenv().getOrDefault("SMTP_USER", ""),
            System.getenv().getOrDefault("SMTP_PASS", ""),
            System.getenv().getOrDefault("SMTP_FROM", System.getenv().getOrDefault("SMTP_USER", ""))
        );
    }

    public MailSender(String smtpHost, int smtpPort, String smtpUser, String smtpPass, String fromAddress) {
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
        this.smtpUser = smtpUser;
        this.smtpPass = smtpPass;
        this.fromAddress = fromAddress;
    }

    /** True if SMTP credentials are configured. */
    public boolean isConfigured() {
        return !smtpUser.isBlank() && !smtpPass.isBlank();
    }

    /** Send an email. Returns silently on success; logs errors. */
    public void send(String to, String subject, String body) {
        if (!isConfigured()) {
            LOG.error("SMTP not configured — cannot send email to {}", to);
            return;
        }

        var props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", String.valueOf(smtpPort));

        var session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(smtpUser, smtpPass);
            }
        });

        try {
            var message = new MimeMessage(session);
            message.setFrom(new InternetAddress(fromAddress));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject);
            message.setText(body);
            Transport.send(message);
            LOG.info("Email sent to {}", to);
        } catch (MessagingException e) {
            LOG.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}
