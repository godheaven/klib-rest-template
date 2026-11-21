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

import cl.kanopus.common.data.Searcher;
import cl.kanopus.common.data.enums.SortOrder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UriSupportTest {

    @Test
    void reservedCharactersInQueryValuesAreEncoded() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("q", "a&admin=true");
        params.put("plus", "1+1=2");
        params.put("hash", "x#y?z");
        params.put("text", "año nuevo");

        assertEquals("http://localhost/search?q=a%26admin%3Dtrue&plus=1%2B1%3D2&hash=x%23y%3Fz&text=a%C3%B1o%20nuevo", UriSupport.build("http://localhost", "/search", params).toString());
    }

    @Test
    void reservedCharactersInQueryNamesAreEncoded() {
        assertEquals("http://localhost/s?a%26b=1", UriSupport.build("http://localhost", "/s", Map.of("a&b", 1)).toString());
    }

    @Test
    void nullMapAndNullValuesAreIgnored() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("a", null);
        params.put(null, "x");
        params.put("b", 2);

        assertEquals("http://localhost/p?b=2", UriSupport.build("http://localhost", "/p", params).toString());
        assertEquals("http://localhost/p", UriSupport.build("http://localhost", "/p", (Map<String, ?>) null).toString());
    }

    @Test
    void iterableAndArrayValuesProduceRepeatedParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("id", List.of(1, 2));
        params.put("tag", new String[]{"x", "y"});
        params.put("n", new int[]{7});

        assertEquals("http://localhost/p?id=1&id=2&tag=x&tag=y&n=7", UriSupport.build("http://localhost", "/p", params).toString());
    }

    @Test
    void pathKeepsExistingQueryAndEncodesIllegalCharacters() {
        assertEquals("http://localhost/my%20path?x=1&y=2", UriSupport.build("http://localhost", "/my path?x=1", Map.of("y", 2)).toString());
        assertEquals("/only-path", UriSupport.build(null, "/only-path").toString());
        assertEquals("http://localhost", UriSupport.build("http://localhost", null).toString());
    }

    @Test
    void searcherNullFieldsAreSkipped() {
        Searcher<Object> searcher = new Searcher<>();
        searcher.setLimit(10);
        searcher.setOffset(null);
        searcher.setSortOrder(SortOrder.DESCENDING);

        assertEquals("http://localhost/items?limit=10&sortOrder=DESCENDING", UriSupport.build("http://localhost", "/items", searcher).toString());
        assertEquals("http://localhost/items", UriSupport.build("http://localhost", "/items", (Searcher<?>) null).toString());
    }
}
