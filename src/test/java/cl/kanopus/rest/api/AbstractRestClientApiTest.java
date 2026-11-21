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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import cl.kanopus.common.data.Paginator;
import cl.kanopus.common.data.Searcher;
import cl.kanopus.common.data.enums.SortOrder;
import cl.kanopus.rest.api.exception.CustomClientException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

class AbstractRestClientApiTest {

    private MockRestServiceServer server;
    private ProductApi api;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        api = new ProductApi("http://localhost", builder);
    }

    @Test
    void getVariantsSendHeadersAndDeserializeJson() {
        String json = "application/json";
        server.expect(requestTo("http://localhost/products/1")).andExpect(method(HttpMethod.GET)).andExpect(header(AbstractRestClientApi.KANOPUS_API_KEY, "key"))
                .andRespond(withSuccess("{\"name\":\"book\",\"price\":10}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost/products?category=a%26b")).andRespond(withSuccess("[{\"name\":\"a\"},{\"name\":\"b\"}]", MediaType.valueOf(json)));
        server.expect(requestTo("http://localhost/products")).andRespond(withSuccess("[{\"name\":\"c\"}]", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost/products")).andRespond(withSuccess("{\"records\":[{\"name\":\"p\"}],\"totalRecords\":7}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost/products?active=true")).andRespond(withSuccess("{\"records\":[],\"totalRecords\":0}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost/products?limit=10&offset=20&sortField=name&sortOrder=ASCENDING"))
                .andRespond(withSuccess("{\"records\":[],\"totalRecords\":3}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost/raw")).andRespond(withSuccess("raw", MediaType.TEXT_PLAIN));
        server.expect(requestTo("http://localhost/raw?x=1")).andRespond(withSuccess("raw2", MediaType.TEXT_PLAIN));

        Product product = api.sendGet("/products/1", Product.class);
        List<Product> list = api.sendGetList("/products", Product.class, Map.of("category", "a&b"));
        List<Product> all = api.sendGetList("/products", Product.class);
        Paginator<Product> page = api.sendGetPaginator("/products", Product.class);
        Paginator<Product> filtered = api.sendGetPaginator("/products", Product.class, Map.of("active", true));
        Searcher<Object> searcher = new Searcher<>();
        searcher.setLimit(10);
        searcher.setOffset(20);
        searcher.setSortField("name");
        searcher.setSortOrder(SortOrder.ASCENDING);
        Paginator<Product> searched = api.sendGetPaginator("/products", Product.class, searcher);
        String raw = api.sendGet("/raw", new ParameterizedTypeReference<String>() {
        });
        String raw2 = api.sendGet("/raw", new ParameterizedTypeReference<String>() {
        }, Map.of("x", 1));

        assertEquals("book", product.name);
        assertEquals(10, product.price);
        assertEquals(2, list.size());
        assertEquals("b", list.get(1).name);
        assertEquals("c", all.get(0).name);
        assertEquals(7, page.getTotalRecords());
        assertEquals("p", page.getRecords().get(0).name);
        assertEquals(0, filtered.getTotalRecords());
        assertEquals(3, searched.getTotalRecords());
        assertEquals("raw", raw);
        assertEquals("raw2", raw2);
        server.verify();
    }

    @Test
    void writeOperationsUseExpectedMethodsAndBodies() {
        server.expect(requestTo("http://localhost/products")).andExpect(method(HttpMethod.POST)).andExpect(content().string("{\"name\":\"new\",\"price\":0}")).andRespond(withSuccess());
        server.expect(requestTo("http://localhost/products")).andExpect(method(HttpMethod.POST)).andRespond(withSuccess("{\"name\":\"created\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost/products?dry=true")).andExpect(method(HttpMethod.POST)).andRespond(withSuccess("{\"name\":\"dry\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost/forms")).andExpect(method(HttpMethod.POST)).andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("A 01"))).andRespond(withSuccess());
        server.expect(requestTo("http://localhost/products/1")).andExpect(method(HttpMethod.PUT)).andExpect(content().string("")).andRespond(withSuccess());
        server.expect(requestTo("http://localhost/products/1")).andExpect(method(HttpMethod.PUT)).andExpect(content().string("{\"name\":\"upd\",\"price\":0}")).andRespond(withSuccess());
        server.expect(requestTo("http://localhost/products/1")).andExpect(method(HttpMethod.PUT)).andRespond(withSuccess("{\"name\":\"upd\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://localhost/products/1")).andExpect(method(HttpMethod.DELETE)).andRespond(withSuccess());
        server.expect(requestTo("http://localhost/products/2")).andExpect(method(HttpMethod.DELETE)).andRespond(withSuccess("gone", MediaType.TEXT_PLAIN));

        Product body = new Product();
        body.name = "new";
        api.sendPost("/products", body);
        Product created = api.sendPost("/products", body, Product.class);
        Product dry = api.sendPost("/products", body, new ParameterizedTypeReference<Product>() {
        }, Map.of("dry", true));
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("code", "A 01");
        api.sendPostMap("/forms", form);
        api.sendPut("/products/1");
        Product upd = new Product();
        upd.name = "upd";
        api.sendPut("/products/1", upd);
        Product updated = api.sendPut("/products/1", upd, Product.class);
        api.sendDelete("/products/1");
        String deleted = api.sendDelete("/products/2", String.class);

        assertEquals("created", created.name);
        assertEquals("dry", dry.name);
        assertEquals("upd", updated.name);
        assertEquals("gone", deleted);
        server.verify();
    }

    @Test
    void errorsAreMappedToCustomClientException() {
        server.expect(requestTo("http://localhost/json")).andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                .body("{\"timestamp\":\"2025-01-01T00:00:00.000+00:00\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"invalid payload\",\"path\":\"/json\"}"));
        server.expect(requestTo("http://localhost/html")).andRespond(withStatus(HttpStatus.BAD_GATEWAY).contentType(MediaType.TEXT_HTML).body("<html>proxy</html>"));
        server.expect(requestTo("http://localhost/empty")).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        CustomClientException json = assertThrows(CustomClientException.class, () -> api.sendGet("/json", Product.class));
        CustomClientException html = assertThrows(CustomClientException.class, () -> api.sendPost("/html", new Product()));
        CustomClientException empty = assertThrows(CustomClientException.class, () -> api.sendDelete("/empty"));

        assertEquals(400, json.getStatus().value());
        assertEquals("invalid payload", json.getDetails().getMessage());
        assertNotNull(json.getDetails().getTimestamp());
        assertEquals("<html>proxy</html>", html.getDetails().getMessage());
        assertEquals("Internal Server Error", empty.getMessage());
        server.verify();
    }

    @Test
    void nullHeadersAreTolerated() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer local = MockRestServiceServer.bindTo(builder).build();
        local.expect(requestTo("http://localhost/x")).andRespond(withSuccess());
        NoHeadersApi noHeaders = new NoHeadersApi(builder);

        assertNull(noHeaders.sendGet("/x", String.class));
        noHeaders.close();
        local.verify();
    }

    @Test
    void defaultConstructorsBuildAClient() {
        try (DefaultApi defaults = new DefaultApi()) {
            assertNotNull(defaults.restClient());
        }
        assertThrows(NullPointerException.class, () -> new ProductApi("http://localhost", null));
    }

    static final class Product {
        public String name;
        public int price;
    }

    private static final class ProductApi extends AbstractRestClientApi {

        ProductApi(String url, RestClient.Builder builder) {
            super(url, builder);
        }

        @Override
        protected HttpHeaders createHeaders() {
            HttpHeaders headers = new HttpHeaders();
            headers.set(KANOPUS_API_KEY, "key");
            return headers;
        }
    }

    private static final class NoHeadersApi extends AbstractRestClientApi {

        NoHeadersApi(RestClient.Builder builder) {
            super("http://localhost", builder);
        }

        @Override
        protected HttpHeaders createHeaders() {
            return null;
        }
    }

    private static final class DefaultApi extends AbstractRestClientApi {

        DefaultApi() {
            super("http://localhost");
        }

        @Override
        protected HttpHeaders createHeaders() {
            return new HttpHeaders();
        }

        RestClient restClient() {
            return client;
        }
    }
}
