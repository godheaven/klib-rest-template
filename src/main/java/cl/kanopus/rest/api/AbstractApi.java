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
import cl.kanopus.common.util.GsonUtils;
import cl.kanopus.rest.api.exception.CustomClientException;
import cl.kanopus.rest.api.exception.ErrorMessage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.net.ssl.SSLContext;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.ssl.SSLContexts;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

public abstract class AbstractApi {
    private static final Logger log = LoggerFactory.getLogger(MyApiRestTemplateErrorHandler.class);
    protected final RestTemplate client;
    private final String url;

    public static final String KANOPUS_API_KEY = "X-API-Key";

    protected AbstractApi(String url) {
        this(url, false);
    }

    protected AbstractApi(String url, boolean allowUntrustedCertificates) {
        this.url = url;
        this.client = new RestTemplate(createRequestFactory(allowUntrustedCertificates));
        this.client.setErrorHandler(new MyApiRestTemplateErrorHandler());
    }

    protected abstract HttpHeaders createHeaders();

    public void sendDelete(String path) throws CustomClientException {
        HttpHeaders headers = createHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url + path);
        client.exchange(builder.build().toUri(), HttpMethod.DELETE, entity, String.class);
    }

    public <T> T sendDelete(String path, Class<T> responseType) {
        HttpHeaders headers = createHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url + path);
        ResponseEntity<T> response =
                client.exchange(builder.build().toUri(), HttpMethod.DELETE, entity, responseType);
        return response.getBody();
    }

    public void sendPost(String path, Object request) throws CustomClientException {
        HttpHeaders headers = createHeaders();
        HttpEntity<Object> entity = new HttpEntity<>(request, headers);

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url + path);
        client.postForLocation(builder.build().toUri(), entity);
    }

    public <T> T sendPost(
            String path,
            Object request,
            ParameterizedTypeReference<T> responseType,
            Map<String, Object> params)
            throws CustomClientException {
        HttpHeaders headers = createHeaders();
        HttpEntity<Object> entity = new HttpEntity<>(request, headers);

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url + path);
        for (Map.Entry<String, Object> m : params.entrySet()) {
            builder.queryParam(m.getKey(), m.getValue());
        }

        ResponseEntity<T> response =
                client.exchange(builder.build().toUri(), HttpMethod.POST, entity, responseType);
        return response.getBody();
    }

    public void sendPostMap(String path, MultiValueMap<String, Object> params)
            throws CustomClientException {
        HttpHeaders headers = createHeaders();
        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(params, headers);
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url + path);
        client.postForLocation(builder.build().toUri(), entity);
    }

    public void sendPut(String path) throws CustomClientException {
        sendPut(path, String.class);
    }

    public void sendPut(String path, Object request) throws CustomClientException {
        HttpHeaders headers = createHeaders();
        HttpEntity<Object> entity = new HttpEntity<>(request, headers);

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url + path);
        client.put(builder.build().toUri(), entity);
    }

    protected <T> T sendPut(String path, Object request, Class<T> responseType)
            throws CustomClientException {
        HttpHeaders headers = createHeaders();
        HttpEntity<Object> entity = new HttpEntity<>(request, headers);

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url + path);
        ResponseEntity<T> response =
                client.exchange(builder.build().toUri(), HttpMethod.PUT, entity, responseType);
        return response.getBody();
    }

    public <T> T sendPost(String path, Object request, Class<T> responseType)
            throws CustomClientException {
        HttpHeaders headers = createHeaders();
        HttpEntity<Object> entity = new HttpEntity<>(request, headers);

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url + path);

        ResponseEntity<T> response =
                client.postForEntity(builder.build().toUri(), entity, responseType);
        return response.getBody();
    }

    public <T> T sendGet(String path, Class<T> responseType) throws CustomClientException {
        HttpHeaders headers = createHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url + path);
        ResponseEntity<T> response =
                client.exchange(builder.build().toUri(), HttpMethod.GET, entity, responseType);
        return response.getBody();
    }

    public <T> T sendGet(String path, ParameterizedTypeReference<T> responseType)
            throws CustomClientException {
        HashMap<String, Object> params = new HashMap<>();
        return sendGet(path, responseType, params);
    }

    public <T> T sendGet(String path, Class<T> responseType, Map<String, Object> params)
            throws CustomClientException {
        HttpHeaders headers = createHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url + path);
        for (Map.Entry<String, Object> m : params.entrySet()) {
            builder.queryParam(m.getKey(), m.getValue());
        }
        ResponseEntity<T> response =
                client.exchange(builder.build().toUri(), HttpMethod.GET, entity, responseType);
        return response.getBody();
    }

    public <T> T sendGet(
            String path, ParameterizedTypeReference<T> responseType, Map<String, Object> params)
            throws CustomClientException {
        HttpHeaders headers = createHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url + path);
        for (Map.Entry<String, Object> m : params.entrySet()) {
            builder.queryParam(m.getKey(), m.getValue());
        }
        ResponseEntity<T> response =
                client.exchange(builder.build().toUri(), HttpMethod.GET, entity, responseType);
        return response.getBody();
    }

    public <T, S extends Searcher<?>> T sendGet(
            String path, ParameterizedTypeReference<T> responseType, S searcher)
            throws CustomClientException {
        HttpHeaders headers = createHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url + path);
        builder.queryParam("limit", searcher.getLimit());
        builder.queryParam("offset", searcher.getOffset());
        builder.queryParam("sortField", searcher.getSortField());
        builder.queryParam("sortOrder", searcher.getSortOrder());
        ResponseEntity<T> response =
                client.exchange(builder.build().toUri(), HttpMethod.GET, entity, responseType);
        return response.getBody();
    }

    public <T> List<T> sendGetList(String url, Class<T> clazz) throws CustomClientException {
        return sendGet(url, typeList(clazz), new HashMap<>());
    }

    public <T> List<T> sendGetList(String url, Class<T> clazz, Map<String, Object> params)
            throws CustomClientException {
        return sendGet(url, typeList(clazz), params);
    }

    public <T> Paginator<T> sendGetPaginator(String url, Class<T> clazz)
            throws CustomClientException {
        return sendGet(url, typePaginator(clazz), new HashMap<>());
    }

    public <T> Paginator<T> sendGetPaginator(String url, Class<T> clazz, Map<String, Object> params)
            throws CustomClientException {
        return sendGet(url, typePaginator(clazz), params);
    }

    public <T, S extends Searcher<?>> Paginator<T> sendGetPaginator(
            String url, Class<T> clazz, S searcher) throws CustomClientException {
        return sendGet(url, typePaginator(clazz), searcher);
    }

    protected <T> ParameterizedTypeReference<Paginator<T>> typePaginator(Class<T> clazz) {
        return new ParameterizedTypeReference<Paginator<T>>() {
            @Override
            public Type getType() {
                return new MyParameterizedTypeImpl(
                        (ParameterizedType) super.getType(), new Type[] {clazz});
            }
        };
    }

    protected <T> ParameterizedTypeReference<List<T>> typeList(Class<T> clazz) {
        return new ParameterizedTypeReference<List<T>>() {
            @Override
            public Type getType() {
                return new MyParameterizedTypeImpl(
                        (ParameterizedType) super.getType(), new Type[] {clazz});
            }
        };
    }

    private ClientHttpRequestFactory createRequestFactory(boolean allowUntrustedCertificates) {
        try {
            HttpClient httpClient =
                    allowUntrustedCertificates
                            ? createHttpClientAcceptsUntrustedCerts()
                            : HttpClients.custom().build();
            return new HttpComponentsClientHttpRequestFactory(httpClient);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Creates an HttpClient that trusts all certificates.
     *
     * <p>Security warning: use this only in non-production environments.
     *
     * <p>This method is {@code protected} so subclasses can override the SSL strategy when needed.
     * Keep overrides side-effect free because it may be invoked during base initialization.
     */
    protected HttpClient createHttpClientAcceptsUntrustedCerts() {
        try {
            // 1. Create an SSLContext that trusts all certificates
            SSLContext sslContext =
                    SSLContexts.custom().loadTrustMaterial(null, TrustAllStrategy.INSTANCE).build();

            // 2. Create a SocketFactory with hostname verification disabled
            SSLConnectionSocketFactory sslSocketFactory =
                    SSLConnectionSocketFactoryBuilder.create()
                            .setSslContext(sslContext)
                            .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                            .build();

            // 3. Create the ConnectionManager using the socket factory
            PoolingHttpClientConnectionManager connectionManager =
                    PoolingHttpClientConnectionManagerBuilder.create()
                            .setSSLSocketFactory(sslSocketFactory)
                            .build();

            // 4. Build the final client
            return HttpClients.custom().setConnectionManager(connectionManager).build();
        } catch (Exception e) {
            throw new RuntimeException(
                    "Error creating HttpClient that trusts untrusted certificates", e);
        }
    }

    public static class MyApiRestTemplateErrorHandler extends DefaultResponseErrorHandler {

        @Override
        protected void handleError(
                @NonNull ClientHttpResponse response,
                HttpStatusCode statusCode,
                URI url,
                HttpMethod method)
                throws IOException {
            if (statusCode.isError()) {
                try (BufferedReader reader =
                        new BufferedReader(new InputStreamReader(response.getBody()))) {
                    String json = reader.lines().collect(Collectors.joining(""));
                    ErrorMessage errorMessage = GsonUtils.custom.fromJson(json, ErrorMessage.class);
                    log.error("API Error: {} - {}", statusCode, errorMessage);
                    throw new CustomClientException(statusCode, errorMessage);
                }
            }
        }
    }

    private static class MyParameterizedTypeImpl implements ParameterizedType {

        private final ParameterizedType delegate;
        private final Type[] actualTypeArguments;

        MyParameterizedTypeImpl(ParameterizedType delegate, Type[] actualTypeArguments) {
            this.delegate = delegate;
            this.actualTypeArguments = actualTypeArguments;
        }

        @Override
        public Type[] getActualTypeArguments() {
            return actualTypeArguments;
        }

        @Override
        public Type getRawType() {
            return delegate.getRawType();
        }

        @Override
        public Type getOwnerType() {
            return delegate.getOwnerType();
        }
    }
}
