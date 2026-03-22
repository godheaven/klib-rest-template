/*-
 * !--
 * For support and inquiries regarding this library, please contact:
 *   soporte@kanopus.cl
 *
 * Project website:
 *   https://www.kanopus.cl
 * %%
 * Copyright (C) 2025 - 2026 Pablo Díaz Saavedra
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import cl.kanopus.common.data.Paginator;
import cl.kanopus.rest.api.exception.CustomClientException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class AbstractApiTest {

    @Test
    void sendGetWithParamsAddsQueryParamsAndHeaders() {
        TestApi api = new TestApi("http://localhost");
        MockRestServiceServer server = MockRestServiceServer.createServer(api.restTemplate());

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("category", "books");
        params.put("active", true);

        server.expect(requestTo("http://localhost/products?category=books&active=true"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(AbstractApi.KANOPUS_API_KEY, "test-key"))
                .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        String response = api.sendGet("/products", String.class, params);

        assertEquals("ok", response);
        server.verify();
    }

    @Test
    void errorHandlerMapsHttpErrorsToCustomClientException() {
        TestApi api = new TestApi("http://localhost");
        MockRestServiceServer server = MockRestServiceServer.createServer(api.restTemplate());

        server.expect(requestTo("http://localhost/fail"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(
                        withStatus(HttpStatus.BAD_REQUEST)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(
                                        "{\"timestamp\":null,\"status\":400,\"error\":\"Bad Request\",\"message\":\"invalid payload\",\"path\":\"/fail\"}"));

        CustomClientException exception =
                assertThrows(CustomClientException.class, () -> api.sendGet("/fail", String.class));

        assertEquals(400, exception.getStatus().value());
        assertNotNull(exception.getDetails());
        assertEquals("invalid payload", exception.getDetails().getMessage());
        server.verify();
    }

    @Test
    void insecureHttpClientHookIsOnlyUsedWhenExplicitlyEnabled() {
        TestApi secureApi = new TestApi("http://localhost", false);
        TestApi insecureApi = new TestApi("http://localhost", true);

        assertFalse(secureApi.untrustedFactoryCalled());
        assertTrue(insecureApi.untrustedFactoryCalled());
    }

    @Test
    void typeHelpersPreserveGenericElementTypes() {
        TestApi api = new TestApi("http://localhost");

        ParameterizedTypeReference<List<String>> listType = api.exposeTypeList(String.class);
        ParameterizedTypeReference<Paginator<Integer>> paginatorType =
                api.exposeTypePaginator(Integer.class);

        ParameterizedType listParameterizedType = (ParameterizedType) listType.getType();
        ParameterizedType paginatorParameterizedType = (ParameterizedType) paginatorType.getType();

        Type listArg = listParameterizedType.getActualTypeArguments()[0];
        Type paginatorArg = paginatorParameterizedType.getActualTypeArguments()[0];

        assertEquals(String.class, listArg);
        assertEquals(Integer.class, paginatorArg);
    }

    private static class TestApi extends AbstractApi {

        private boolean untrustedFactoryCalled;

        TestApi(String url) {
            super(url);
        }

        TestApi(String url, boolean allowUntrustedCertificates) {
            super(url, allowUntrustedCertificates);
        }

        @Override
        protected HttpHeaders createHeaders() {
            HttpHeaders headers = new HttpHeaders();
            headers.set(KANOPUS_API_KEY, "test-key");
            return headers;
        }

        @Override
        protected HttpClient createHttpClientAcceptsUntrustedCerts() {
            untrustedFactoryCalled = true;
            return HttpClients.custom().build();
        }

        RestTemplate restTemplate() {
            return client;
        }

        boolean untrustedFactoryCalled() {
            return untrustedFactoryCalled;
        }

        <T> ParameterizedTypeReference<List<T>> exposeTypeList(Class<T> clazz) {
            return typeList(clazz);
        }

        <T> ParameterizedTypeReference<Paginator<T>> exposeTypePaginator(Class<T> clazz) {
            return typePaginator(clazz);
        }
    }
}
