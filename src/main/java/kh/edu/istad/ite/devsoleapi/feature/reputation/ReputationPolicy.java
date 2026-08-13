package kh.edu.istad.ite.devsoleapi.feature.reputation;

import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;

/**
 * The single place that decides what a recognised finding is worth.
 *
 * <p>The curve is deliberately super-linear. On a flat scale twenty low
 * findings outrank one critical, so the leaderboard fills with researchers
 * who farm trivia instead of the ones who find the bugs that matter. Each
 * step up roughly doubles the reward, which is the shape every established
 * bounty platform converged on.
 *
 * <p>{@link Severity#NONE} scores zero on purpose: informational findings
 * still earn a recognition and a hacktivity entry, they just do not move the
 * leaderboard.
 */
public final class ReputationPolicy {

    private ReputationPolicy() {
    }

    /**
     * Points awarded for a finding of the given severity. Written as an
     * exhaustive switch so that adding a severity to the enum fails the
     * build here rather than silently scoring the new level as zero.
     */
    public static int pointsFor(Severity severity) {
        if (severity == null) {
            return 0;
        }

        return switch (severity) {
            case NONE -> 0;
            case LOW -> 5;
            case MEDIUM -> 15;
            case HIGH -> 40;
            case CRITICAL -> 100;
        };
    }
}
