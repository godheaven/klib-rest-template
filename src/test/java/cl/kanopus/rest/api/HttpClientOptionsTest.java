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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HttpClientOptionsTest {

    @TempDir
    Path tempDir;

    @Test
    void defaultsAreSecureWithFiniteTimeouts() {
        HttpClientOptions options = HttpClientOptions.defaults();

        assertEquals(Duration.ofSeconds(10), options.getConnectTimeout());
        assertEquals(Duration.ofSeconds(60), options.getResponseTimeout());
        assertEquals(Duration.ofSeconds(30), options.getConnectionRequestTimeout());
        assertNull(options.getSslContext());
        assertNull(options.getHostnameVerifier());
    }

    @Test
    void builderStoresCustomValues() throws Exception {
        SSLContext context = SSLContext.getDefault();
        HostnameVerifier verifier = (host, session) -> true;

        HttpClientOptions options = HttpClientOptions.builder().connectTimeout(Duration.ofSeconds(1)).responseTimeout(Duration.ZERO).connectionRequestTimeout(Duration.ofMillis(5)).sslContext(context)
                .hostnameVerifier(verifier).build();

        assertEquals(Duration.ofSeconds(1), options.getConnectTimeout());
        assertEquals(Duration.ZERO, options.getResponseTimeout());
        assertEquals(Duration.ofMillis(5), options.getConnectionRequestTimeout());
        assertSame(context, options.getSslContext());
        assertSame(verifier, options.getHostnameVerifier());
    }

    @Test
    void negativeOrNullTimeoutsAreRejected() {
        HttpClientOptions.Builder builder = HttpClientOptions.builder();

        assertThrows(IllegalArgumentException.class, () -> builder.connectTimeout(Duration.ofSeconds(-1)));
        assertThrows(NullPointerException.class, () -> builder.responseTimeout(null));
    }

    @Test
    void trustStoreFileIsLoadedIntoSslContext() throws Exception {
        Path file = tempDir.resolve("truststore.p12");
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        try (OutputStream out = Files.newOutputStream(file)) {
            keyStore.store(out, "secret".toCharArray());
        }

        HttpClientOptions options = HttpClientOptions.builder().trustStore(file, "secret".toCharArray(), "PKCS12").build();
        HttpClientOptions defaultType = HttpClientOptions.builder().trustStore(file, "secret".toCharArray(), null).build();

        assertNotNull(options.getSslContext());
        assertNotNull(defaultType.getSslContext());
    }

    @Test
    void invalidTrustStoreIsReported() {
        Path missing = tempDir.resolve("missing.p12");
        HttpClientOptions.Builder builder = HttpClientOptions.builder();

        assertThrows(IllegalArgumentException.class, () -> builder.trustStore(missing, null, "PKCS12"));
        assertThrows(NullPointerException.class, () -> builder.trustStore((KeyStore) null));
    }

    @Test
    void httpClientsCanBeBuiltForEveryTlsMode() throws Exception {
        try (CloseableHttpClient secure = HttpClientSupport.createHttpClient(null);
                CloseableHttpClient custom = HttpClientSupport.createHttpClient(HttpClientOptions.builder().sslContext(SSLContext.getDefault()).build());
                CloseableHttpClient verifierOnly = HttpClientSupport.createHttpClient(HttpClientOptions.builder().hostnameVerifier((h, s) -> true).build());
                CloseableHttpClient trustAll = HttpClientSupport.createTrustAllHttpClient(null)) {
            assertNotNull(secure);
            assertNotNull(custom);
            assertNotNull(verifierOnly);
            assertNotNull(trustAll);
        }
    }

    @Test
    void zeroDurationMeansDisabledTimeout() {
        assertEquals(Timeout.DISABLED, HttpClientSupport.timeout(Duration.ZERO));
        assertEquals(Timeout.DISABLED, HttpClientSupport.timeout(null));
        assertEquals(Timeout.ofMilliseconds(1500), HttpClientSupport.timeout(Duration.ofMillis(1500)));
    }
}
