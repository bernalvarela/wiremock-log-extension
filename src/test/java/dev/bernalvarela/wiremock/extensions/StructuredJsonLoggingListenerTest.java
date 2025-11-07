package dev.bernalvarela.wiremock.extensions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.IOException;
import java.util.UUID;

import static dev.bernalvarela.wiremock.extensions.StructuredJsonLoggingListenerTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class StructuredJsonLoggingListenerTest {

    private StructuredJsonLoggingListener listener;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;

    @BeforeEach
    void setUp() {
        listener = new StructuredJsonLoggingListener();
        System.setOut(new PrintStream(outContent));
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
    }

    @Test
    void givenTextRequestAndResponse_whenBeforeResponseSent_thenLogsFullJson() throws IOException {
        // When
        listener.beforeResponseSent(TEXT_SERVE_EVENT, null);

        // Then
        String jsonOutput = outContent.toString().trim();
        JsonNode rootNode = objectMapper.readTree(jsonOutput);

        assertThat(rootNode.get("service").asText()).isEqualTo("wiremock");
        assertThat(rootNode.get("wasMatched").asBoolean()).isTrue();
        assertThat(rootNode.get("request").get("method").asText()).isEqualTo("GET");
        assertThat(rootNode.get("request").get("body").asText()).isEqualTo("{\"key\":\"value\"}");
        assertThat(rootNode.get("response").get("status").asInt()).isEqualTo(200);
        assertThat(rootNode.get("response").get("body").asText()).isEqualTo("{\"status\":\"ok\"}");
    }

    @Test
    void givenMultipartRequest_whenBeforeResponseSent_thenLogsRequestWithBinaryPlaceholder() throws IOException {
        // When
        listener.beforeResponseSent(MULTIPART_SERVE_EVENT, null);

        // Then
        String jsonOutput = outContent.toString().trim();
        JsonNode rootNode = objectMapper.readTree(jsonOutput);

        assertThat(rootNode.get("request").get("body").asText()).isEqualTo("<binary content not logged>");
    }

    @Test
    void givenPdfRequest_whenBeforeResponseSent_thenLogsRequestWithBinaryPlaceholder() throws IOException {
        // When
        listener.beforeResponseSent(PDF_SERVE_EVENT, null);

        // Then
        String jsonOutput = outContent.toString().trim();
        JsonNode rootNode = objectMapper.readTree(jsonOutput);

        assertThat(rootNode.get("request").get("body").asText()).isEqualTo("<binary content not logged>");
    }

    @Test
    void givenResponseWithEmptyHeaders_whenBeforeResponseSent_thenLogsEmptyResponseHeaders() throws IOException {
        // When
        listener.beforeResponseSent(EMPTY_HEADERS_SERVE_EVENT, null);

        // Then
        String jsonOutput = outContent.toString().trim();
        JsonNode rootNode = objectMapper.readTree(jsonOutput);

        assertThat(rootNode.get("response").get("headers").isEmpty()).isTrue();
    }

    @Test
    void givenResponseWithTopLevelBase64_whenBeforeResponseSent_thenSanitizesField() throws IOException {
        // When
        listener.beforeResponseSent(TOP_LEVEL_BASE64_EVENT, null);

        // Then
        String jsonOutput = outContent.toString().trim();
        JsonNode responseBody = objectMapper.readTree(objectMapper.readTree(jsonOutput).get("response").get("body").asText());

        assertThat(responseBody.get("documentoFirmadoBase64").asText()).isEqualTo("<base64_data_omitted>");
        assertThat(responseBody.get("other").asText()).isEqualTo("value");
    }

    @Test
    void givenResponseWithNestedBase64_whenBeforeResponseSent_thenSanitizesField() throws IOException {
        // When
        listener.beforeResponseSent(NESTED_BASE64_EVENT, null);

        // Then
        String jsonOutput = outContent.toString().trim();
        JsonNode responseBody = objectMapper.readTree(objectMapper.readTree(jsonOutput).get("response").get("body").asText());

        assertThat(responseBody.get("metadata").get("signature").asText()).isEqualTo("<base64_data_omitted>");
        assertThat(responseBody.get("other").asText()).isEqualTo("value");
    }

    @Test
    void givenResponseWithShortBase64_whenBeforeResponseSent_thenDoesNotSanitizeField() throws IOException {
        // When
        listener.beforeResponseSent(SHORT_BASE64_EVENT, null);

        // Then
        String jsonOutput = outContent.toString().trim();
        JsonNode responseBody = objectMapper.readTree(objectMapper.readTree(jsonOutput).get("response").get("body").asText());

        assertThat(responseBody.get("token").asText()).isNotEqualTo("<base64_data_omitted>");
        assertThat(responseBody.get("token").asText()).isEqualTo(SHORT_VALID_BASE64_STRING);
    }

    @Test
    void givenResponseWithInvalidBase64_whenBeforeResponseSent_thenDoesNotSanitizeField() throws IOException {
        // When
        listener.beforeResponseSent(INVALID_BASE64_EVENT, null);

        // Then
        String jsonOutput = outContent.toString().trim();
        JsonNode responseBody = objectMapper.readTree(objectMapper.readTree(jsonOutput).get("response").get("body").asText());

        assertThat(responseBody.get("data").asText()).isNotEqualTo("<base64_data_omitted>");
        assertThat(responseBody.get("data").asText()).isEqualTo(LONG_INVALID_BASE64_STRING);
    }
}
