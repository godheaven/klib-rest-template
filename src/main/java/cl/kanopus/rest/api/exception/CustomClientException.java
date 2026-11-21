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
package cl.kanopus.rest.api.exception;

import org.springframework.http.HttpStatusCode;

/**
 * Thrown when a remote API answers with an HTTP error status (4xx/5xx).
 *
 * <p>
 * {@link #getDetails()} is never {@code null} when the exception is created by the library: if the error body is empty or is not a JSON error document (for example an HTML error page from a proxy), a synthetic {@link ErrorMessage} is built from the status code and the (truncated) raw body, which is
 * also available through {@link #getResponseBody()}.
 */
public class CustomClientException extends RuntimeException {

    private final HttpStatusCode status;
    private final ErrorMessage details;
    private final String responseBody;

    public CustomClientException(HttpStatusCode status, ErrorMessage details) {
        this(status, details, null);
    }

    public CustomClientException(HttpStatusCode status, ErrorMessage details, String responseBody) {
        super(resolveMessage(status, details));
        this.status = status;
        this.details = details;
        this.responseBody = responseBody;
    }

    private static String resolveMessage(HttpStatusCode status, ErrorMessage details) {
        if (details != null && details.getMessage() != null) {
            return details.getMessage();
        }
        if (details != null && details.getError() != null) {
            return details.getError();
        }
        return status != null ? "HTTP " + status.value() : null;
    }

    public HttpStatusCode getStatus() {
        return status;
    }

    public ErrorMessage getDetails() {
        return details;
    }

    /**
     * @return the raw error response body as text (possibly truncated), or {@code null} when it was not captured.
     */
    public String getResponseBody() {
        return responseBody;
    }
}
