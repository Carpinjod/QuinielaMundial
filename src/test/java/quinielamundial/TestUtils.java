package quinielamundial;

import quinielamundial.domain.Group;
import quinielamundial.domain.Match;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Test utilities for QuinielaMundial tests.
 * Provides helpers to create test data with future dates.
 */
public final class TestUtils {
    private TestUtils() {}

    /**
     * Sets all group-stage match kickoff times to a future date (24h from now).
     * This allows tests to submit predictions without
     * {@code IllegalStateException("El partido ya comenzó")}.
     */
    public static void setFutureKickoffs(Group group) {
        var future = Instant.now().plus(1, ChronoUnit.DAYS);
        for (var match : group.matches()) {
            setKickoff(match, future);
        }
        for (var match : group.knockoutMatches()) {
            setKickoff(match, future.plus(30, ChronoUnit.DAYS));
        }
    }

    /**
     * Sets a future kickoff on a specific match.
     */
    public static void setFutureKickoff(Match match) {
        setKickoff(match, Instant.now().plus(1, ChronoUnit.DAYS));
    }

    private static void setKickoff(Match match, Instant kickoff) {
        try {
            var field = Match.class.getDeclaredField("kickoff");
            field.setAccessible(true);
            field.set(match, kickoff);
        } catch (Exception e) {
            throw new RuntimeException("Cannot set kickoff on match " + match.id(), e);
        }
    }
}
