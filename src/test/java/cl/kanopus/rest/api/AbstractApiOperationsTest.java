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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import cl.kanopus.common.data.Paginator;
import cl.kanopus.common.data.Searcher;
import cl.kanopus.common.data.enums.SortOrder;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

class AbstractApiOperationsTest {

    @Test
    void deleteAndPostAndPutOperationsUseExpectedHttpMethods() {
        ExposedApi api = new ExposedApi("http://localhost");
        MockRestServiceServer server = MockRestServiceServer.createServer(api.restTemplate());

        server.expect(requestTo("http://localhost/resource/1")).andExpect(method(HttpMethod.DELETE)).andRespond(withSuccess("deleted", MediaType.TEXT_PLAIN));

        server.expect(requestTo("http://localhost/resource")).andExpect(method(HttpMethod.POST)).andRespond(withSuccess("", MediaType.TEXT_PLAIN));

        server.expect(requestTo("http://localhost/resource/1")).andExpect(method(HttpMethod.PUT)).andRespond(withSuccess("", MediaType.TEXT_PLAIN));

        String deleted = api.sendDelete("/resource/1", String.class);
        api.sendPost("/resource", Map.of("name", "book"));
        api.sendPut("/resource/1", Map.of("name", "new-name"));

        assertEquals("deleted", deleted);
        server.verify();
    }

    @Test
    void sendPostMapAndTypedPostAndTypedPutReturnBody() {
        ExposedApi api = new ExposedApi("http://localhost");
        MockRestServiceServer server = MockRestServiceServer.createServer(api.restTemplate());

        server.expect(requestTo("http://localhost/forms")).andExpect(method(HttpMethod.POST)).andRespond(withSuccess("", MediaType.TEXT_PLAIN));

        server.expect(requestTo("http://localhost/products")).andExpect(method(HttpMethod.POST)).andRespond(withSuccess("created", MediaType.TEXT_PLAIN));

        server.expect(requestTo("http://localhost/products/1")).andExpect(method(HttpMethod.PUT)).andRespond(withSuccess("updated", MediaType.TEXT_PLAIN));

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("code", "A-01");

        api.sendPostMap("/forms", form);
        String postResult = api.sendPost("/products", Map.of("name", "book"), String.class);
        String putResult = api.exposeTypedPut("/products/1", Map.of("name", "book-2"), String.class);

        assertEquals("created", postResult);
        assertEquals("updated", putResult);
        server.verify();
    }

    @Test
    void parameterizedGetAndPostIncludeQueryParams() {
        ExposedApi api = new ExposedApi("http://localhost");
        MockRestServiceServer server = MockRestServiceServer.createServer(api.restTemplate());

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("page", 1);
        params.put("size", 20);

        server.expect(requestTo("http://localhost/search?page=1&size=20")).andExpect(method(HttpMethod.GET)).andRespond(withSuccess("result", MediaType.TEXT_PLAIN));

        server.expect(requestTo("http://localhost/create?page=1&size=20")).andExpect(method(HttpMethod.POST)).andRespond(withSuccess("posted", MediaType.TEXT_PLAIN));

        String getResult = api.sendGet("/search", new ParameterizedTypeReference<String>() {
        }, params);
        String postResult = api.sendPost("/create", Map.of("name", "x"), new ParameterizedTypeReference<String>() {
        }, params);

        assertEquals("result", getResult);
        assertEquals("posted", postResult);
        server.verify();
    }

    @Test
    void parameterizedGetWithoutParamsUsesShortcutOverload() {
        ExposedApi api = new ExposedApi("http://localhost");
        MockRestServiceServer server = MockRestServiceServer.createServer(api.restTemplate());

        server.expect(requestTo("http://localhost/ping")).andExpect(method(HttpMethod.GET)).andRespond(withSuccess("pong", MediaType.TEXT_PLAIN));

        String response = api.sendGet("/ping", new ParameterizedTypeReference<String>() {
        });

        assertEquals("pong", response);
        server.verify();
    }

    @Test
    void searcherBasedGetAddsPaginationAndSortingParams() {
        ExposedApi api = new ExposedApi("http://localhost");
        MockRestServiceServer server = MockRestServiceServer.createServer(api.restTemplate());

        @SuppressWarnings("unchecked")
        Searcher<Object> searcher = mock(Searcher.class);
        when(searcher.getLimit()).thenReturn(15);
        when(searcher.getOffset()).thenReturn(30);
        when(searcher.getSortField()).thenReturn("name");
        when(searcher.getSortOrder()).thenReturn(SortOrder.ASCENDING);

        server.expect(requestTo("http://localhost/items?limit=15&offset=30&sortField=name&sortOrder=ASCENDING")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("rows", MediaType.TEXT_PLAIN));

        String response = api.sendGet("/items", new ParameterizedTypeReference<String>() {
        }, searcher);

        assertEquals("rows", response);
        server.verify();
    }

