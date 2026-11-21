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
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.apache.hc.client5.http.classic.HttpClient;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

/**
 * Base class for typed REST clients built on {@link RestTemplate}.
 *
 * @deprecated since 4.20.0: {@link RestTemplate} is deprecated for removal in Spring Framework 7.1. Extend {@link AbstractRestClientApi} (based on {@link org.springframework.web.client.RestClient}) instead; it offers the same helper methods. This class will be removed in the next Spring Boot line
 *             (4.30.0).
 */
@Deprecated(since = "4.20.0", forRemoval = true)
@SuppressWarnings("removal")
public abstract class AbstractApi implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(AbstractApi.class);
    protected final RestTemplate client;
    private final String url;
    private final HttpComponentsClientHttpRequestFactory requestFactory;

    public static final String KANOPUS_API_KEY = "X-API-Key";

    protected AbstractApi(String url) {
        this(url, HttpClientOptions.defaults());
    }

    /**
     * @param allowUntrustedCertificates
     *            when {@code true}, every TLS certificate is trusted and hostname verification is disabled (insecure, logs a warning).
     * @deprecated since 4.20.0: trusting all certificates is insecure. Use {@link #AbstractApi(String, HttpClientOptions)} with {@link HttpClientOptions.Builder#trustStore(java.nio.file.Path, char[], String)} or a custom {@link javax.net.ssl.SSLContext}.
     */
    @Deprecated(since = "4.20.0", forRemoval = true)
    protected AbstractApi(String url, boolean allowUntrustedCertificates) {
        this(url, HttpClientOptions.defaults(), allowUntrustedCertificates);
    }

    /**
     * @param options
     *            timeouts and TLS configuration (trust store / SSL context).
     */
    protected AbstractApi(String url, HttpClientOptions options) {
        this(url, options, false);
    }

    private AbstractApi(String url, HttpClientOptions options, boolean allowUntrustedCertificates) {
        this.url = url;
        this.requestFactory = new HttpComponentsClientHttpRequestFactory(createHttpClient(options, allowUntrustedCertificates));
        this.client = new RestTemplate(requestFactory);
        this.client.setErrorHandler(new MyApiRestTemplateErrorHandler());
    }

    protected abstract HttpHeaders createHeaders();

    /** Closes the underlying HTTP client and its connection pool. Spring calls it automatically for beans. */
    @Override
    public void close() {
        try {
            requestFactory.destroy();
        } catch (Exception e) {
            log.debug("Error closing HttpClient", e);
        }
    }

    public void sendDelete(String path) throws CustomClientException {
        client.exchange(UriSupport.build(url, path), HttpMethod.DELETE, new HttpEntity<>(createHeaders()), String.class);
    }

    public <T> T sendDelete(String path, Class<T> responseType) {
        ResponseEntity<T> response = client.exchange(UriSupport.build(url, path), HttpMethod.DELETE, new HttpEntity<>(createHeaders()), responseType);
        return response.getBody();
    }

    public void sendPost(String path, Object request) throws CustomClientException {
        client.postForLocation(UriSupport.build(url, path), new HttpEntity<>(request, createHeaders()));
    }

    public <T> T sendPost(String path, Object request, ParameterizedTypeReference<T> responseType, Map<String, Object> params) throws CustomClientException {
        ResponseEntity<T> response = client.exchange(UriSupport.build(url, path, params), HttpMethod.POST, new HttpEntity<>(request, createHeaders()), responseType);
        return response.getBody();
    }

    public void sendPostMap(String path, MultiValueMap<String, Object> params) throws CustomClientException {
        client.postForLocation(UriSupport.build(url, path), new HttpEntity<>(params, createHeaders()));
    }

    /** Sends a PUT request without a body. */
    public void sendPut(String path) throws CustomClientException {
        client.put(UriSupport.build(url, path), new HttpEntity<>(createHeaders()));
    }

    public void sendPut(String path, Object request) throws CustomClientException {
        client.put(UriSupport.build(url, path), new HttpEntity<>(request, createHeaders()));
    }

    protected <T> T sendPut(String path, Object request, Class<T> responseType) throws CustomClientException {
        ResponseEntity<T> response = client.exchange(UriSupport.build(url, path), HttpMethod.PUT, new HttpEntity<>(request, createHeaders()), responseType);
        return response.getBody();
    }

    public <T> T sendPost(String path, Object request, Class<T> responseType) throws CustomClientException {
        ResponseEntity<T> response = client.postForEntity(UriSupport.build(url, path), new HttpEntity<>(request, createHeaders()), responseType);
        return response.getBody();
    }

    public <T> T sendGet(String path, Class<T> responseType) throws CustomClientException {
        ResponseEntity<T> response = client.exchange(UriSupport.build(url, path), HttpMethod.GET, new HttpEntity<>(createHeaders()), responseType);
        return response.getBody();
    }

    public <T> T sendGet(String path, ParameterizedTypeReference<T> responseType) throws CustomClientException {
        return sendGet(path, responseType, Map.of());
    }

    public <T> T sendGet(String path, Class<T> responseType, Map<String, Object> params) throws CustomClientException {
        ResponseEntity<T> response = client.exchange(UriSupport.build(url, path, params), HttpMethod.GET, new HttpEntity<>(createHeaders()), responseType);
        return response.getBody();
    }

    public <T> T sendGet(String path, ParameterizedTypeReference<T> responseType, Map<String, Object> params) throws CustomClientException {
        ResponseEntity<T> response = client.exchange(UriSupport.build(url, path, params), HttpMethod.GET, new HttpEntity<>(createHeaders()), responseType);
        return response.getBody();
    }

    public <T, S extends Searcher<?>> T sendGet(String path, ParameterizedTypeReference<T> responseType, S searcher) throws CustomClientException {
        ResponseEntity<T> response = client.exchange(UriSupport.build(url, path, searcher), HttpMethod.GET, new HttpEntity<>(createHeaders()), responseType);
        return response.getBody();
    }

    public <T> List<T> sendGetList(String url, Class<T> clazz) throws CustomClientException {
        return sendGet(url, typeList(clazz), Map.of());
    }

    public <T> List<T> sendGetList(String url, Class<T> clazz, Map<String, Object> params) throws CustomClientException {
        return sendGet(url, typeList(clazz), params);
    }

    public <T> Paginator<T> sendGetPaginator(String url, Class<T> clazz) throws CustomClientException {
        return sendGet(url, typePaginator(clazz), Map.of());
    }

    public <T> Paginator<T> sendGetPaginator(String url, Class<T> clazz, Map<String, Object> params) throws CustomClientException {
        return sendGet(url, typePaginator(clazz), params);
    }

    public <T, S extends Searcher<?>> Paginator<T> sendGetPaginator(String url, Class<T> clazz, S searcher) throws CustomClientException {
        return sendGet(url, typePaginator(clazz), searcher);
    }

    protected <T> ParameterizedTypeReference<Paginator<T>> typePaginator(Class<T> clazz) {
        return TypeReferences.paginatorOf(clazz);
    }

    protected <T> ParameterizedTypeReference<List<T>> typeList(Class<T> clazz) {
        return TypeReferences.listOf(clazz);
    }

    private HttpClient createHttpClient(HttpClientOptions options, boolean allowUntrustedCertificates) {
        if (allowUntrustedCertificates) {
            log.warn("{} is configured to trust ALL TLS certificates and to skip hostname verification. This is insecure and must not be used in production; "
                    + "configure a trust store with HttpClientOptions instead.", getClass().getName());
            return createHttpClientAcceptsUntrustedCerts();
        }
        return HttpClientSupport.createHttpClient(options);
    }

    /**
     * Creates an HttpClient that trusts all certificates and disables hostname verification.
     *
     * <p>
     * Security warning: use this only in non-production environments.
     *
     * <p>
     * This method is {@code protected} so subclasses can override the SSL strategy when needed. Keep overrides side-effect free because it may be invoked during base initialization.
     *
     * @deprecated since 4.20.0: insecure. Supply a trust store or {@link javax.net.ssl.SSLContext} through {@link HttpClientOptions}.
     */
    @Deprecated(since = "4.20.0", forRemoval = true)
    protected HttpClient createHttpClientAcceptsUntrustedCerts() {
        return HttpClientSupport.createTrustAllHttpClient(HttpClientOptions.defaults());
    }

    /**
     * Maps HTTP error responses to {@link CustomClientException}.
     *
     * @deprecated since 4.20.0 together with {@link AbstractApi}; {@link AbstractRestClientApi} handles errors with a {@code RestClient} status handler.
     */
    @Deprecated(since = "4.20.0", forRemoval = true)
    public static class MyApiRestTemplateErrorHandler extends DefaultResponseErrorHandler {

        @Override
        protected void handleError(@NonNull ClientHttpResponse response, HttpStatusCode statusCode, URI url, HttpMethod method) throws IOException {
            if (statusCode.isError()) {
                throw ErrorResponses.toException(response, statusCode, method, url, log);
            }
        }
    }
}
