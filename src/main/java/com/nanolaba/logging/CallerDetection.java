package com.nanolaba.logging;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.nanolaba.logging.DefaultLoggerCreator.PROPERTY_CALLER_SKIP;
import static com.nanolaba.logging.DefaultLoggerCreator.getProperty;
import static com.nanolaba.logging.DefaultLoggerCreator.printInvalid;

/**
 * Finds the caller of {@link LOG}: the first stack frame outside Nanolog, that is outside {@code LOG},
 * {@code LogEntry}, this class and any {@code ILogger}, and outside the classes of {@code nanolog.caller.skip} and
 * {@link LOG#skipCaller(Class[])}. Its top-level class becomes the source class of the entry, the frame itself the
 * call site.
 */
final class CallerDetection {

    private CallerDetection() {/**/}

    // SKIPPED CLASSES

    /**
     * A class or package name: Java identifiers separated by dots, a nested class after a {@code $}.
     */
    private static final Pattern NAME =
            Pattern.compile("\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*(\\.\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*)*");

    /**
     * The names of {@code nanolog.caller.skip}, read by {@link LOG#init()}.
     */
    private static Set<String> configured = Collections.emptySet();

    /**
     * The names of {@link LOG#skipCaller(Class[])}; {@link LOG#init()} keeps them.
     */
    private static final Set<String> ADDED = new HashSet<>();

    private static volatile Skip skip = new Skip(Collections.<String>emptySet());

    /**
     * The classes and packages whose frames are skipped, with what was found per class. A rule covers its nested
     * classes and, for a package, its classes and subpackages, by whole segments of the name, like the level rules.
     */
    private static final class Skip {

        private final Set<String> names;
        private final Map<Class, Boolean> resolved = new ConcurrentHashMap<>();

        Skip(Set<String> names) {
            this.names = names;
        }

        boolean matches(Class frameClass) {
            return !names.isEmpty() && resolved.computeIfAbsent(frameClass, this::find);
        }

        /**
         * Looks the name of the class up, then the names of its enclosing classes and packages from the innermost
         * outwards.
         */
        private boolean find(Class frameClass) {
            String name = frameClass.getName();
            while (!names.contains(name)) {
                int cut = Math.max(name.lastIndexOf('.'), name.lastIndexOf('$'));
                if (cut == -1) {
                    return false;
                }
                name = name.substring(0, cut);
            }
            return true;
        }
    }

    /**
     * Reads {@code nanolog.caller.skip}: class and package names separated by commas. An invalid name prints a
     * diagnostic and is left out; empty items are ignored.
     */
    static synchronized void configureSkip() {
        String value = getProperty(PROPERTY_CALLER_SKIP);
        Set<String> names = new HashSet<>();
        if (value != null) {
            for (String item : value.split(",")) {
                String name = item.trim();
                if (NAME.matcher(name).matches()) {
                    names.add(name);
                } else if (!name.isEmpty()) {
                    printInvalid(PROPERTY_CALLER_SKIP, name, "expected class or package names separated by commas");
                }
            }
        }
        configured = names;
        updateSkip();
    }

    /**
     * Skips the frames of these classes from now on; {@code null}s are ignored.
     */
    static synchronized void skipClasses(Class<?>[] classes) {
        if (classes != null) {
            for (Class<?> skipped : classes) {
                if (skipped != null) {
                    ADDED.add(skipped.getName());
                }
            }
        }
        updateSkip();
    }

    /**
     * Forgets the classes of {@link LOG#skipCaller(Class[])}; for tests.
     */
    static synchronized void forgetSkipped() {
        ADDED.clear();
        updateSkip();
    }

    private static void updateSkip() {
        Set<String> names = new HashSet<>(configured);
        names.addAll(ADDED);
        skip = new Skip(names);
    }

    /**
     * One walk down the stack: decides which frames are skipped, and remembers the class of a frame skipped by
     * {@code nanolog.caller.skip} right before the caller, the boundary for a location-aware SLF4J backend.
     */
    static final class Walk {

