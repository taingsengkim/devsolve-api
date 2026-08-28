package kh.edu.istad.ite.devsoleapi.feature.program;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What a program handle may be, in the one place every caller reads it.
 *
 * <p>The handle is a program's public URL segment, so the create DTO, the
 * update DTO and the availability check all have an opinion about it. Restated
 * in three places it is a rule that will disagree with itself, and the
 * disagreement surfaces as a handle the availability check calls free and the
 * write then rejects.
 */
public final class ProgramHandlePolicy {

    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 100;

    /**
     * Lowercase alphanumeric segments joined by single hyphens. Rules out
     * {@code -acme}, {@code acme-} and {@code acme--web}, none of which read
     * as one path segment.
     */
    public static final String FORMAT = "^[a-z0-9]+(?:-[a-z0-9]+)*$";

    public static final String LENGTH_MESSAGE =
            "Program handle must be between " + MIN_LENGTH + " and "
                    + MAX_LENGTH + " characters";

    public static final String FORMAT_MESSAGE =
            "Program handle must use lowercase letters, numbers, and single "
                    + "hyphens";

    private static final Pattern COMPILED_FORMAT = Pattern.compile(FORMAT);

    private ProgramHandlePolicy() {
    }

    public static boolean isValid(String handle) {
        return handle != null
                && handle.length() >= MIN_LENGTH
                && handle.length() <= MAX_LENGTH
                && COMPILED_FORMAT.matcher(handle).matches();
    }

    /**
     * The form two handles are compared in. Uniqueness is case-insensitive
     * because two programs whose URLs differ only in case are one URL.
     */
    public static String normalize(String handle) {
        return handle == null
                ? null
                : handle.trim().toLowerCase(Locale.ROOT);
    }
}
