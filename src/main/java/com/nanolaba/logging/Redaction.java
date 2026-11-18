package com.nanolaba.logging;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static com.nanolaba.logging.DefaultLoggerCreator.PROPERTY_REDACT;
import static com.nanolaba.logging.DefaultLoggerCreator.getProperty;

/**
 * The masking of {@code nanolog.redact}: the values of fields of {@link LOG#kv(String, Object)} and of the context of
 * {@link LOG#context(String, Object)} whose key names a secret become {@code ***} when an entry is created, before
 * anything formats them. A key names a secret when it, or one of its words, is in the list: words are split at
 * characters that are not letters or digits and before an upper-case letter that follows a lower-case one, so
 * {@code password} covers {@code db.password} and {@code userPassword}, not {@code passwords}.
 */
final class Redaction {

    /**
     * The words masked without {@code nanolog.redact}, those whose fields {@code LOG.dbg} hides.
     */
    static final List<String> DEFAULT = Arrays.asList("password", "passwd", "secret", "token", "apikey", "credential", "authorization");

    /**
     * The words of the list, lower case, without separators; read by {@link LOG#init()}.
     */
    private static volatile Set<String> words = new HashSet<>(DEFAULT);

    private Redaction() {/**/}

    /**
     * Reads {@code nanolog.redact}: a comma-separated list that replaces the default one; empty turns masking off.
     */
    static void configure() {
        String value = getProperty(PROPERTY_REDACT);
        Set<String> read = new HashSet<>();
        for (String item : value == null ? DEFAULT : Arrays.asList(value.split(","))) {
            String word = normalized(item);
            if (!word.isEmpty()) {
                read.add(word);
            }
        }
        words = read;
    }

    private static String normalized(String text) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                result.append(Character.toLowerCase(c));
            }
        }
        return result.toString();
    }

    /**
     * Whether a key names a secret: the whole key without its separators, or one of its words, is in the list.
     */
    static boolean matches(String key) {
        Set<String> current = words;
        StringBuilder whole = new StringBuilder();
        for (String part : WORDS.split(key)) {
            String word = part.toLowerCase(Locale.ROOT);
            if (current.contains(word)) {
                return true;
            }
            whole.append(word);
        }
        return current.contains(whole.toString());
    }

    /**
     * Where a key splits into words: at characters that are not letters or digits, and between a lower-case and an
     * upper-case letter. An empty part, before a leading separator, matches no word of the list.
     */
    private static final Pattern WORDS = Pattern.compile("[^\\p{L}\\p{N}]+|(?<=\\p{Ll})(?=\\p{Lu})");

    /**
     * Masks the fields among the arguments of an entry whose key names a secret. The array of the caller is left as
     * it is.
     *
     * @return {@code args} itself when nothing is masked
     */
    static Object[] apply(Object[] args) {
        Object[] masked = args;
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof LOG.Field && isSecret(((LOG.Field) args[i]).getKey())) {
                    if (masked == args) {
                        masked = args.clone();
                    }
                    masked[i] = LOG.kv(((LOG.Field) args[i]).getKey(), LOG.SECRET);
                }
            }
        }
        return masked;
    }

    /**
     * Masks the values of a context whose key names a secret.
     *
     * @return {@code pairs} itself when nothing is masked, otherwise an unmodifiable copy
     */
    static Map<String, Object> apply(Map<String, Object> pairs) {
        Map<String, Object> masked = pairs;
        for (String key : pairs.keySet()) {
            if (isSecret(key)) {
                if (masked == pairs) {
                    masked = new LinkedHashMap<>(pairs);
                }
                masked.put(key, LOG.SECRET);
            }
        }
        return masked == pairs ? pairs : Collections.unmodifiableMap(masked);
    }

    private static boolean isSecret(String key) {
        return key != null && matches(key);
    }
}
