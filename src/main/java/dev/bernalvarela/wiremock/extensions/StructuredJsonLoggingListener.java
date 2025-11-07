package dev.bernalvarela.wiremock.extensions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ServeEventListener;
import com.github.tomakehurst.wiremock.http.ContentTypeHeader;
import com.github.tomakehurst.wiremock.http.LoggedResponse;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class StructuredJsonLoggingListener implements ServeEventListener {

    private final ObjectMapper mapper = new ObjectMapper();

    // Constants for structured logging
    private static final String LISTENER_NAME = "structured-json-logging";
    private static final String TIMESTAMP_FIELD = "@timestamp";
    private static final String SERVICE_FIELD = "service";
    private static final String SERVICE_NAME = "wiremock";
    private static final String WAS_MATCHED_FIELD = "wasMatched";
    private static final String REQUEST_ID_FIELD = "requestId";
    private static final String REQUEST_FIELD = "request";
    private static final String METHOD_FIELD = "method";
    private static final String URL_FIELD = "url";
    private static final String CLIENT_IP_FIELD = "clientIp";
    private static final String HEADERS_FIELD = "headers";
    private static final String BODY_FIELD = "body";
    private static final String RESPONSE_FIELD = "response";
    private static final String STATUS_FIELD = "status";
    private static final String ERROR_MESSAGE_PREFIX = "Error in StructuredJsonLoggingListener: ";

    // Constants for data sanitization
    private static final String BINARY_BODY_PLACEHOLDER = "<binary content not logged>";
    private static final String BASE64_PLACEHOLDER = "<base64_data_omitted>";
    // Minimum threshold to consider a string as a potential Base64. Avoids false positives.
    private static final int BASE64_MIN_LENGTH_THRESHOLD = 100;
    private static final String CONTENT_TYPE_MULTIPART = "multipart/form-data";
    private static final String CONTENT_TYPE_PDF = "application/pdf";
    private static final String CONTENT_TYPE_IMAGE = "image/";

    @Override
    public String getName() {
        return LISTENER_NAME;
    }

    @Override
    public void beforeResponseSent(ServeEvent serveEvent, Parameters parameters) {
        try {
            ObjectNode rootNode = mapper.createObjectNode();

            LoggedRequest request = serveEvent.getRequest();
            LoggedResponse response = serveEvent.getResponse();

            // 1. General Information
            rootNode.put(TIMESTAMP_FIELD, Instant.now().toString());
            rootNode.put(SERVICE_FIELD, SERVICE_NAME);
            rootNode.put(WAS_MATCHED_FIELD, serveEvent.getWasMatched());
            rootNode.put(REQUEST_ID_FIELD, UUID.randomUUID().toString());

            // 2. Request Information
            ObjectNode requestNode = rootNode.putObject(REQUEST_FIELD);
            requestNode.put(METHOD_FIELD, request.getMethod().getName());
            requestNode.put(URL_FIELD, request.getAbsoluteUrl());
            requestNode.put(CLIENT_IP_FIELD, request.getClientIp());

            ObjectNode requestHeaders = requestNode.putObject(HEADERS_FIELD);
            if (request.getHeaders() != null) {
                request.getHeaders().all().forEach(h -> requestHeaders.put(h.key(), h.firstValue()));
            }
            requestNode.put(BODY_FIELD, getRequestBody(request));

            // 3. Response Information
            ObjectNode responseNode = rootNode.putObject(RESPONSE_FIELD);
            responseNode.put(STATUS_FIELD, response.getStatus());

            ObjectNode responseHeaders = responseNode.putObject(HEADERS_FIELD);
            if (response.getHeaders() != null) {
                response.getHeaders().all().forEach(h -> responseHeaders.put(h.key(), h.firstValue()));
            }
            responseNode.put(BODY_FIELD, getSanitizedResponseBody(response));

            String jsonLog = mapper.writeValueAsString(rootNode);
            System.out.println(jsonLog);

        } catch (Exception e) {
            System.err.println(ERROR_MESSAGE_PREFIX + e.getMessage());
        }
    }

    private String getRequestBody(LoggedRequest request) {
        if (Objects.isNull(request)) {
            return "";
        }

        if (Objects.isNull(request.getHeaders()) ||
            Objects.isNull(request.getHeaders().getContentTypeHeader())
        ) {
            return request.getBodyAsString();
        }

        ContentTypeHeader contentTypeHeader = request.getHeaders().getContentTypeHeader();
        if (contentTypeHeader != null && contentTypeHeader.isPresent() && contentTypeHeader.firstValue() != null) {
            String contentType = contentTypeHeader.firstValue().toLowerCase();
            if (contentType.startsWith(CONTENT_TYPE_MULTIPART) || contentType.startsWith(CONTENT_TYPE_PDF) || contentType.startsWith(CONTENT_TYPE_IMAGE)) {
                return BINARY_BODY_PLACEHOLDER;
            }
        }
        return request.getBodyAsString();
    }

    private String getSanitizedResponseBody(LoggedResponse response) {
        String originalBody = response.getBodyAsString();
        if (originalBody == null || originalBody.isEmpty()) {
            return originalBody;
        }

        try {
            JsonNode rootNode = mapper.readTree(originalBody);
            sanitizeNode(rootNode);
            return mapper.writeValueAsString(rootNode);
        } catch (IOException e) {
            // Not a valid JSON, treat it as plain text and return as is.
            return originalBody;
        }
    }

    private void sanitizeNode(JsonNode node) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode childNode = field.getValue();
                if (childNode.isTextual() && isLikelyBase64(childNode.asText())) {
                    ((ObjectNode) node).put(field.getKey(), BASE64_PLACEHOLDER);
                } else {
                    sanitizeNode(childNode);
                }
            }
        } else if (node.isArray()) {
            for (JsonNode element : node) {
                sanitizeNode(element);
            }
        }
    }

    private boolean isLikelyBase64(String value) {
        if (value == null || value.length() < BASE64_MIN_LENGTH_THRESHOLD) {
            return false;
        }
        try {
            Base64.getDecoder().decode(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