    @Test
    void wrapperMethodsDelegateToGenericGetVariants() {
        DelegatingApi api = new DelegatingApi("http://localhost");

        List<String> expectedList = List.of("a", "b");
        @SuppressWarnings("unchecked")
        Paginator<String> expectedPaginator = mock(Paginator.class);

        api.nextMapResponse = expectedList;
        List<String> list = api.sendGetList("/products", String.class);
        assertSame(expectedList, list);

        api.nextMapResponse = expectedList;
        list = api.sendGetList("/products", String.class, Map.of("active", true));
        assertSame(expectedList, list);

        api.nextMapResponse = expectedPaginator;
        Paginator<String> page = api.sendGetPaginator("/products", String.class);
        assertSame(expectedPaginator, page);

        api.nextMapResponse = expectedPaginator;
        page = api.sendGetPaginator("/products", String.class, Map.of("active", true));
        assertSame(expectedPaginator, page);

        api.nextSearcherResponse = expectedPaginator;
        page = api.sendGetPaginator("/products", String.class, mock(Searcher.class));
        assertSame(expectedPaginator, page);
    }

    @Test
    void defaultPutShortcutCallsPutMethod() {
        ExposedApi api = new ExposedApi("http://localhost");
        MockRestServiceServer server = MockRestServiceServer.createServer(api.restTemplate());

        server.expect(requestTo("http://localhost/shortcut")).andExpect(method(HttpMethod.PUT)).andRespond(withSuccess("", MediaType.TEXT_PLAIN));

        api.sendPut("/shortcut");

        server.verify();
    }

    @Test
    void defaultUntrustedHttpClientFactoryCanBeInstantiatedWhenEnabled() {
        BasicApi api = new BasicApi("http://localhost", true);
        assertNotNull(api.restTemplate());
    }

    @Test
    void errorHandlerIgnoresNonErrorStatus() throws Exception {
        ExposedErrorHandler handler = new ExposedErrorHandler();
        ClientHttpResponse response = mock(ClientHttpResponse.class);
        when(response.getBody()).thenReturn(emptyBody());

        assertDoesNotThrow(() -> handler.invokeHandleError(response, HttpStatus.OK, URI.create("http://localhost/ok"), HttpMethod.GET));
    }

    private InputStream emptyBody() {
        return new ByteArrayInputStream(new byte[0]);
    }

    private static class BasicApi extends AbstractApi {

        BasicApi(String url, boolean allowUntrustedCertificates) {
            super(url, allowUntrustedCertificates);
        }

        @Override
        protected HttpHeaders createHeaders() {
            return new HttpHeaders();
        }

        RestTemplate restTemplate() {
            return client;
        }
    }

    private static class ExposedApi extends AbstractApi {

        ExposedApi(String url) {
            super(url);
        }

        @Override
        protected HttpHeaders createHeaders() {
            HttpHeaders headers = new HttpHeaders();
            headers.set(KANOPUS_API_KEY, "test-key");
            return headers;
        }

        RestTemplate restTemplate() {
            return client;
        }

        <T> T exposeTypedPut(String path, Object request, Class<T> responseType) {
            return sendPut(path, request, responseType);
        }
    }

    private static class DelegatingApi extends AbstractApi {

        private Object nextMapResponse;
        private Object nextSearcherResponse;

        DelegatingApi(String url) {
            super(url);
        }

        @Override
        protected HttpHeaders createHeaders() {
            return new HttpHeaders();
        }

        @Override
        public <T> T sendGet(String path, ParameterizedTypeReference<T> responseType, Map<String, Object> params) {
            return responseType.getType() != null ? (T) nextMapResponse : null;
        }

        @Override
        public <T, S extends Searcher<?>> T sendGet(String path, ParameterizedTypeReference<T> responseType, S searcher) {
            return responseType.getType() != null ? (T) nextSearcherResponse : null;
        }
    }

    private static class ExposedErrorHandler extends AbstractApi.MyApiRestTemplateErrorHandler {

        void invokeHandleError(ClientHttpResponse response, org.springframework.http.HttpStatusCode status, URI uri, HttpMethod method) throws Exception {
            handleError(response, status, uri, method);
        }
    }
}