        private final Skip skipped = skip;
        private String boundary;

        /**
         * Whether a frame is skipped: it belongs to Nanolog, to the method handles that call its stack walker, or to a
         * skipped class.
         */
        boolean skips(Class frameClass, Class sourceClass) {
            if (isNanolog(sourceClass) || isMachinery(frameClass.getName())) {
                boundary = null;
                return true;
            }
            if (skipped.matches(frameClass)) {
                boundary = frameClass.getName();
                return true;
            }
            return false;
        }

        Caller caller(Class sourceClass, StackTraceElement frame) {
            return new Caller(sourceClass, frame, boundary);
        }
    }

    /**
     * A detected caller. When no frame qualifies, it has neither a class nor a frame.
     */
    static final class Caller {

        static final Caller NONE = new Caller(null, null);

        final Class sourceClass;
        final StackTraceElement frame;

        /**
         * The class of the skipped frame right before the caller, or {@code null} when that frame is Nanolog's.
         */
        private final String boundary;

        Caller(Class sourceClass, StackTraceElement frame) {
            this(sourceClass, frame, null);
        }

        Caller(Class sourceClass, StackTraceElement frame, String boundary) {
            this.sourceClass = sourceClass;
            this.frame = frame;
            this.boundary = boundary;
        }

        /**
         * The caller boundary of an entry, see {@link LogEntry#getCallerBoundary()}: the skipped class right before
         * the caller, or the class of Nanolog that the call went through.
         */
        String boundaryOr(String nanologBoundary) {
            return boundary != null ? boundary : nanologBoundary;
        }
    }

    /**
     * A way to find the caller.
     */
    interface Locator {

        Caller find();
    }

    /**
     * Whether a frame of this class belongs to Nanolog and is skipped. The frames of the locators are skipped too: a
     * walk starts in them.
     *
     * @param topLevel a top-level class
     */
    static boolean isNanolog(Class topLevel) {
        return topLevel.equals(LOG.class) || topLevel.equals(LogEntry.class) || topLevel.equals(CallerDetection.class) ||
               ILogger.class.isAssignableFrom(topLevel);
    }

    /**
     * Whether a frame belongs to the method handles through which {@link StackWalk} calls the stack walker: hidden on
     * a JVM, but shown in a native image of GraalVM. Their classes are in {@code java.lang.invoke} and
     * {@code com.oracle.svm}, or are the hidden {@code $$InjectedInvoker} that the JDK makes for the caller-sensitive
     * {@code StackWalker.getInstance}, named after the class of the lookup and without an enclosing class.
     */
    static boolean isMachinery(String className) {
        return className.startsWith("java.lang.invoke.") || className.startsWith("com.oracle.svm.") || className.contains("$$InjectedInvoker");
    }

    /**
     * Nested, anonymous and lambda classes count as their top-level enclosing class.
     */
    static Class topLevel(Class frameClass) {
        Class result = frameClass;
        while (result.getEnclosingClass() != null) {
            result = result.getEnclosingClass();
        }
        return result;
    }

    /**
     * The locator {@code LOG} uses: the stack walker where it works (Java 9+), the legacy walk where it works (Java 8),
     * the {@code Throwable} walk otherwise.
     */
    static Locator best() {
        return firstWorking(StackWalk::new, firstWorking(LegacyWalk::new, ThrowableWalk.INSTANCE));
    }

    /**
     * The preferred locator if it can be created and finds a caller once without failing, the fallback otherwise.
     */
    static Locator firstWorking(Supplier<Locator> preferred, Locator fallback) {
        try {
            Locator locator = preferred.get();
            locator.find();
            return locator;
        } catch (Throwable t) {
            // StackWalker is missing (Java 8), forbidden (a security manager) or not registered (a native image)
            return fallback;
        }
    }

