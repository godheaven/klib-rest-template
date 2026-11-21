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

import cl.kanopus.rest.api.exception.ErrorCode;
import org.junit.jupiter.api.Test;

class ErrorCodeSubclassTest {

    /** Regression: the constructor used to be package-private, so ErrorCode could not be extended outside its package. */
    static final class ApiErrorCode extends ErrorCode {

        static final ApiErrorCode NOT_FOUND = new ApiErrorCode("Resource {0} not found");

        private ApiErrorCode(String message) {
            super(message);
        }
    }

    @Test
    void errorCodeCanBeExtendedFromAnotherPackage() {
        assertEquals("Resource 42 not found", ApiErrorCode.NOT_FOUND.getMessage(42));
    }
}
