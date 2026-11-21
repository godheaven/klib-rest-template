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
import static org.junit.jupiter.api.Assertions.assertThrows;

import cl.kanopus.rest.api.exception.CustomClientException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.ResourceAccessException;

/** End-to-end tests over a real local HTTP server (timeouts, encoding, error bodies, closing). */
@SuppressWarnings("removal")
class HttpTransportTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/echo", exchange -> respond(exchange, 200, "text/plain;charset=UTF-8", exchange.getRequestURI().getRawQuery()));
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, "text/plain", "late");
        });
        server.createContext("/html-error", exchange -> respond(exchange, 502, "text/html;charset=UTF-8", "<html><body>Bad gateway ñ</body></html>"));
        server.createContext("/empty-error", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = (body != null ? body : "").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Test
    void queryValuesAreEncodedOverTheWire() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("q", "a&b=1");
        params.put("p", "1+1");
        try (RestClientApi api = new RestClientApi(baseUrl, HttpClientOptions.defaults()); LegacyApi legacy = new LegacyApi(baseUrl, HttpClientOptions.defaults())) {
            assertEquals("q=a%26b%3D1&p=1%2B1", api.sendGet("/echo", String.class, params));
            assertEquals("q=a%26b%3D1&p=1%2B1", legacy.sendGet("/echo", String.class, params));
        }
    }

    @Test
    void responseTimeoutIsApplied() {
        HttpClientOptions options = HttpClientOptions.builder().responseTimeout(Duration.ofMillis(200)).build();
        try (RestClientApi api = new RestClientApi(baseUrl, options); LegacyApi legacy = new LegacyApi(baseUrl, options)) {
            assertThrows(ResourceAccessException.class, () -> api.sendGet("/slow", String.class));
            assertThrows(ResourceAccessException.class, () -> legacy.sendGet("/slow", String.class));
        }
    }

    @Test
    void htmlAndEmptyErrorBodiesBecomeCustomClientException() {
        try (RestClientApi api = new RestClientApi(baseUrl, HttpClientOptions.defaults()); LegacyApi legacy = new LegacyApi(baseUrl, HttpClientOptions.defaults())) {
            CustomClientException html = assertThrows(CustomClientException.class, () -> api.sendGet("/html-error", String.class));
            CustomClientException empty = assertThrows(CustomClientException.class, () -> legacy.sendGet("/empty-error", String.class));
            CustomClientException legacyHtml = assertThrows(CustomClientException.class, () -> legacy.sendGet("/html-error", String.class));

            assertEquals(502, html.getStatus().value());
            assertEquals("<html><body>Bad gateway ñ</body></html>", html.getDetails().getMessage());
            assertEquals("/html-error", html.getDetails().getPath());
            assertEquals(500, empty.getStatus().value());
            assertEquals("Internal Server Error", empty.getDetails().getMessage());
            assertEquals("<html><body>Bad gateway ñ</body></html>", legacyHtml.getResponseBody());
        }
    }

    @Test
    void closedClientCannotBeReused() {
        RestClientApi api = new RestClientApi(baseUrl, HttpClientOptions.defaults());
        LegacyApi legacy = new LegacyApi(baseUrl, HttpClientOptions.defaults());
        assertEquals("x=1", api.sendGet("/echo?x=1", String.class));
        api.close();
        legacy.close();
        api.close();

        assertThrows(RuntimeException.class, () -> api.sendGet("/echo", String.class));
        assertThrows(RuntimeException.class, () -> legacy.sendGet("/echo", String.class));
    }

    private static final class RestClientApi extends AbstractRestClientApi {

        RestClientApi(String url, HttpClientOptions options) {
            super(url, options);
        }

        @Override
        protected HttpHeaders createHeaders() {
            return new HttpHeaders();
        }
    }

    private static final class LegacyApi extends AbstractApi {

        LegacyApi(String url, HttpClientOptions options) {
            super(url, options);
        }

        @Override
        protected HttpHeaders createHeaders() {
            return new HttpHeaders();
        }
    }
}