    /**
     * Java 9+: a {@code StackWalker} that hands over the {@code Class} of each frame and stops at the first frame
     * outside Nanolog. No class is loaded by name, and only that one frame becomes a {@code StackTraceElement}, so the
     * cost hardly grows with the depth of the stack.
     * <p>
     * Nanolog is compiled for Java 8, so the walker is reached through method handles. On Java 8 creating this class
     * fails with an {@code ExceptionInInitializerError}, and {@link #best()} takes the {@code Throwable} walk.
     */
    static final class StackWalk implements Locator {

        private static final Object WALKER;
        private static final MethodHandle WALK;
        private static final MethodHandle DECLARING_CLASS;
        private static final MethodHandle TO_STACK_TRACE_ELEMENT;

        static {
            try {
                Class<?> walkerClass = Class.forName("java.lang.StackWalker");
                Class<?> optionClass = Class.forName("java.lang.StackWalker$Option");
                Class<?> frameClass = Class.forName("java.lang.StackWalker$StackFrame");
                // a full lookup: publicLookup() refuses getInstance, which is caller-sensitive
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                @SuppressWarnings("unchecked")
                Object retainClassReference = Enum.valueOf((Class) optionClass, "RETAIN_CLASS_REFERENCE");
                WALKER = lookup.findStatic(walkerClass, "getInstance", MethodType.methodType(walkerClass, optionClass))
                               .invoke(retainClassReference);
                WALK = lookup.findVirtual(walkerClass, "walk", MethodType.methodType(Object.class, Function.class));
                DECLARING_CLASS = lookup.findVirtual(frameClass, "getDeclaringClass", MethodType.methodType(Class.class));
                TO_STACK_TRACE_ELEMENT = lookup.findVirtual(frameClass, "toStackTraceElement",
                                                            MethodType.methodType(StackTraceElement.class));
            } catch (Throwable t) {
                throw new ExceptionInInitializerError(t);
            }
        }

        private static final Function<Stream<?>, Caller> FIRST_OUTSIDE_NANOLOG = StackWalk::firstOutsideNanolog;

        @Override
        public Caller find() {
            try {
                return (Caller) WALK.invoke(WALKER, FIRST_OUTSIDE_NANOLOG);
            } catch (Throwable t) {
                throw rethrow(t);
            }
        }

        private static Caller firstOutsideNanolog(Stream<?> frames) {
            try {
                Walk walk = new Walk();
                Iterator<?> iterator = frames.iterator();
                while (iterator.hasNext()) {
                    Object frame = iterator.next();
                    Class frameClass = (Class) DECLARING_CLASS.invoke(frame);
                    Class sourceClass = topLevel(frameClass);
                    if (!walk.skips(frameClass, sourceClass)) {
                        return walk.caller(sourceClass, (StackTraceElement) TO_STACK_TRACE_ELEMENT.invoke(frame));
                    }
                }
                return Caller.NONE;
            } catch (Throwable t) {
                throw rethrow(t);
            }
        }
    }

    /**
     * Throws a checked throwable of a method handle without wrapping it; {@code LOG} catches it and falls back to the
     * {@code Throwable} walk.
     */
    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException rethrow(Throwable t) throws T {
        throw (T) t;
    }

    /**
     * Java 8: the class of each frame from {@code sun.reflect.Reflection.getCallerClass(int)} and only the frames it
     * looks at from {@code sun.misc.JavaLangAccess.getStackTraceElement}, so no class is loaded by name and no
     * element is built for the frames below the caller: the cost grows far more slowly with the depth of the stack.
     * Both are internal APIs of Java 8, reached through method handles; on any other JVM creating this class fails
     * with an {@code ExceptionInInitializerError}, and {@link #best()} takes the {@code Throwable} walk.
     * <p>
     * {@code getCallerClass(i + 1)} and frame {@code i} of a {@code Throwable} made in {@link #find()} are the same
     * frame, except where {@code getCallerClass} skips the frames of reflection ({@code Method.invoke}): a class whose
     * name differs from the name in the frame gives way to {@link #fallback}.
     */
    static final class LegacyWalk implements Locator {

        private static final MethodHandle CALLER_CLASS;
        private static final MethodHandle ELEMENT;
        private static final Object ACCESS;

