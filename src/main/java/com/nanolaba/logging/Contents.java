package com.nanolaba.logging;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import static com.nanolaba.logging.DefaultLoggerCreator.PROPERTY_CONTENT;
import static com.nanolaba.logging.DefaultLoggerCreator.getProperty;
import static com.nanolaba.logging.DefaultLoggerCreator.printDiagnostic;

/**
 * The payloads of {@link LOG#content(Object)}: how much of them is written, by {@code nanolog.content} and its rules
 * for packages and classes, {@code nanolog.content.<package or class>}. Hidden, a payload is its length and hash;
 * truncated, its start; shown, all of it. What is written of the text is always escaped, so a payload cannot forge a
 * line.
 */
final class Contents {

    /**
     * The limit that writes no text, only the length and the hash.
     */
    static final int HIDE = -1;

    /**
     * The limit that writes all of the text.
     */
    static final int SHOW = Integer.MAX_VALUE;

    private static final String RULE_PREFIX = PROPERTY_CONTENT + ".";

    /**
     * The limit without a rule, from {@code nanolog.content}; read by {@link LOG#init()}.
     */
    static volatile int defaultLimit = HIDE;

    /**
     * The limits of the rules by package or class name; never modified, replaced by {@link #configure()}.
     */
    private static volatile Map<String, Integer> rules = new HashMap<>();

    private Contents() {/**/}

    /**
     * Reads {@code nanolog.content} and the system properties {@code nanolog.content.<package or class>}, which
     * include the keys of {@code nanolog.properties}.
     */
    static void configure() {
        String value = getProperty(PROPERTY_CONTENT);
        Integer limit = value == null ? null : parse(PROPERTY_CONTENT, value);
        defaultLimit = limit == null ? HIDE : limit;
        Map<String, Integer> read = new HashMap<>();
        for (String key : System.getProperties().stringPropertyNames()) {
            if (key.startsWith(RULE_PREFIX)) {
                Integer rule = parse(key, System.getProperty(key));
                if (rule != null) {
                    read.put(key.substring(RULE_PREFIX.length()), rule);
                }
            }
        }
        rules = read;
    }

    /**
     * Parses {@code hide}, {@code show} or {@code truncate:<characters>}, ignoring case and spaces.
     *
     * @return the limit; {@code null}, after a diagnostic, for an invalid value
     */
    private static Integer parse(String property, String value) {
        String mode = value.trim().toLowerCase(Locale.ROOT);
        if (mode.equals("hide")) {
            return HIDE;
        } else if (mode.equals("show")) {
            return SHOW;
        } else if (mode.startsWith("truncate:")) {
            try {
                int characters = Integer.parseInt(mode.substring("truncate:".length()).trim());
                if (characters > 0) {
                    return characters;
                }
            } catch (NumberFormatException e) {
                // reported below
            }
        }
        DefaultLoggerCreator.printInvalid(property, value, "expected hide, show or truncate:<characters>");
        return null;
    }

    /**
     * The limit for the entries of a class: the rule of the class, of its enclosing classes, then of its packages
     * from the innermost, or else {@code nanolog.content}.
     */
    static int limit(Class sourceClass) {
        if (sourceClass != null) {
            Map<String, Integer> current = rules;
            String name = sourceClass.getName();
            while (true) {
                Integer limit = current.get(name);
                if (limit != null) {
                    return limit;
                }
                int cut = Math.max(name.lastIndexOf('.'), name.lastIndexOf('$'));
                if (cut == -1) {
                    break;
                }
                name = name.substring(0, cut);
            }
        }
        return defaultLimit;
    }

