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

import java.security.GeneralSecurityException;
import java.time.Duration;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.HostnameVerificationPolicy;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.Timeout;

/** Builds Apache HttpClient 5 instances from {@link HttpClientOptions}. */
final class HttpClientSupport {

    private HttpClientSupport() {
    }

    /** Creates a client with the configured timeouts and TLS settings (secure by default). */
    static CloseableHttpClient createHttpClient(HttpClientOptions options) {
        HttpClientOptions opts = options != null ? options : HttpClientOptions.defaults();
        TlsSocketStrategy tls = null;
        if (opts.getSslContext() != null || opts.getHostnameVerifier() != null) {
            SSLContext sslContext = opts.getSslContext() != null ? opts.getSslContext() : SSLContexts.createDefault();
            tls = tlsStrategy(sslContext, opts.getHostnameVerifier());
        }
        return build(opts, tls);
    }

    /**
     * Creates a client that trusts every certificate and skips hostname verification. Insecure: only kept for backward compatibility of {@link AbstractApi}.
     */
    static CloseableHttpClient createTrustAllHttpClient(HttpClientOptions options) {
        HttpClientOptions opts = options != null ? options : HttpClientOptions.defaults();
        try {
            SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(null, TrustAllStrategy.INSTANCE).build();
            return build(opts, tlsStrategy(sslContext, NoopHostnameVerifier.INSTANCE));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Error creating HttpClient that trusts untrusted certificates", e);
        }
    }

    private static TlsSocketStrategy tlsStrategy(SSLContext sslContext, HostnameVerifier hostnameVerifier) {
        ClientTlsStrategyBuilder builder = ClientTlsStrategyBuilder.create().setSslContext(sslContext);
        if (hostnameVerifier != null) {
            // Only the supplied verifier decides; otherwise the built-in JSSE check would still apply.
            builder.setHostVerificationPolicy(HostnameVerificationPolicy.CLIENT).setHostnameVerifier(hostnameVerifier);
        }
        return builder.buildClassic();
    }

    private static CloseableHttpClient build(HttpClientOptions opts, TlsSocketStrategy tls) {
        ConnectionConfig connectionConfig = ConnectionConfig.custom().setConnectTimeout(timeout(opts.getConnectTimeout())).setSocketTimeout(timeout(opts.getResponseTimeout())).build();
        PoolingHttpClientConnectionManagerBuilder connectionManager = PoolingHttpClientConnectionManagerBuilder.create().setDefaultConnectionConfig(connectionConfig);
        if (tls != null) {
            connectionManager.setTlsSocketStrategy(tls);
        }
        RequestConfig requestConfig = RequestConfig.custom().setResponseTimeout(timeout(opts.getResponseTimeout())).setConnectionRequestTimeout(timeout(opts.getConnectionRequestTimeout())).build();
        return HttpClients.custom().setConnectionManager(connectionManager.build()).setDefaultRequestConfig(requestConfig).build();
    }

    static Timeout timeout(Duration duration) {
        return duration == null || duration.isZero() ? Timeout.DISABLED : Timeout.ofMilliseconds(duration.toMillis());
    }
}
