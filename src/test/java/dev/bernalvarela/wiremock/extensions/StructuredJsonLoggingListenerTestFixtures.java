package dev.bernalvarela.wiremock.extensions;

import com.github.tomakehurst.wiremock.http.*;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.UUID;

import static org.mockito.Mockito.when;

public final class StructuredJsonLoggingListenerTestFixtures {

    // Prevenir instanciación
    private StructuredJsonLoggingListenerTestFixtures() {}

    // --- Mocks y Helpers ---
    private static final StubMapping MATCHED_STUB_MAPPING = Mockito.mock(StubMapping.class);
    static final String LONG_BASE64_STRING = Base64.getEncoder()
        .encodeToString(
            String.join(
                "",
                Collections.nCopies(101, "a")
            ).getBytes(StandardCharsets.UTF_8)
        );
    static final String LONG_INVALID_BASE64_STRING = String.join("", Collections.nCopies(201, "*"));
    static final String SHORT_VALID_BASE64_STRING = "dGVzdA=="; // "test"

    private static LoggedRequest createLoggedRequest(RequestMethod method, String url, String contentType, byte[] body) {
        Request requestMock = Mockito.mock(Request.class);
        when(requestMock.getMethod()).thenReturn(method);
        when(requestMock.getUrl()).thenReturn(url);
        when(requestMock.getAbsoluteUrl()).thenReturn("http://localhost:8080" + url);
        when(requestMock.getClientIp()).thenReturn("127.0.0.1");
        if (contentType != null) {
            when(requestMock.getHeaders()).thenReturn(new HttpHeaders(new HttpHeader("Content-Type", contentType)));
        } else {
            when(requestMock.getHeaders()).thenReturn(new HttpHeaders());
        }
        when(requestMock.getBody()).thenReturn(body);
        return LoggedRequest.createFrom(requestMock);
    }

    // --- REQUEST FIXTURES ---
    static final LoggedRequest TEXT_REQUEST = createLoggedRequest(RequestMethod.GET, "/test", "application/json", "{\"key\":\"value\"}".getBytes());
    static final LoggedRequest MULTIPART_REQUEST = createLoggedRequest(RequestMethod.POST, "/upload", "multipart/form-data", new byte[]{1, 2, 3});
    static final LoggedRequest PDF_REQUEST = createLoggedRequest(RequestMethod.POST, "/upload", "application/pdf", new byte[]{1, 2, 3});
    static final LoggedRequest REQUEST_WITH_NO_HEADERS = createLoggedRequest(RequestMethod.GET, "/test", null, new byte[0]);

    // --- RESPONSE FIXTURES ---
    static final LoggedResponse TEXT_RESPONSE = new LoggedResponse(
        200,
        new HttpHeaders(new HttpHeader("Content-Type", "application/json")),
        Base64.getEncoder().encodeToString("{\"status\":\"ok\"}".getBytes()),
        null, null
    );

    static final LoggedResponse EMPTY_RESPONSE = new LoggedResponse(204, new HttpHeaders(), null, null, null);

    static final LoggedResponse RESPONSE_WITH_EMPTY_HEADERS = new LoggedResponse(200, new HttpHeaders(), null, null, null);

    private static String createJsonPayload(String key, String value) {
        return String.format("{\"%s\":\"%s\"}", key, value);
    }

    static final LoggedResponse RESPONSE_WITH_TOP_LEVEL_BASE64 = new LoggedResponse(200, new HttpHeaders(),
        Base64.getEncoder().encodeToString(String.format("{\"documentoFirmadoBase64\":\"%s\", \"other\":\"value\"}", LONG_BASE64_STRING).getBytes()),
        null, null);

    static final LoggedResponse RESPONSE_WITH_NESTED_BASE64 = new LoggedResponse(200, new HttpHeaders(),
        Base64.getEncoder().encodeToString(String.format("{\"metadata\":{\"signature\":\"%s\"}, \"other\":\"value\"}", LONG_BASE64_STRING).getBytes()),
        null, null);

    static final LoggedResponse RESPONSE_WITH_SHORT_BASE64 = new LoggedResponse(200, new HttpHeaders(),
        Base64.getEncoder().encodeToString(createJsonPayload("token", SHORT_VALID_BASE64_STRING).getBytes()),
        null, null);

    static final LoggedResponse RESPONSE_WITH_INVALID_BASE64 = new LoggedResponse(200, new HttpHeaders(),
        Base64.getEncoder().encodeToString(createJsonPayload("data", LONG_INVALID_BASE64_STRING).getBytes()),
        null, null);

    // --- SERVE EVENT FIXTURES ---
    private static ServeEvent createEvent(LoggedRequest request, LoggedResponse response) {
        ResponseDefinition responseDefinition = Mockito.mock(ResponseDefinition.class);
        when(responseDefinition.wasConfigured()).thenReturn(true);
        return new ServeEvent(UUID.randomUUID(), request, MATCHED_STUB_MAPPING, responseDefinition, response, true, null, null);
    }

    static final ServeEvent TEXT_SERVE_EVENT = createEvent(TEXT_REQUEST, TEXT_RESPONSE);
    static final ServeEvent MULTIPART_SERVE_EVENT = createEvent(MULTIPART_REQUEST, EMPTY_RESPONSE);
    static final ServeEvent PDF_SERVE_EVENT = createEvent(PDF_REQUEST, EMPTY_RESPONSE);
    static final ServeEvent EMPTY_HEADERS_SERVE_EVENT = createEvent(REQUEST_WITH_NO_HEADERS, RESPONSE_WITH_EMPTY_HEADERS);
    static final ServeEvent TOP_LEVEL_BASE64_EVENT = createEvent(TEXT_REQUEST, RESPONSE_WITH_TOP_LEVEL_BASE64);
    static final ServeEvent NESTED_BASE64_EVENT = createEvent(TEXT_REQUEST, RESPONSE_WITH_NESTED_BASE64);
    static final ServeEvent SHORT_BASE64_EVENT = createEvent(TEXT_REQUEST, RESPONSE_WITH_SHORT_BASE64);
    static final ServeEvent INVALID_BASE64_EVENT = createEvent(TEXT_REQUEST, RESPONSE_WITH_INVALID_BASE64);
}