    /**
     * Binds the payloads among the arguments of an entry to the limit of its class: a payload, or a field whose value
     * is one. The array of the caller is left as it is.
     *
     * @return {@code args} itself when it holds no payload
     */
    static Object[] bind(Object[] args, Class sourceClass) {
        Object[] bound = args;
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                Object argument = bind(args[i], sourceClass);
                if (argument != args[i]) {
                    if (bound == args) {
                        bound = args.clone();
                    }
                    bound[i] = argument;
                }
            }
        }
        return bound;
    }

    /**
     * Binds the payloads among the values of a context to the limit of a class, so that a rule for the package of an
     * entry applies to its context too.
     *
     * @return {@code pairs} itself when it holds no payload, otherwise an unmodifiable copy
     */
    static Map<String, Object> bind(Map<String, Object> pairs, Class sourceClass) {
        Map<String, Object> bound = pairs;
        for (Map.Entry<String, Object> pair : pairs.entrySet()) {
            Object value = bind(pair.getValue(), sourceClass);
            if (value != pair.getValue()) {
                if (bound == pairs) {
                    bound = new LinkedHashMap<>(pairs);
                }
                bound.put(pair.getKey(), value);
            }
        }
        return bound == pairs ? pairs : Collections.unmodifiableMap(bound);
    }

    /**
     * Binds a payload, or a field whose value is one, to the limit of a class.
     *
     * @return {@code value} itself when it is neither
     */
    static Object bind(Object value, Class sourceClass) {
        if (value instanceof LOG.Content) {
            return ((LOG.Content) value).bind(sourceClass);
        } else if (value instanceof LOG.Field && ((LOG.Field) value).getValue() instanceof LOG.Content) {
            LOG.Field field = (LOG.Field) value;
            return LOG.kv(field.getKey(), ((LOG.Content) field.getValue()).bind(sourceClass));
        }
        return value;
    }

    /**
     * The text of a payload as a logger writes it: {@code [content 1834 chars #3f2a9c1b]} when hidden, otherwise the
     * text, escaped, cut to the limit with {@code ... [+1754 chars]}.
     */
    static String render(String text, int limit) {
        if (limit == HIDE) {
            return "[content " + chars(text.length()) + " #" + hash(text) + "]";
        }
        String masked = masked(text);
        boolean cut = masked.length() > limit;
        String shown = escape(cut ? masked.substring(0, limit) : masked);
        return cut ? shown + "... [+" + chars(masked.length() - limit) + "]" : shown;
    }

    /**
     * Secrets that a shown payload may carry, with what they become: keys of model providers ({@code sk-...}), AWS
     * access keys, bearer tokens and JSON web tokens. Only payloads are scanned, never the text of messages.
     */
    private static final Pattern[] PATTERNS = {
            Pattern.compile("\\bsk-[A-Za-z0-9_-]{16,}"),
            Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b"),
            Pattern.compile("(?i)\\b(Bearer)\\s+[A-Za-z0-9._~+/=-]{8,}"),
            Pattern.compile("\\beyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]*"),
    };
    private static final String[] REPLACEMENTS = {"sk-***", "AKIA***", "$1 ***", "eyJ***"};

    /**
     * A shown payload with its known secrets masked, before it is cut.
     */
    static String masked(String text) {
        String result = text;
        for (int i = 0; i < PATTERNS.length; i++) {
            result = PATTERNS[i].matcher(result).replaceAll(REPLACEMENTS[i]);
        }
        return result;
    }

    /**
     * Eight hexadecimal digits of the SHA-256 of a random key of this JVM and the text: equal payloads have equal
     * hashes within one run, and the log does not give a short payload away to someone who tries candidates.
     */
    static String hash(String text) {
        MessageDigest digest = sha256();
        digest.update(Key.BYTES);
        byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
        return String.format(Locale.ROOT, "%02x%02x%02x%02x", bytes[0], bytes[1], bytes[2], bytes[3]);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // every Java platform has SHA-256
            throw new IllegalStateException(e);
        }
    }

    /**
     * The key of the hashes, made on first use, so that a JVM that hides no payload never seeds a random generator.
     */
    private static final class Key {
        static final byte[] BYTES = new byte[16];

        static {
            new SecureRandom().nextBytes(BYTES);
        }
    }

    /**
     * A number of characters: {@code 1 char}, {@code 4936 chars}; also for the line of agent mode.
     */
    static String chars(int count) {
        return count + (count == 1 ? " char" : " chars");
    }

    /**
     * Line breaks as {@code \r} and {@code \n}, other control characters, U+2028/U+2029, bidi controls and tags
     * ({@link DefaultLoggerCreator#isMisleadingAt}) as {@code \}{@code u001b}; a tab stays.
     */
    static String escape(String text) {
        StringBuilder result = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                result.append("\\n");
            } else if (c == '\r') {
                result.append("\\r");
            } else if (c != '\t' && (Character.isISOControl(c) || DefaultLoggerCreator.isMisleadingAt(text, i) || c == ' ' || c == ' ')) {
                result.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
