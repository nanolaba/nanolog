/**
 * Nanolog: a zero-dependency logging facade, {@link com.nanolaba.logging.LOG}. The module needs only
 * {@code java.base}. SLF4J, for {@code Slf4jLogger}, and JUnit, for {@code NanologExtension} and the run summary of
 * tests, are optional: they are read when present.
 * <p>
 * The services of JUnit are not declared here, since a module that provides a service of an absent module fails to
 * resolve: on the module path, register {@code NanologExtension} with {@code @ExtendWith}.
 */
module com.nanolaba.nanolog {
    requires static org.slf4j;
    requires static org.junit.jupiter.api;
    requires static org.junit.platform.launcher;

    exports com.nanolaba.logging;
}
