/*-
 * !--
 * For support and inquiries regarding this library, please contact:
 *   soporte@kanopus.cl
 *
 * Project website:
 *   https://www.kanopus.cl
 * %%
 * Copyright (C) 2025 - 2026 Pablo Díaz Saavedra
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
package cl.kanopus.rest.api.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ExceptionModelTest {

    @Test
    void errorMessageGettersExposeAssignedValues() {
        Date timestamp = new Date(0);
        ErrorMessage details = new ErrorMessage(timestamp, 500, "Internal", "failed", "/orders");

        assertSame(timestamp, details.getTimestamp());
        assertEquals(500, details.getStatus());
        assertEquals("Internal", details.getError());
        assertEquals("failed", details.getMessage());
        assertEquals("/orders", details.getPath());
    }

    @Test
    void customClientExceptionExposesStatusDetailsAndMessage() {
        ErrorMessage details = new ErrorMessage(new Date(0), 404, "Not Found", "resource missing", "/products/10");

        CustomClientException exception = new CustomClientException(HttpStatus.NOT_FOUND, details);

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertSame(details, exception.getDetails());
        assertEquals("resource missing", exception.getMessage());
    }

    @Test
    void serviceExceptionFormatsArgumentsAndPreservesCause() {
        DummyErrorCode code = new DummyErrorCode("Code {0} failed for {1}");
        IllegalStateException cause = new IllegalStateException("boom");

        ServiceException exception = new ServiceException(code, cause, 12, "orders");

        assertSame(code, exception.getCode());
        assertSame(cause, exception.getCause());
        assertEquals("Code 12 failed for orders", exception.getMessage());
    }

    @Test
    void serviceExceptionSupportsRemainingConstructors() {
        DummyErrorCode code = new DummyErrorCode("Simple message");
        IllegalArgumentException cause = new IllegalArgumentException("bad");

        ServiceException withCodeOnly = new ServiceException(code);
        ServiceException withArgs = new ServiceException(new DummyErrorCode("Hello {0}"), "world");
        ServiceException withCause = new ServiceException(code, cause);

        assertSame(code, withCodeOnly.getCode());
        assertEquals("Simple message", withCodeOnly.getMessage());

        assertEquals("Hello world", withArgs.getMessage());

        assertSame(code, withCause.getCode());
        assertSame(cause, withCause.getCause());
        assertEquals("Simple message", withCause.getMessage());
    }

    @Test
    void errorCodeReplacesAllPlaceholders() {
        DummyErrorCode code = new DummyErrorCode("A={0}, B={1}, A-again={0}");

        String message = code.getMessage("x", 7);

        assertEquals("A=x, B=7, A-again=x", message);
    }

    @Test
    void errorCodeReturnsBaseMessageWhenNoArgsAreProvided() {
        DummyErrorCode code = new DummyErrorCode("Base message");

        assertEquals("Base message", code.getMessage());
    }

    @Test
    void errorCodeDoesNotLoopWhenArgumentContainsItsOwnPlaceholder() {
        DummyErrorCode code = new DummyErrorCode("value={0}, other={1}");

        String message = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> code.getMessage("{0}", "{1}{0}"));

        assertEquals("value={0}, other={1}{0}", message);
    }

    @Test
    void errorCodeKeepsUnknownOrMalformedPlaceholders() {
        DummyErrorCode code = new DummyErrorCode("{0} {1} {x} {} { {0");

        assertEquals("a {1} {x} {} { {0", code.getMessage("a"));
        assertEquals("null {1} {x} {} { {0", code.getMessage((Object) null));
        assertEquals("{0} {1} {x} {} { {0", code.getMessage((Object[]) null));
        assertEquals("{0} {1} {x} {} { {0", code.getMessage(new Object[0]));
        assertEquals("{99999999999}", new DummyErrorCode("{99999999999}").getMessage("a"));
    }

    @Test
    void errorCodeWithNullMessageIsSafe() {
        assertNull(new DummyErrorCode(null).getMessage("a"));
    }

    @Test
    void customClientExceptionToleratesNullDetails() {
        CustomClientException exception = new CustomClientException(HttpStatus.BAD_GATEWAY, null);

        assertNull(exception.getDetails());
        assertNull(exception.getResponseBody());
        assertEquals("HTTP 502", exception.getMessage());
        assertNull(new CustomClientException(null, null).getMessage());
    }

    @Test
    void customClientExceptionFallsBackToErrorAndKeepsRawBody() {
        ErrorMessage details = new ErrorMessage(null, 500, "Internal Server Error", null, "/x");

        CustomClientException exception = new CustomClientException(HttpStatus.INTERNAL_SERVER_ERROR, details, "<html/>");

        assertEquals("Internal Server Error", exception.getMessage());
        assertEquals("<html/>", exception.getResponseBody());
    }

    private static final class DummyErrorCode extends ErrorCode {

        DummyErrorCode(String message) {
            super(message);
        }
    }
}
