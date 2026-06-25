package quinielamundial.notification;

import quinielamundial.domain.Group;
import quinielamundial.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Sends email reminders 1 hour before each match for members who have not yet predicted.
 * <p>
 * Checks every 60 seconds. Skips matches that are finished, in progress, or have unknown teams
 * (knockout slots not yet resolved). Tracks sent notifications to avoid duplicates.
 */
public class NotificationService {

    private static final Logger LOG = new Logger("NotificationService");
    private static final long CHECK_INTERVAL_SECONDS = 60;
    private static final long NOTIFY_BEFORE_SECONDS = 3600; // 1 hour before kickoff
    private static final long NOTIFY_WINDOW_SECONDS = 120;   // +/- 2 minutes window

    private final List<Group> groups;
    private final MailSender mailSender;
    private final String publicUrl;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Set<String> sentNotifications = ConcurrentHashMap.newKeySet();

    public NotificationService(List<Group> groups, MailSender mailSender, String publicUrl) {
        this.groups = groups;
        this.mailSender = mailSender;
        this.publicUrl = publicUrl;
    }

    public void start() {
        if (!mailSender.isConfigured()) {
            LOG.info("SMTP not configured — notification service disabled");
            return;
        }
        LOG.info("Starting notification service (every {}s, notify {}s before kickoff)",
            CHECK_INTERVAL_SECONDS, NOTIFY_BEFORE_SECONDS);
        scheduler.scheduleAtFixedRate(this::check, 10, CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public void stop() {
        scheduler.shutdown();
    }

    private void check() {
        try {
            var now = Instant.now();
            for (var group : groups) {
                for (var match : group.allMatches()) {
                    // Skip matches that have already started or where teams aren't known yet
                    if (match.finished() || match.isStarted()) continue;
                    if (!match.teamsKnown()) continue;

                    var secondsUntil = Duration.between(now, match.kickoff()).getSeconds();
                    if (secondsUntil < NOTIFY_BEFORE_SECONDS - NOTIFY_WINDOW_SECONDS
                        || secondsUntil > NOTIFY_BEFORE_SECONDS + NOTIFY_WINDOW_SECONDS) continue;

                    for (var member : group.members().values()) {
                        var email = member.email();
                        if (email == null || email.isBlank()) continue;
                        if (member.predictions().containsKey(match.id())) continue;

                        var key = member.name() + ":" + group.code() + ":" + match.id();
                        if (!sentNotifications.add(key)) continue;

                        var kickoffTime = match.kickoff().toString();
                        var timeStr = kickoffTime.substring(11, 16); // HH:mm from ISO
                        var subject = "Recordatorio: " + match.home() + " vs " + match.away() + " en 1 hora";
                        var body = "Hola " + member.name() + ",\n\n"
                            + "El partido " + match.home() + " vs " + match.away()
                            + " empieza en 1 hora (a las " + timeStr + " UTC).\n\n"
                            + "Todavia no has hecho tu pronostico para este partido en el grupo \""
                            + group.name() + "\".\n\n"
                            + "Entra aqui para pronosticar:\n"
                            + publicUrl + "/groups/" + group.code() + "?token=" + member.token() + "\n\n"
                            + "Saludos,\nQuiniela Mundial 2026";

                        mailSender.send(email, subject, body);
                        LOG.info("Reminder sent to {} for {} vs {} (group {})",
                            member.name(), match.home(), match.away(), group.code());
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("Notification check failed: {}", e.getMessage());
        }
    }
}
