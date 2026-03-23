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

public class CustomClientException extends RuntimeException {

    private final HttpStatusCode status;
    private final ErrorMessage details;

    public CustomClientException(HttpStatusCode status, ErrorMessage details) {
        super(details.getMessage());
        this.status = status;
        this.details = details;
    }

    public HttpStatusCode getStatus() {
        return status;
    }

    public ErrorMessage getDetails() {
        return details;
    }
}
