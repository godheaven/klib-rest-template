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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import cl.kanopus.common.data.Searcher;
import cl.kanopus.rest.api.exception.CustomClientException;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

/** Regression tests for the 4.20.0 fixes in the deprecated {@link AbstractApi}. */
@SuppressWarnings("removal")
class AbstractApiRegressionTest {

    @Test
    void sendPutWithoutRequestSendsNoBody() {
        Api api = new Api("http://localhost");
        MockRestServiceServer server = MockRestServiceServer.createServer(api.restTemplate());
        server.expect(requestTo("http://localhost/r/1")).andExpect(method(HttpMethod.PUT)).andExpect(content().string("")).andRespond(withSuccess());

        api.sendPut("/r/1");

        server.verify();
    }

    @Test
    void queryValuesWithReservedCharactersAreEncoded() {
        Api api = new Api("http://localhost");
        MockRestServiceServer server = MockRestServiceServer.createServer(api.restTemplate());
        server.expect(requestTo("http://localhost/search?q=a%26role%3Dadmin&sum=1%2B1")).andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("q", "a&role=admin");
        params.put("sum", "1+1");

        assertEquals("ok", api.sendGet("/search", String.class, params));
        server.verify();
    }

    @Test
    void nullParamsAndNullSearcherFieldsAreIgnored() {
        Api api = new Api("http://localhost");
        MockRestServiceServer server = MockRestServiceServer.createServer(api.restTemplate());
        server.expect(requestTo("http://localhost/a")).andRespond(withSuccess("1", MediaType.TEXT_PLAIN));
        server.expect(requestTo("http://localhost/b?limit=5")).andRespond(withSuccess("2", MediaType.TEXT_PLAIN));
        server.expect(requestTo("http://localhost/c?x=1")).andRespond(withSuccess("3", MediaType.TEXT_PLAIN));

        Searcher<Object> searcher = new Searcher<>();
        searcher.setLimit(5);
        searcher.setOffset(null);
        Map<String, Object> params = new HashMap<>();
        params.put("x", 1);
        params.put("y", null);

        assertEquals("1", api.sendGet("/a", String.class, null));
        assertEquals("2", api.sendGet("/b", new ParameterizedTypeReference<String>() {
        }, searcher));
        assertEquals("3", api.sendGet("/c", String.class, params));
        server.verify();
    }

    @Test
    void nonJsonErrorBodyDoesNotFailParsing() {
        Api api = new Api("http://localhost");
        MockRestServiceServer server = MockRestServiceServer.createServer(api.restTemplate());
        server.expect(requestTo("http://localhost/fail")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.TEXT_HTML).body("<h1>down</h1>"));
        server.expect(requestTo("http://localhost/empty")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        CustomClientException html = assertThrows(CustomClientException.class, () -> api.sendGet("/fail", String.class));
        CustomClientException empty = assertThrows(CustomClientException.class, () -> api.sendDelete("/empty"));

        assertEquals("<h1>down</h1>", html.getDetails().getMessage());
        assertEquals(503, html.getDetails().getStatus());
        assertEquals("Not Found", empty.getMessage());
        server.verify();
    }

    @Test
    void optionsConstructorAndDeprecatedTrustAllStillWork() {
        try (Api custom = new Api("http://localhost", HttpClientOptions.builder().connectTimeout(Duration.ofSeconds(2)).build()); Api trustAll = new Api("http://localhost", true)) {
            assertEquals(RestTemplate.class, custom.restTemplate().getClass());
            assertEquals(RestTemplate.class, trustAll.restTemplate().getClass());
        }
    }

    private static final class Api extends AbstractApi {

        Api(String url) {
            super(url);
        }

        Api(String url, boolean trustAll) {
            super(url, trustAll);
        }

        Api(String url, HttpClientOptions options) {
            super(url, options);
        }

        @Override
        protected HttpHeaders createHeaders() {
            return new HttpHeaders();
        }

        RestTemplate restTemplate() {
            return client;
        }
    }
}
