package kh.edu.istad.ite.devsoleapi.feature.userprofile.domain;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What a username may be, in the one place every caller reads it.
 *
 * <p>Registration, social-login provisioning, the backfill and the edit
 * endpoint all mint or accept usernames. A rule restated in four places is a
 * rule that will disagree with itself, and the disagreement surfaces as a
 * handle that exists but cannot be typed into a URL.
 */
public final class UsernamePolicy {

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 30;

    /**
     * How long a handle stays put after a change.
     *
     * <p>A username is the public address of a profile: every link shared to
     * it breaks when it moves, and a handle freed instantly can be taken by
     * somebody else and pointed at a different person. A month is long enough
     * that neither is casual and short enough that a genuine mistake is not
     * permanent.
     */
    public static final long CHANGE_COOLDOWN_DAYS = 30;

    /**
     * Alphanumeric at both ends, separators only between. Rules out
     * {@code .hidden} and {@code name-}, and with them the lookalikes that
     * leading and trailing punctuation makes possible.
     */
    private static final Pattern FORMAT = Pattern.compile(
            "^[a-zA-Z0-9](?:[a-zA-Z0-9._-]{1,28}[a-zA-Z0-9])?$"
    );

    /**
     * Names that must never belong to a person: they collide with routes a
     * client builds by hand, or they let an account pass itself off as the
     * platform.
     */
    private static final Set<String> RESERVED = Set.of(
            "me", "self", "admin", "administrator", "root", "system",
            "api", "app", "www", "mail", "static", "assets",
            "login", "logout", "register", "signup", "signin", "auth",
            "settings", "account", "dashboard", "profile", "profiles",
            "user", "users", "user-profiles", "organization", "organizations",
            "program", "programs", "report", "reports", "problem", "problems",
            "solution", "solutions", "showcase", "showcases", "hacktivity",
            "leaderboard", "notifications", "search", "support", "help",
            "security", "legal", "privacy", "terms", "about", "contact",
            "devsolve", "staff", "team", "official", "moderator",
            "new", "edit", "delete", "null", "undefined", "none"
    );

    private UsernamePolicy() {
    }

    /**
     * Whether the value is a username at all. Case is preserved on the way in
     * — someone who signs up as {@code TaingSengkim} keeps the capitals on
     * their profile — so only the comparison is case-insensitive.
     */
    public static boolean isValid(String username) {
        return username != null
                && username.length() >= MIN_LENGTH
                && username.length() <= MAX_LENGTH
                && FORMAT.matcher(username).matches();
    }

    public static boolean isReserved(String username) {
        return username != null
                && RESERVED.contains(username.toLowerCase(Locale.ROOT));
    }

    /**
     * The form two usernames are compared in. Uniqueness is case-insensitive
     * because {@code sengkim} and {@code Sengkim} reading as different people
     * is how impersonation starts.
     */
    public static String normalize(String username) {
        return username == null
                ? null
                : username.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * A first username for an account that arrived without one — a social
     * login, or a row that predates the column.
     *
     * <p>Derived from the email so it is recognisable to its owner rather than
     * a random string they immediately want to change. The result is only a
     * candidate: it still has to survive a uniqueness check, which is the
     * caller's job because only they know what else is being written.
     */
    public static String suggestFrom(String email) {
        String localPart = email == null ? "" : email.split("@", 2)[0];
        String cleaned = localPart
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "")
                .replaceAll("^[._-]+", "")
                .replaceAll("[._-]+$", "");

        if (cleaned.length() > MAX_LENGTH) {
            cleaned = cleaned.substring(0, MAX_LENGTH)
                    .replaceAll("[._-]+$", "");
        }
        // "user" would be the obvious fallback and is reserved, which would
        // send every unusable email straight back through the numbering loop.
        return cleaned.length() >= MIN_LENGTH ? cleaned : "member";
    }

    /**
     * The candidate with a disambiguating number, kept inside the length
     * limit. {@code sengkim} taken becomes {@code sengkim2}, not a truncation
     * that collides all over again.
     */
    public static String withSuffix(String candidate, int suffix) {
        String tail = String.valueOf(suffix);
        String head = candidate.length() + tail.length() > MAX_LENGTH
                ? candidate.substring(0, MAX_LENGTH - tail.length())
                : candidate;
        return head.replaceAll("[._-]+$", "") + tail;
    }
}
