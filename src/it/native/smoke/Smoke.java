package smoke;

import com.nanolaba.logging.LOG;

/**
 * Logs through Nanolog from a native image: the source class comes from caller detection, the settings from the
 * nanolog.properties beside this class, and the logger from nanolog.logger when the image runs.
 */
public class Smoke {

    public static void main(String[] args) {
        LOG.info("Hello from a native image");
        LOG.warn("Answer {}", 42);
        LOG.error(new IllegalStateException("boom"), "Failed");
    }
}
