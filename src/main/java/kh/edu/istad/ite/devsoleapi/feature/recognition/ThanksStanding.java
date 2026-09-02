package kh.edu.istad.ite.devsoleapi.feature.recognition;

import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.reputation.ReputationPolicy;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

/**
 * What one researcher has been thanked for by a program or an organization,
 * accumulated as the severity tallies are read.
 *
 * <p>Mutable and package-private: it exists for the length of one query fold
 * and never leaves {@link RecognitionServiceImpl}.
 */
final class ThanksStanding {

    private long recognitions;

    private int severityWeight;

    private final Map<Severity, Long> bySeverity =
            new EnumMap<>(Severity.class);

    private LocalDateTime lastThankedAt;

    void add(Severity severity, long count, LocalDateTime lastAwardedAt) {

        recognitions += count;
        severityWeight += ReputationPolicy.pointsFor(severity) * (int) count;

        // A null severity would blow up an EnumMap, and there is nothing
        // useful to say about it on a card anyway — it still counts toward
        // the total above, which is what the page is actually about.
        if (severity != null) {
            bySeverity.merge(severity, count, Long::sum);
        }

        if (lastAwardedAt != null
                && (lastThankedAt == null
                        || lastAwardedAt.isAfter(lastThankedAt))) {
            lastThankedAt = lastAwardedAt;
        }
    }

    long recognitions() {
        return recognitions;
    }

    /**
     * The tie-break behind {@link #recognitions()}: at equal counts the
     * researcher thanked for the harder findings ranks first.
     *
     * <p>Deliberately not published. An organization's thanks are not a second
     * currency beside reputation — reputation is earned when a report is
     * resolved, platform-wide and priced by severity — and a number on the
     * wire invites being read as one.
     */
    int severityWeight() {
        return severityWeight;
    }

    Map<Severity, Long> bySeverity() {
        return Map.copyOf(bySeverity);
    }

    LocalDateTime lastThankedAt() {
        return lastThankedAt;
    }
}
