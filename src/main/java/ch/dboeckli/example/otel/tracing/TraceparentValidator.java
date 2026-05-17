package ch.dboeckli.example.otel.tracing;

import java.util.regex.Pattern;

public class TraceparentValidator {

    private static final Pattern TRACEPARENT_PATTERN = Pattern.compile("^00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$");

    public static boolean isValidTraceparent(String traceparent) {
        if (traceparent == null || traceparent.isEmpty()) {
            return false;
        }

        return TRACEPARENT_PATTERN.matcher(traceparent).matches();
    }

    public static TraceparentInfo parseTraceparent(String traceparent) {
        if (!isValidTraceparent(traceparent)) {
            throw new IllegalArgumentException("Invalid traceparent format: " + traceparent);
        }

        String[] parts = traceparent.split("-");
        return new TraceparentInfo(parts[0], // version
                parts[1], // traceId
                parts[2], // spanId
                parts[3] // flags
        );
    }

    public static class TraceparentInfo {

        private final String version;

        private final String traceId;

        private final String spanId;

        private final String flags;

        public TraceparentInfo(String version, String traceId, String spanId, String flags) {
            this.version = version;
            this.traceId = traceId;
            this.spanId = spanId;
            this.flags = flags;
        }

        // Getters
        public String getVersion() {
            return version;
        }

        public String getTraceId() {
            return traceId;
        }

        public String getSpanId() {
            return spanId;
        }

        public String getFlags() {
            return flags;
        }

        public boolean isSampled() {
            return "01".equals(flags);
        }

    }

}
