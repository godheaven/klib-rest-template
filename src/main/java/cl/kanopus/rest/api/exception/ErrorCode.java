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

/**
 * This class holds all the exceptions that are declared in the system.
 *
 * <p>
 * Messages may contain positional placeholders ({@code {0}}, {@code {1}}, ...) that are replaced by {@link #getMessage(Object...)} in a single pass: replacement values are inserted verbatim and never re-scanned, so an argument that itself contains a placeholder (for example {@code "{0}"}) is safe.
 */
public abstract class ErrorCode {

    private final String message;

    protected ErrorCode(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public String getMessage(Object... args) {
        if (message == null || args == null || args.length == 0) {
            return message;
        }
        StringBuilder sb = new StringBuilder(message.length() + 16);
        int length = message.length();
        int i = 0;
        while (i < length) {
            char c = message.charAt(i);
            if (c == '{') {
                int close = message.indexOf('}', i + 1);
                int index = close > i + 1 ? parseIndex(message, i + 1, close) : -1;
                if (index >= 0 && index < args.length) {
                    sb.append(args[index]);
                    i = close + 1;
                    continue;
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    private static int parseIndex(String text, int from, int to) {
        if (to - from > 9) {
            return -1;
        }
        int value = 0;
        for (int i = from; i < to; i++) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') {
                return -1;
            }
            value = value * 10 + (c - '0');
        }
        return value;
    }
}
