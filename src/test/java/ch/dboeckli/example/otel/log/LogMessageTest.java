package ch.dboeckli.example.otel.log;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogMessageTest {

    @Test
    void getMessageContainsLogIdPrefix() {
        assertThat(LogMessage.READING_CONFIG_CLASS.getMessage()).startsWith("[logid=10001]");
        assertThat(LogMessage.RECEIVED_REFRESH_EVENT_FOR_CONFIG_MAP.getMessage()).startsWith("[logid=10002]");
        assertThat(LogMessage.RECEIVED_CONTEXT_REFRESH_EVENT.getMessage()).startsWith("[logid=10003]");
    }

    @Test
    void getMessageKeepsFormattingTemplate() {
        assertThat(LogMessage.READING_CONFIG_CLASS.getMessage())
            .isEqualTo("[logid=10001] Reading configuration from {} config map: {}");
    }

    @Test
    void idsAreUnique() {
        assertThat(LogMessage.READING_CONFIG_CLASS).isNotEqualTo(LogMessage.RECEIVED_REFRESH_EVENT_FOR_CONFIG_MAP)
            .isNotEqualTo(LogMessage.RECEIVED_CONTEXT_REFRESH_EVENT);
    }

}
