package com.nanolaba.logging;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Renders values whose class has no {@code toString()} of its own field by field, instead of
 * {@code Order@2f92e0f4}: {@code Order{id=17, items=[Item{sku="A-1", qty=2}]}}. {@link LOG#dbg(Object)} always uses
 * it; {@code {}} arguments use it with {@code nanolog.dump=auto}.
 * <p>
 * Classes of the JDK are never reflected into, so the dump needs no {@code --add-opens} on JDK 17+: collections, maps,
 * arrays and {@code Optional} are rendered through their public API, any other JDK value through its
 * {@code toString()}. Cycles, depth, items and string length are limited, and every cut says what was left out. Field
 * names and string map keys that look like secrets ({@code password}, {@code token}, {@code apiKey}...) show
 * {@code ***}. Proxies and lambdas are shown by their class name, so a lazy entity is never loaded.
 */
final class Dump {

    /**
     * Containers nested deeper than this below the dumped value show only how many items they have.
     */
    static final int MAX_DEPTH = 3;
    /**
     * The most items shown of a collection, a map or an array.
     */
    static final int MAX_ITEMS = 100;
    /**
     * The most characters shown of a string inside a dump.
     */
    static final int MAX_STRING = 1000;

    static final String FAILED = "[FAILED dump()]";
    static final String INACCESSIBLE = "<inaccessible>";
    static final String REDACTED = "***";

    private static final String[] SECRETS = {"password", "passwd", "secret", "token", "apikey", "credential", "authorization"};
    private static final String RECORD_CLASS = "java.lang.Record";
    private static final String[] JDK_PACKAGES = {"java.", "javax.", "jdk.", "sun.", "com.sun."};

    /**
     * {@code nanolog.dump=auto}: {@code {}} arguments without a {@code toString()} of their own are dumped. Read by
     * {@link LOG#init()}.
     */
    static volatile boolean auto;

    /**
     * How a class is rendered.
     */
    enum Kind {
        ARRAY(true, false),
        COLLECTION(true, false),
        MAP(true, false),
        OPTIONAL(true, false),
        /**
         * An application class with the {@code toString()} of {@code Object}: listed field by field.
         */
        FIELDS(false, true),
        RECORD(false, false),
        /**
         * A proxy or a lambda with the {@code toString()} of {@code Object}: its class name.
         */
        NAMED(false, true),
        /**
         * A proxy with a {@code toString()}: its class name in a dump, which never calls a proxy.
         */
        PROXY(false, false),
        /**
         * Its own {@code toString()}, or a class of the JDK.
         */
        TEXT(false, false);

        /**
         * Rendered item by item in both styles.
         */
        final boolean container;
        /**
         * Has the {@code toString()} of {@code Object}, so {@code nanolog.dump=auto} dumps it as an argument.
         */
        final boolean undescribed;

        Kind(boolean container, boolean undescribed) {
            this.container = container;
            this.undescribed = undescribed;
        }
    }

    /**
     * The kind of each class. A {@code ClassValue} does not keep a class loader alive.
     */
    private static volatile ClassValue<Kind> kinds = kinds();
    /**
     * The fields of each class that is listed field by field.
     */
    private static volatile ClassValue<Field[]> fields = fields();

    /**
     * The containers being rendered, by identity: meeting one again is a cycle.
     */
    private final Set<Object> path = Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * A value as {@code LOG.dbg} shows it: strings quoted, objects without {@code toString()} field by field, all
     * within the limits. Never throws.
     */
    static String dump(Object value) {
        return render(value, false);
    }

    /**
     * A {@code {}} argument with {@code nanolog.dump=auto}: what has a {@code toString()} prints as before, with no
     * limits; collections, maps, arrays and {@code Optional} are walked, so that only the values without a
     * {@code toString()} are dumped. Never throws.
     */
    static String argument(Object value) {
        return render(value, true);
    }

    private static String render(Object value, boolean plain) {
        try {
            StringBuilder out = new StringBuilder();
            new Dump().append(out, value, 0, plain);
            return out.toString();
        } catch (Throwable t) {
            return FAILED;
        }
    }

    /**
     * @param depth the number of containers around the value
     * @param plain {@code true} for an argument of a message: what has a {@code toString()} keeps it
     */
    private void append(StringBuilder out, Object value, int depth, boolean plain) {
        if (value == null) {
            out.append("null");
            return;
        }
        Class<?> type = value.getClass();
        Kind kind = kinds.get(type);
        if (kind.container) {
            container(out, value, kind, depth, plain);
        } else if (plain && !kind.undescribed) {
            out.append(LogEntry.safeToString(value));
        } else if (value instanceof String) {
            String text = (String) value;
            if (text.length() > MAX_STRING) {
                JsonLogger.appendString(out, text.substring(0, MAX_STRING));
                out.append("... (+").append(text.length() - MAX_STRING).append(" more chars)");
            } else {
                JsonLogger.appendString(out, text);
            }
        } else if (value instanceof Character) {
            out.append(quote((Character) value));
        } else if (kind == Kind.FIELDS || kind == Kind.RECORD) {
            fields(out, value, type, kind == Kind.RECORD, depth);
        } else if (kind == Kind.TEXT) {
            out.append(LogEntry.safeToString(value));
        } else {
            out.append(LogEntry.simpleName(type));
        }
    }

    private void container(StringBuilder out, Object value, Kind kind, int depth, boolean plain) {
        if (kind == Kind.OPTIONAL) {
            Optional<?> optional = (Optional<?>) value;
            if (optional.isPresent()) {
                out.append("Optional[");
                append(out, optional.get(), depth, plain);
                out.append(']');
            } else {
                out.append("Optional.empty");
            }
        } else if (!path.add(value)) {
            cycle(out, value);
        } else {
            int limit = plain ? Integer.MAX_VALUE : depth <= MAX_DEPTH ? MAX_ITEMS : 0;
            if (kind == Kind.MAP) {
                entries(out, (Map<?, ?>) value, limit, depth, plain);
            } else {
                items(out, kind == Kind.ARRAY ? asList(value) : (Collection<?>) value, limit, depth, plain);
            }
            path.remove(value);
        }
    }

    private void items(StringBuilder out, Collection<?> items, int limit, int depth, boolean plain) {
        out.append('[');
        int shown = 0;
        for (Object item : items) {
            if (shown == limit) {
                break;
            }
            separator(out, shown++);
            append(out, item, depth + 1, plain);
        }
        more(out, shown, items.size());
        out.append(']');
    }

    private void entries(StringBuilder out, Map<?, ?> map, int limit, int depth, boolean plain) {
        out.append('{');
        int shown = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (shown == limit) {
                break;
            }
            separator(out, shown++);
            append(out, entry.getKey(), depth + 1, plain);
            out.append('=');
            value(out, entry.getKey(), entry.getValue(), depth + 1, plain);
        }
        more(out, shown, map.size());
        out.append('}');
    }

    private void fields(StringBuilder out, Object value, Class<?> type, boolean record, int depth) {
        if (!path.add(value)) {
            cycle(out, value);
            return;
        }
        Field[] fields = Dump.fields.get(type);
        int limit = depth <= MAX_DEPTH ? fields.length : 0;
        out.append(LogEntry.simpleName(type)).append(record ? '[' : '{');
        for (int i = 0; i < limit; i++) {
            separator(out, i);
            field(out, fields[i], value, depth + 1);
        }
        more(out, limit, fields.length);
        out.append(record ? ']' : '}');
        path.remove(value);
    }

    /**
     * Appends {@code name=value} of one field of the target.
     */
    void field(StringBuilder out, Field field, Object target, int depth) {
        out.append(field.getName()).append('=');
        Object value;
        try {
            value = field.get(target);
        } catch (Exception e) {
            out.append(INACCESSIBLE);
            return;
        }
        value(out, field.getName(), value, depth, false);
    }

    /**
     * Appends a value, or {@code ***} in a dump if its name looks like a secret. A message argument with a
     * {@code toString()} of its own is never redacted, so a map prints as before.
     *
     * @param name a field name or a map key
     */
    private void value(StringBuilder out, Object name, Object value, int depth, boolean plain) {
        if (!plain && value != null && name instanceof String && isSecret((String) name)) {
            out.append(REDACTED);
        } else {
            append(out, value, depth, plain);
        }
    }

    private void cycle(StringBuilder out, Object value) {
        out.append("<cycle: ").append(LogEntry.simpleName(value.getClass())).append('>');
    }

    private static void separator(StringBuilder out, int index) {
        if (index > 0) {
            out.append(", ");
        }
    }

    private static void more(StringBuilder out, int shown, int total) {
        if (total > shown) {
            separator(out, shown);
            out.append("... (+").append(total - shown).append(" more)");
        }
    }

    /**
     * Whether a field name or a map key looks like a secret: {@code password}, {@code passwd}, {@code secret},
     * {@code token}, {@code apiKey}, {@code credential} or {@code authorization} anywhere in it, ignoring case,
     * underscores and hyphens, so {@code accessToken} and {@code X-Api-Key} match too.
     */
    static boolean isSecret(String name) {
        String normalized = name.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        for (String secret : SECRETS) {
            if (normalized.contains(secret)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A string quoted and escaped as in JSON, so a value never breaks the line.
     */
    static String quote(String value) {
        StringBuilder out = new StringBuilder();
        JsonLogger.appendString(out, value);
        return out.toString();
    }

    /**
     * A char in single quotes, escaped like a string.
     */
    static String quote(char value) {
        if (value == '\'') {
            return "'\\''";
        } else if (value == '"') {
            return "'\"'";
        }
        String quoted = quote(String.valueOf(value));
        return "'" + quoted.substring(1, quoted.length() - 1) + "'";
    }

    /**
     * Forgets what is known about classes; for tests.
     */
    static void clearCache() {
        kinds = kinds();
        fields = fields();
    }

    private static ClassValue<Kind> kinds() {
        return new ClassValue<Kind>() {
            @Override
            protected Kind computeValue(Class<?> type) {
                return kindOf(type);
            }
        };
    }

    private static ClassValue<Field[]> fields() {
        return new ClassValue<Field[]>() {
            @Override
            protected Field[] computeValue(Class<?> type) {
                return fieldsOf(type);
            }
        };
    }

    static Kind kindOf(Class<?> type) {
        if (type.isArray()) {
            return Kind.ARRAY;
        }
        Class<?> declaring = toStringDeclaringClass(type);
        boolean described = declaring != Object.class;
        String name = type.getName();
        if (Proxy.isProxyClass(type) || name.contains("$$") || name.contains("$HibernateProxy$")) {
            return described ? Kind.PROXY : Kind.NAMED;
        }
        if (isJdk(declaring)) {
            if (Collection.class.isAssignableFrom(type)) {
                return Kind.COLLECTION;
            } else if (Map.class.isAssignableFrom(type)) {
                return Kind.MAP;
            } else if (type == Optional.class) {
                return Kind.OPTIONAL;
            }
        }
        if (isJdk(type)) {
            return Kind.TEXT;
        }
        boolean record = RECORD_CLASS.equals(type.getSuperclass().getName());
        return record ? Kind.RECORD : described ? Kind.TEXT : Kind.FIELDS;
    }

    private static Class<?> toStringDeclaringClass(Class<?> type) {
        for (Class<?> current = type; ; current = current.getSuperclass()) {
            try {
                current.getDeclaredMethod("toString");
                return current;
            } catch (NoSuchMethodException e) {
                // inherited: Object declares it, so the loop ends
            }
        }
    }

    private static boolean isJdk(Class<?> type) {
        for (String prefix : JDK_PACKAGES) {
            if (type.getName().startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The instance fields of a class of the application and of its superclasses up to the first class of the JDK,
     * the superclass fields first. Static and synthetic fields, such as {@code this$0}, are left out.
     */
    static Field[] fieldsOf(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> current = type; !isJdk(current); current = current.getSuperclass()) {
            List<Field> declared = new ArrayList<>();
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                    try {
                        field.setAccessible(true);
                    } catch (RuntimeException e) {
                        // a module that is not open: reading the field shows <inaccessible>
                    }
                    declared.add(field);
                }
            }
            fields.addAll(0, declared);
        }
        return fields.toArray(new Field[0]);
    }

    private static List<Object> asList(Object array) {
        return new AbstractList<Object>() {
            @Override
            public Object get(int index) {
                return Array.get(array, index);
            }

            @Override
            public int size() {
                return Array.getLength(array);
            }
        };
    }
}
