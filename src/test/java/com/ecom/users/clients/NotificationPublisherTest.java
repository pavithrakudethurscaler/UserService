package com.ecom.users.clients;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationPublisherTest {
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private final ObjectMapper mapper = new ObjectMapper();

    private NotificationPublisher publisher() {
        return new NotificationPublisher(kafka, mapper, "kafka", "ecom.user-events", "", "secret");
    }

    @Test
    void sendsJsonEnvelopeToTopicKeyedByUserId() throws Exception {
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(new CompletableFuture<>());

        publisher().publish("user-7-registered", "USER_REGISTERED", Map.of("userId", 7L, "email", "a@x.com"));

        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(eq("ecom.user-events"), eq("7"), value.capture());
        JsonNode json = mapper.readTree(value.getValue());
        assertThat(json.get("eventId").asText()).isEqualTo("user-7-registered");
        assertThat(json.get("type").asText()).isEqualTo("USER_REGISTERED");
        assertThat(json.get("occurredAt").asLong()).isPositive();
        assertThat(json.get("data").get("email").asText()).isEqualTo("a@x.com");
    }

    @Test
    void brokerFailuresNeverReachTheCaller() {
        when(kafka.send(anyString(), anyString(), anyString())).thenThrow(new RuntimeException("broker down"));
        assertThatCode(() -> publisher().publish("e1", "USER_REGISTERED", Map.of("userId", 1L)))
                .doesNotThrowAnyException();

        doReturn(CompletableFuture.failedFuture(new RuntimeException("timeout")))
                .when(kafka).send(anyString(), anyString(), anyString());
        assertThatCode(() -> publisher().publish("e2", "USER_REGISTERED", Map.of("userId", 1L)))
                .doesNotThrowAnyException();
    }
}