        static {
            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                CALLER_CLASS = lookup.findStatic(Class.forName("sun.reflect.Reflection"), "getCallerClass",
                                                 MethodType.methodType(Class.class, int.class));
                Class<?> accessClass = Class.forName("sun.misc.JavaLangAccess");
                ACCESS = lookup.findStatic(Class.forName("sun.misc.SharedSecrets"), "getJavaLangAccess",
                                           MethodType.methodType(accessClass)).invoke();
                ELEMENT = lookup.findVirtual(accessClass, "getStackTraceElement",
                                             MethodType.methodType(StackTraceElement.class, Throwable.class, int.class));
            } catch (Throwable t) {
                throw new ExceptionInInitializerError(t);
            }
        }

        /**
         * What finds the caller when the two views of the stack drift apart; replaced in tests.
         */
        static Locator fallback = ThrowableWalk.INSTANCE;

        @Override
        public Caller find() {
            try {
                Throwable stack = new Throwable();
                Walk walk = new Walk();
                // getCallerClass must be called right here: its depth counts from this frame, as the frames of stack do
                for (int i = 0; ; i++) {
                    Class frameClass = (Class) CALLER_CLASS.invoke(i + 1);
                    if (frameClass == null) {
                        return Caller.NONE;
                    }
                    StackTraceElement frame = (StackTraceElement) ELEMENT.invoke(ACCESS, stack, i);
                    if (!frameClass.getName().equals(frame.getClassName())) {
                        return fallback.find();
                    }
                    Class sourceClass = topLevel(frameClass);
                    if (!walk.skips(frameClass, sourceClass)) {
                        return walk.caller(sourceClass, frame);
                    }
                }
            } catch (Throwable t) {
                throw rethrow(t);
            }
        }
    }

    /**
     * Reads the stack trace of a new {@code Throwable} and resolves each frame's class by its name. Works on every
     * JVM, but builds an element for every frame of the stack and loads classes by name.
     */
    static final class ThrowableWalk implements Locator {

        static final ThrowableWalk INSTANCE = new ThrowableWalk();

        /**
         * Reads the frames of the {@code Throwable}. Tests replace it to simulate a JVM that records no frames
         * ({@code -XX:-StackTraceInThrowable}) and must restore it afterwards.
         */
        static Function<Throwable, StackTraceElement[]> stackTraceOf = Throwable::getStackTrace;

        private ThrowableWalk() {/**/}

        @Override
        public Caller find() {
            Walk walk = new Walk();
            for (StackTraceElement element : stackTraceOf.apply(new Throwable())) {
                Class frameClass = resolveClass(element.getClassName());
                if (frameClass == null) {
                    // the frame belongs to a class loader we cannot see (e.g. a child loader in an app server): skip it
                    continue;
                }
                Class sourceClass = topLevel(frameClass);
                if (!walk.skips(frameClass, sourceClass)) {
                    return walk.caller(sourceClass, element);
                }
            }
            // no frame qualifies (stack traces disabled, the code runs entirely inside an ILogger, or every class is
            // skipped): logging must not throw, so the entry goes out without a source class and call site, like LOG.log(level, null, ...)
            return Caller.NONE;
        }

        /**
         * Resolves a stack frame class, preferring the thread's context class loader (the application's loader in
         * containers) and falling back to the loader of {@code LOG} itself. Returns {@code null} if neither can see or
         * load it, whatever the failure.
         */
        static Class resolveClass(String className) {
            ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
            if (contextClassLoader != null) {
                try {
                    return Class.forName(className, false, contextClassLoader);
                } catch (Throwable e) {
                    // not visible from the context class loader, or it fails to load the class: try our own
                }
            }
            try {
                return Class.forName(className, false, LOG.class.getClassLoader());
            } catch (Throwable e) {
                // besides ClassNotFoundException: a LinkageError, GraalVM's MissingReflectionRegistrationError (an
                // Error), a SecurityException. Logging never throws, so the frame is skipped instead
                return null;
            }
        }
    }
}
