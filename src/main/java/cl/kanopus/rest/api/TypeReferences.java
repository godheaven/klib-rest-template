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

import cl.kanopus.common.data.Paginator;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import org.springframework.core.ParameterizedTypeReference;

/** Builds {@link ParameterizedTypeReference}s for {@code List<T>} and {@code Paginator<T>} from a runtime element class. */
final class TypeReferences {

    private TypeReferences() {
    }

    static <T> ParameterizedTypeReference<Paginator<T>> paginatorOf(Class<T> clazz) {
        return new ParameterizedTypeReference<Paginator<T>>() {
            @Override
            public Type getType() {
                return new MyParameterizedTypeImpl((ParameterizedType) super.getType(), new Type[]{clazz});
            }
        };
    }

    static <T> ParameterizedTypeReference<List<T>> listOf(Class<T> clazz) {
        return new ParameterizedTypeReference<List<T>>() {
            @Override
            public Type getType() {
                return new MyParameterizedTypeImpl((ParameterizedType) super.getType(), new Type[]{clazz});
            }
        };
    }

    private static final class MyParameterizedTypeImpl implements ParameterizedType {

        private final ParameterizedType delegate;
        private final Type[] actualTypeArguments;

        MyParameterizedTypeImpl(ParameterizedType delegate, Type[] actualTypeArguments) {
            this.delegate = delegate;
            this.actualTypeArguments = actualTypeArguments;
        }

        @Override
        public Type[] getActualTypeArguments() {
            return actualTypeArguments.clone();
        }

        @Override
        public Type getRawType() {
            return delegate.getRawType();
        }

        @Override
        public Type getOwnerType() {
            return delegate.getOwnerType();
        }
    }
}
