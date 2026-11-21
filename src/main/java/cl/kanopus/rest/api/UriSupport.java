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

import cl.kanopus.common.data.Searcher;
import java.lang.reflect.Array;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

/**
 * Builds request URIs. The base URL + path are encoded like before (illegal characters are percent-encoded), while query parameter names and values are encoded strictly, so reserved characters such as {@code &}, {@code =}, {@code +}, {@code #} or {@code ?} in a value can no longer split or inject
 * parameters.
 */
final class UriSupport {

    private UriSupport() {
    }

    static URI build(String baseUrl, String path) {
        return build(baseUrl, path, (Map<String, ?>) null);
    }

    /**
     * @param params
     *            query parameters; {@code null} map and {@code null} values are ignored, {@link Iterable} and array values produce repeated parameters.
     */
    static URI build(String baseUrl, String path, Map<String, ?> params) {
        UriComponentsBuilder builder = base(baseUrl, path);
        if (params != null) {
            for (Map.Entry<String, ?> entry : params.entrySet()) {
                addParam(builder, entry.getKey(), entry.getValue());
            }
        }
        return builder.build(true).toUri();
    }

    /** Adds {@code limit}, {@code offset}, {@code sortField} and {@code sortOrder}; {@code null} fields (or a {@code null} searcher) are skipped. */
    static URI build(String baseUrl, String path, Searcher<?> searcher) {
        UriComponentsBuilder builder = base(baseUrl, path);
        if (searcher != null) {
            addParam(builder, "limit", searcher.getLimit());
            addParam(builder, "offset", searcher.getOffset());
            addParam(builder, "sortField", searcher.getSortField());
            addParam(builder, "sortOrder", searcher.getSortOrder());
        }
        return builder.build(true).toUri();
    }

    private static UriComponentsBuilder base(String baseUrl, String path) {
        String full = (baseUrl != null ? baseUrl : "") + (path != null ? path : "");
        UriComponents encoded = UriComponentsBuilder.fromUriString(full).build().encode(StandardCharsets.UTF_8);
        return UriComponentsBuilder.newInstance().uriComponents(encoded);
    }

    private static void addParam(UriComponentsBuilder builder, String name, Object value) {
        if (name == null || value == null) {
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                addParam(builder, name, item);
            }
            return;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                addParam(builder, name, Array.get(value, i));
            }
            return;
        }
        builder.queryParam(encode(name), encode(String.valueOf(value)));
    }

    private static String encode(String value) {
        return UriUtils.encode(value, StandardCharsets.UTF_8);
    }
}
