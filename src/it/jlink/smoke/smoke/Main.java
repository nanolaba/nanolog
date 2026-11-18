package smoke;

import com.nanolaba.logging.LOG;

/**
 * Logs once from a runtime image that jlink built with Nanolog.
 */
public class Main {

    public static void main(String[] args) {
        LOG.info("Hello from a jlink image");
    }
}
