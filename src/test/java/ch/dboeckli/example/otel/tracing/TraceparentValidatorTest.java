package ch.dboeckli.example.otel.tracing;

import ch.dboeckli.example.otel.tracing.TraceparentValidator.TraceparentInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TraceparentValidatorTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    private static final String SPAN_ID = "00f067aa0ba902b7";

    private static final String VALID_TRACEPARENT = "00-" + TRACE_ID + "-" + SPAN_ID + "-01";

    @Test
    void acceptsAValidTraceparent() {
        assertThat(TraceparentValidator.isValidTraceparent(VALID_TRACEPARENT)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "invalid", "00-" + TRACE_ID + "-" + SPAN_ID + "-zz",
            "01-" + TRACE_ID + "-" + SPAN_ID + "-01", "00-" + TRACE_ID + "-" + SPAN_ID })
    void rejectsAnInvalidTraceparent(String traceparent) {
        assertThat(TraceparentValidator.isValidTraceparent(traceparent)).isFalse();
    }

    @Test
    void parsesAllFieldsFromAValidTraceparent() {
        TraceparentInfo info = TraceparentValidator.parseTraceparent(VALID_TRACEPARENT);

        assertThat(info.getVersion()).isEqualTo("00");
        assertThat(info.getTraceId()).isEqualTo(TRACE_ID);
        assertThat(info.getSpanId()).isEqualTo(SPAN_ID);
        assertThat(info.getFlags()).isEqualTo("01");
    }

    @Test
    void reportsTraceAsSampledWhenFlagsAreOne() {
        TraceparentInfo info = TraceparentValidator.parseTraceparent(VALID_TRACEPARENT);

        assertThat(info.isSampled()).isTrue();
    }

    @Test
    void reportsTraceAsNotSampledWhenFlagsAreZero() {
        TraceparentInfo info = TraceparentValidator.parseTraceparent("00-" + TRACE_ID + "-" + SPAN_ID + "-00");

        assertThat(info.isSampled()).isFalse();
    }

    @Test
    void rejectsParsingAnInvalidTraceparent() {
        assertThatThrownBy(() -> TraceparentValidator.parseTraceparent("invalid"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid traceparent format");
    }

}
