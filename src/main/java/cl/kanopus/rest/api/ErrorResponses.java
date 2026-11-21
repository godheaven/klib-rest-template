/*-
 * !--
 * For support and inquiries regarding this library, please contact:
 *   soporte@kanopus.cl
 *
 * Project website:
 *   https://www.kanopus.cl
 * %%
 * Copyright (C) 2025 Pablo Díaz Saavedra
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * --!
 */
package cl.kanopus.rest.api;

import cl.kanopus.rest.api.exception.CustomClientException;
import cl.kanopus.rest.api.exception.ErrorMessage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.slf4j.Logger;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Converts HTTP error responses into {@link CustomClientException}. Parsing is tolerant: empty bodies, non-JSON bodies (HTML error pages, plain text) and unexpected JSON shapes never fail; the raw body is kept (truncated) instead.
 */
final class ErrorResponses {

    /** Maximum number of characters of an error body that are kept in messages and logs. */
    static final int MAX_BODY_LENGTH = 512;

    /** Hard limit of bytes read from an error response. */
    static final int MAX_READ_BYTES = 64 * 1024;

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private ErrorResponses() {
    }

    static CustomClientException toException(ClientHttpResponse response, HttpStatusCode status, HttpMethod method, URI uri, Logger log) throws IOException {
        String body = readBody(response);
        ErrorMessage errorMessage = parse(status, body, uri != null ? uri.getPath() : null);
        if (log != null) {
            String summary = truncate(errorMessage.getMessage(), MAX_BODY_LENGTH);
            String target = uri != null ? uri.getPath() : null;
            if (status.is5xxServerError()) {
                log.error("API Error: {} {} {} - {}", status.value(), method, target, summary);
            } else {
                log.warn("API Error: {} {} {} - {}", status.value(), method, target, summary);
            }
        }
        return new CustomClientException(status, errorMessage, truncate(body, MAX_BODY_LENGTH));
    }

    static String readBody(ClientHttpResponse response) throws IOException {
        InputStream in = response.getBody();
        if (in == null) {
            return "";
        }
        byte[] bytes = in.readNBytes(MAX_READ_BYTES);
        return new String(bytes, charset(response));
    }

    private static Charset charset(ClientHttpResponse response) {
        try {
            MediaType contentType = response.getHeaders().getContentType();
            if (contentType != null && contentType.getCharset() != null) {
                return contentType.getCharset();
            }
        } catch (RuntimeException ignored) {
            // invalid Content-Type header: fall back to UTF-8
        }
        return StandardCharsets.UTF_8;
    }

    static ErrorMessage parse(HttpStatusCode status, String body, String path) {
        int statusValue = status != null ? status.value() : 0;
        String reason = reasonPhrase(status);
        if (body == null || body.isBlank()) {
            return new ErrorMessage(new Date(), statusValue, reason, reason, path);
        }
        String trimmed = body.strip();
        if (trimmed.startsWith("{")) {
            try {
                JsonNode node = MAPPER.readTree(trimmed);
                if (node != null && node.isObject()) {
                    return new ErrorMessage(timestamp(node.get("timestamp")), node.path("status").isNumber() ? node.path("status").asInt() : statusValue, text(node.get("error"), reason),
                            text(node.get("message"), null), text(node.get("path"), path));
                }
            } catch (RuntimeException ignored) {
                // not valid JSON: fall through and use the raw text
            }
        }
        return new ErrorMessage(new Date(), statusValue, reason, truncate(trimmed, MAX_BODY_LENGTH), path);
    }

    private static String text(JsonNode node, String fallback) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return fallback;
        }
        return node.isValueNode() ? node.asString() : node.toString();
    }

    private static Date timestamp(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return MAPPER.treeToValue(node, Date.class);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String reasonPhrase(HttpStatusCode status) {
        if (status == null) {
            return null;
        }
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return resolved != null ? resolved.getReasonPhrase() : "HTTP " + status.value();
    }

    static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "...(truncated)";
    }
}
