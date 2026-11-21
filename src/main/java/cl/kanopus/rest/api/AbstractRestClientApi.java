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

import cl.kanopus.common.data.Paginator;
import cl.kanopus.common.data.Searcher;
import cl.kanopus.rest.api.exception.CustomClientException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Base class for typed REST clients built on Spring's {@link RestClient} and Apache HttpClient 5 (replacement of the deprecated {@link AbstractApi}).
 *
 * <ul>
 * <li>Secure TLS by default (JVM trust store, hostname verification); custom trust store / {@link javax.net.ssl.SSLContext} via {@link HttpClientOptions}.</li>
 * <li>Connect / response / pool timeouts with sane defaults (see {@link HttpClientOptions}).</li>
 * <li>Query parameters are strictly percent-encoded; {@code null} values are skipped.</li>
 * <li>HTTP 4xx/5xx responses are mapped to {@link CustomClientException}, also for empty or non-JSON bodies.</li>
 * <li>{@link AutoCloseable}: closes the HTTP connection pool (Spring invokes {@link #close()} automatically on bean destruction).</li>
 * </ul>
 */
public abstract class AbstractRestClientApi implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AbstractRestClientApi.class);

    public static final String KANOPUS_API_KEY = "X-API-Key";

    protected final RestClient client;
    private final String url;
    private final HttpComponentsClientHttpRequestFactory requestFactory;

    /** Uses {@link HttpClientOptions#defaults()}. */
    protected AbstractRestClientApi(String url) {
        this(url, HttpClientOptions.defaults());
    }

    /**
     * @param options
     *            timeouts and TLS configuration.
     */
    protected AbstractRestClientApi(String url, HttpClientOptions options) {
        this.url = url;
        this.requestFactory = new HttpComponentsClientHttpRequestFactory(HttpClientSupport.createHttpClient(options));
        this.client = withErrorHandler(RestClient.builder().requestFactory(requestFactory)).build();
    }

    /**
     * Uses a caller supplied builder (for example one bound to {@code MockRestServiceServer}, or with custom message converters / interceptors). The builder's request factory is left untouched; the error status handler of this class is added to it.
     */
    protected AbstractRestClientApi(String url, RestClient.Builder builder) {
        this.url = url;
        this.requestFactory = null;
        this.client = withErrorHandler(Objects.requireNonNull(builder, "builder")).build();
    }

    private static RestClient.Builder withErrorHandler(RestClient.Builder builder) {
        return builder.defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
            throw ErrorResponses.toException(response, response.getStatusCode(), request.getMethod(), request.getURI(), log);
        });
    }

    protected abstract HttpHeaders createHeaders();

    /** Closes the underlying HTTP client and its connection pool (no-op when a custom builder was supplied). */
    @Override
    public void close() {
        if (requestFactory != null) {
            try {
                requestFactory.destroy();
            } catch (Exception e) {
                log.debug("Error closing HttpClient", e);
            }
        }
    }

    private RestClient.RequestBodySpec request(HttpMethod method, URI uri, Object body) {
        RestClient.RequestBodySpec spec = client.method(method).uri(uri).headers(h -> {
            HttpHeaders headers = createHeaders();
            if (headers != null) {
                h.addAll(headers);
            }
        });
        if (body != null) {
            spec.body(body);
        }
        return spec;
    }

    public void sendDelete(String path) throws CustomClientException {
        request(HttpMethod.DELETE, UriSupport.build(url, path), null).retrieve().toBodilessEntity();
    }

    public <T> T sendDelete(String path, Class<T> responseType) throws CustomClientException {
        return request(HttpMethod.DELETE, UriSupport.build(url, path), null).retrieve().body(responseType);
    }

    public void sendPost(String path, Object request) throws CustomClientException {
        request(HttpMethod.POST, UriSupport.build(url, path), request).retrieve().toBodilessEntity();
    }

    public <T> T sendPost(String path, Object request, Class<T> responseType) throws CustomClientException {
        return request(HttpMethod.POST, UriSupport.build(url, path), request).retrieve().body(responseType);
    }

    public <T> T sendPost(String path, Object request, ParameterizedTypeReference<T> responseType, Map<String, Object> params) throws CustomClientException {
        return request(HttpMethod.POST, UriSupport.build(url, path, params), request).retrieve().body(responseType);
    }

    /** POST with form parameters ({@link org.springframework.http.converter.FormHttpMessageConverter}: multipart unless a Content-Type header says otherwise), same as {@link AbstractApi}. */
    public void sendPostMap(String path, MultiValueMap<String, Object> params) throws CustomClientException {
        request(HttpMethod.POST, UriSupport.build(url, path), params).retrieve().toBodilessEntity();
    }

    /** Sends a PUT request without a body. */
    public void sendPut(String path) throws CustomClientException {
        request(HttpMethod.PUT, UriSupport.build(url, path), null).retrieve().toBodilessEntity();
    }

    public void sendPut(String path, Object request) throws CustomClientException {
        request(HttpMethod.PUT, UriSupport.build(url, path), request).retrieve().toBodilessEntity();
    }

    public <T> T sendPut(String path, Object request, Class<T> responseType) throws CustomClientException {
        return request(HttpMethod.PUT, UriSupport.build(url, path), request).retrieve().body(responseType);
    }

    public <T> T sendGet(String path, Class<T> responseType) throws CustomClientException {
        return sendGet(path, responseType, Map.of());
    }

    public <T> T sendGet(String path, Class<T> responseType, Map<String, Object> params) throws CustomClientException {
        return request(HttpMethod.GET, UriSupport.build(url, path, params), null).retrieve().body(responseType);
    }

    public <T> T sendGet(String path, ParameterizedTypeReference<T> responseType) throws CustomClientException {
        return sendGet(path, responseType, Map.of());
    }

    public <T> T sendGet(String path, ParameterizedTypeReference<T> responseType, Map<String, Object> params) throws CustomClientException {
        return request(HttpMethod.GET, UriSupport.build(url, path, params), null).retrieve().body(responseType);
    }

    public <T, S extends Searcher<?>> T sendGet(String path, ParameterizedTypeReference<T> responseType, S searcher) throws CustomClientException {
        return request(HttpMethod.GET, UriSupport.build(url, path, searcher), null).retrieve().body(responseType);
    }

    public <T> List<T> sendGetList(String path, Class<T> clazz) throws CustomClientException {
        return sendGet(path, typeList(clazz), Map.of());
    }

    public <T> List<T> sendGetList(String path, Class<T> clazz, Map<String, Object> params) throws CustomClientException {
        return sendGet(path, typeList(clazz), params);
    }

    public <T> Paginator<T> sendGetPaginator(String path, Class<T> clazz) throws CustomClientException {
        return sendGet(path, typePaginator(clazz), Map.of());
    }

    public <T> Paginator<T> sendGetPaginator(String path, Class<T> clazz, Map<String, Object> params) throws CustomClientException {
        return sendGet(path, typePaginator(clazz), params);
    }

    public <T, S extends Searcher<?>> Paginator<T> sendGetPaginator(String path, Class<T> clazz, S searcher) throws CustomClientException {
        return sendGet(path, typePaginator(clazz), searcher);
    }

    protected <T> ParameterizedTypeReference<Paginator<T>> typePaginator(Class<T> clazz) {
        return TypeReferences.paginatorOf(clazz);
    }

    protected <T> ParameterizedTypeReference<List<T>> typeList(Class<T> clazz) {
        return TypeReferences.listOf(clazz);
    }
}
