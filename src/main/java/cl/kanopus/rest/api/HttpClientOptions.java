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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.time.Duration;
import java.util.Objects;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import org.apache.hc.core5.ssl.SSLContexts;

/**
 * Immutable transport options for the Apache HttpClient 5 instance used by {@link AbstractRestClientApi} and {@link AbstractApi}.
 *
 * <p>
 * Defaults: connect timeout {@value #DEFAULT_CONNECT_TIMEOUT_SECONDS}s, response (socket read) timeout {@value #DEFAULT_RESPONSE_TIMEOUT_SECONDS}s, connection-pool lease timeout {@value #DEFAULT_CONNECTION_REQUEST_TIMEOUT_SECONDS}s, and the JVM default trust store with standard hostname
 * verification.
 *
 * <pre>{@code
 * HttpClientOptions options = HttpClientOptions.builder().connectTimeout(Duration.ofSeconds(5)).responseTimeout(Duration.ofSeconds(20))
 *         .trustStore(Path.of("/etc/app/truststore.p12"), "changeit".toCharArray(), "PKCS12").build();
 * }</pre>
 */
public final class HttpClientOptions {

    public static final long DEFAULT_CONNECT_TIMEOUT_SECONDS = 10;
    public static final long DEFAULT_RESPONSE_TIMEOUT_SECONDS = 60;
    public static final long DEFAULT_CONNECTION_REQUEST_TIMEOUT_SECONDS = 30;

    private static final HttpClientOptions DEFAULTS = builder().build();

    private final Duration connectTimeout;
    private final Duration responseTimeout;
    private final Duration connectionRequestTimeout;
    private final SSLContext sslContext;
    private final HostnameVerifier hostnameVerifier;

    private HttpClientOptions(Builder builder) {
        this.connectTimeout = builder.connectTimeout;
        this.responseTimeout = builder.responseTimeout;
        this.connectionRequestTimeout = builder.connectionRequestTimeout;
        this.sslContext = builder.sslContext;
        this.hostnameVerifier = builder.hostnameVerifier;
    }

    /** @return the default options (secure TLS, default timeouts). */
    public static HttpClientOptions defaults() {
        return DEFAULTS;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public Duration getResponseTimeout() {
        return responseTimeout;
    }

    public Duration getConnectionRequestTimeout() {
        return connectionRequestTimeout;
    }

    /** @return the custom SSL context, or {@code null} to use the JVM default trust material. */
    public SSLContext getSslContext() {
        return sslContext;
    }

    /** @return the custom hostname verifier, or {@code null} for the standard HTTPS hostname verification. */
    public HostnameVerifier getHostnameVerifier() {
        return hostnameVerifier;
    }

    public static final class Builder {

        private Duration connectTimeout = Duration.ofSeconds(DEFAULT_CONNECT_TIMEOUT_SECONDS);
        private Duration responseTimeout = Duration.ofSeconds(DEFAULT_RESPONSE_TIMEOUT_SECONDS);
        private Duration connectionRequestTimeout = Duration.ofSeconds(DEFAULT_CONNECTION_REQUEST_TIMEOUT_SECONDS);
        private SSLContext sslContext;
        private HostnameVerifier hostnameVerifier;

        private Builder() {
        }

        /** Maximum time to establish the TCP/TLS connection. {@link Duration#ZERO} means no timeout. */
        public Builder connectTimeout(Duration timeout) {
            this.connectTimeout = requirePositiveOrZero(timeout, "connectTimeout");
            return this;
        }

        /** Maximum inactivity time while waiting for response data. {@link Duration#ZERO} means no timeout. */
        public Builder responseTimeout(Duration timeout) {
            this.responseTimeout = requirePositiveOrZero(timeout, "responseTimeout");
            return this;
        }

        /** Maximum time to wait for a connection from the pool. {@link Duration#ZERO} means no timeout. */
        public Builder connectionRequestTimeout(Duration timeout) {
            this.connectionRequestTimeout = requirePositiveOrZero(timeout, "connectionRequestTimeout");
            return this;
        }

        /** Uses a custom SSL context (custom trust store and/or client certificate for mTLS). */
        public Builder sslContext(SSLContext sslContext) {
            this.sslContext = sslContext;
            return this;
        }

        /** Uses a custom hostname verifier. Prefer the default (standard verification) in production. */
        public Builder hostnameVerifier(HostnameVerifier hostnameVerifier) {
            this.hostnameVerifier = hostnameVerifier;
            return this;
        }

        /** Trusts the certificates of the given trust store (instead of the JVM default {@code cacerts}). */
        public Builder trustStore(KeyStore trustStore) {
            Objects.requireNonNull(trustStore, "trustStore");
            try {
                this.sslContext = SSLContexts.custom().loadTrustMaterial(trustStore, null).build();
            } catch (GeneralSecurityException e) {
                throw new IllegalArgumentException("Unable to create an SSLContext from the trust store", e);
            }
            return this;
        }

        /**
         * Loads a trust store file (for example {@code PKCS12} or {@code JKS}) and trusts its certificates.
         */
        public Builder trustStore(Path path, char[] password, String type) {
            Objects.requireNonNull(path, "path");
            try (InputStream in = Files.newInputStream(path)) {
                KeyStore keyStore = KeyStore.getInstance(type != null ? type : KeyStore.getDefaultType());
                keyStore.load(in, password);
                return trustStore(keyStore);
            } catch (IOException | GeneralSecurityException e) {
                throw new IllegalArgumentException("Unable to load trust store " + path, e);
            }
        }

        public HttpClientOptions build() {
            return new HttpClientOptions(this);
        }

        private static Duration requirePositiveOrZero(Duration value, String name) {
            Objects.requireNonNull(value, name);
            if (value.isNegative()) {
                throw new IllegalArgumentException(name + " must not be negative");
            }
            return value;
        }
    }
}
