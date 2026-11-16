package com.nanolaba.logging.util;

/**
 * Source classes without a canonical name. Kept in their own file so their binary names are stable:
 * {@code AnonymousSource$1} and {@code AnonymousSource$1Local}.
 */
public final class AnonymousSource {

    /**
     * An anonymous class: its simple name is empty and its canonical name is {@code null}.
     */
    public static final Class<?> ANONYMOUS = new Object() {
    }.getClass();

    /**
     * A local class: its simple name is {@code Local}, its canonical name is {@code null}.
     */
    public static final Class<?> LOCAL = local();

    private AnonymousSource() {
    }

    private static Class<?> local() {
        class Local {
        }
        return Local.class;
    }
}
