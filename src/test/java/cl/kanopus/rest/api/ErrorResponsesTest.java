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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cl.kanopus.rest.api.exception.CustomClientException;
import cl.kanopus.rest.api.exception.ErrorMessage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;

class ErrorResponsesTest {

    @Test
    void emptyBodyProducesSyntheticDetails() {
        ErrorMessage message = ErrorResponses.parse(HttpStatus.SERVICE_UNAVAILABLE, "  ", "/p");

        assertEquals(503, message.getStatus());
        assertEquals("Service Unavailable", message.getError());
        assertEquals("Service Unavailable", message.getMessage());
        assertEquals("/p", message.getPath());
        assertNotNull(message.getTimestamp());
        assertEquals(null, ErrorResponses.parse(null, null, null).getError());
    }

    @Test
    void nonJsonBodyIsKeptAsTruncatedText() {
        String html = "<html><body>" + "x".repeat(2000) + "</body></html>";

        ErrorMessage message = ErrorResponses.parse(HttpStatus.BAD_GATEWAY, html, "/p");

        assertEquals("Bad Gateway", message.getError());
        assertTrue(message.getMessage().startsWith("<html><body>"));
        assertTrue(message.getMessage().endsWith("...(truncated)"));
        assertTrue(message.getMessage().length() < 600);
    }

    @Test
    void invalidJsonAndNonObjectJsonFallBackToText() {
        assertEquals("{not json", ErrorResponses.parse(HttpStatus.BAD_REQUEST, "{not json", null).getMessage());
        assertEquals("[1,2]", ErrorResponses.parse(HttpStatus.BAD_REQUEST, "[1,2]", null).getMessage());
    }

    @Test
    void jsonFieldsAreMappedTolerantly() {
        ErrorMessage message = ErrorResponses.parse(HttpStatus.CONFLICT,
                "{\"timestamp\":\"2025-01-02T03:04:05.000+00:00\",\"status\":409,\"error\":\"Conflict\",\"message\":\"dup\",\"path\":\"/a\",\"extra\":{\"k\":1}}", "/b");

        assertEquals(409, message.getStatus());
        assertEquals("Conflict", message.getError());
        assertEquals("dup", message.getMessage());
        assertEquals("/a", message.getPath());
        assertEquals(1735787045000L, message.getTimestamp().getTime());
    }

    @Test
    void missingOrOddJsonFieldsUseFallbacks() {
        ErrorMessage message = ErrorResponses.parse(HttpStatusCode.valueOf(599), "{\"timestamp\":\"not-a-date\",\"message\":{\"detail\":\"x\"}}", "/b");

        assertEquals(599, message.getStatus());
        assertEquals("HTTP 599", message.getError());
        assertEquals("{\"detail\":\"x\"}", message.getMessage());
        assertEquals("/b", message.getPath());
        assertNull(message.getTimestamp());

        assertEquals(0L, ErrorResponses.parse(HttpStatus.BAD_REQUEST, "{\"timestamp\":0}", null).getTimestamp().getTime());
    }

    @Test
    void bodyIsDecodedWithDeclaredCharsetOrUtf8() throws Exception {
        assertEquals("canción", ErrorResponses.readBody(response("canción".getBytes(StandardCharsets.UTF_8), null)));
        assertEquals("canción", ErrorResponses.readBody(response("canción".getBytes(StandardCharsets.ISO_8859_1), "text/plain;charset=ISO-8859-1")));
        assertEquals("ok", ErrorResponses.readBody(response("ok".getBytes(StandardCharsets.UTF_8), "not a media type")));

        ClientHttpResponse noBody = mock(ClientHttpResponse.class);
        when(noBody.getBody()).thenReturn(null);
        assertEquals("", ErrorResponses.readBody(noBody));
    }

    @Test
    void toExceptionLogsTruncatedSummary() throws Exception {
        Logger log = mock(Logger.class);
        ClientHttpResponse response = response("{\"message\":\"boom\"}".getBytes(StandardCharsets.UTF_8), "application/json");

        CustomClientException server = ErrorResponses.toException(response, HttpStatus.INTERNAL_SERVER_ERROR, HttpMethod.GET, URI.create("http://h/x?secret=1"), log);
        CustomClientException client = ErrorResponses.toException(response("".getBytes(), null), HttpStatus.NOT_FOUND, HttpMethod.GET, null, null);

        assertEquals("boom", server.getMessage());
        assertEquals("{\"message\":\"boom\"}", server.getResponseBody());
        assertEquals("Not Found", client.getMessage());
        org.mockito.Mockito.verify(log).error("API Error: {} {} {} - {}", 500, HttpMethod.GET, "/x", "boom");
    }

    @Test
    void truncateHandlesNullAndShortValues() {
        assertNull(ErrorResponses.truncate(null, 3));
        assertEquals("abc", ErrorResponses.truncate("abc", 3));
        assertEquals("ab...(truncated)", ErrorResponses.truncate("abc", 2));
    }

    private static ClientHttpResponse response(byte[] body, String contentType) throws Exception {
        ClientHttpResponse response = mock(ClientHttpResponse.class);
        HttpHeaders headers = new HttpHeaders();
        if (contentType != null) {
            headers.set(HttpHeaders.CONTENT_TYPE, contentType);
        }
        when(response.getHeaders()).thenReturn(headers);
        when(response.getBody()).thenReturn(new ByteArrayInputStream(body));
        return response;
    }
}
