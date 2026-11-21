<p style="text-align:left">
  <img src="https://www.kanopus.cl/assets/kanopus_black.png" width="220" alt="Kanopus logo"/>
</p>

![Maven](https://img.shields.io/maven-central/v/cl.kanopus.util/klib-rest-template) ![License](https://img.shields.io/badge/license-Apache%20License%202.0-blue) ![Java](https://img.shields.io/badge/java-21+-orange)

# klib-rest-template

A reusable Java library that provides an abstract HTTP client base class for building strongly-typed REST API
integrations within the Kanopus ecosystem.
Built on top of **Spring Framework 7.1** (`spring-web`, no Spring Boot required), **Jackson 3**, **Apache HttpClient 5.x**, and **klib-common**.

## ✨ Features

- `AbstractRestClientApi`: base class built on Spring's **`RestClient`** with **Apache HttpClient 5.x** as transport (recommended).
- `AbstractApi`: legacy `RestTemplate` based class, **deprecated** (see [Changes in 4.20.0](#-changes-in-4200)).
- Secure TLS by default; custom **trust store / `SSLContext`** (also for mTLS) through `HttpClientOptions`.
- Configurable **connect / response / connection-pool timeouts** with sane defaults.
- Query parameters are **strictly URL-encoded** (`&`, `=`, `+`, `#`… in values cannot break or inject parameters).
- Automatic **error handling**: HTTP 4xx/5xx responses are surfaced as `CustomClientException`, also when the body is
  empty or not JSON (e.g. an HTML page from a proxy).
- Ready-to-use helper methods for `GET`, `POST`, `PUT`, and `DELETE` requests, including generic and parameterized
  response types (`List<T>`, `Paginator<T>`).
- `AutoCloseable`: the HTTP connection pool is released on `close()` (Spring calls it automatically on bean destruction).

## 🚀 Installation

Add the dependency to your `pom.xml`:

```xml

<dependency>
	<groupId>cl.kanopus.util</groupId>
	<artifactId>klib-rest-template</artifactId>
	<version>4.20.0</version>
</dependency>
```

> The version follows the parent `kanopus-core-parent` release train.

---

## 🧱 Core Classes

### 🔷 AbstractRestClientApi (recommended)

`cl.kanopus.rest.api.AbstractRestClientApi` is the base class you extend to create a typed client for a specific REST service.

```
AbstractRestClientApi (abstract, AutoCloseable)
 ├── client          : RestClient (Apache HttpClient 5, secure TLS, timeouts)
 ├── KANOPUS_API_KEY : "X-API-Key"
 └── createHeaders() : HttpHeaders  (abstract — implement your auth/headers here)
```

Constructors:

| Constructor                                         | Description                                                                                      |
|-----------------------------------------------------|--------------------------------------------------------------------------------------------------|
| `AbstractRestClientApi(url)`                        | Default `HttpClientOptions` (JVM trust store, default timeouts).                                 |
| `AbstractRestClientApi(url, HttpClientOptions)`     | Custom timeouts, trust store, `SSLContext` or hostname verifier.                                 |
| `AbstractRestClientApi(url, RestClient.Builder)`    | Caller supplied builder (interceptors, converters, `MockRestServiceServer.bindTo(builder)`); its request factory is not changed. |

### 🔶 AbstractApi (deprecated)

`cl.kanopus.rest.api.AbstractApi` exposes the same helper methods over `RestTemplate`. `RestTemplate` is
`@Deprecated(forRemoval = true)` in Spring Framework 7.1, so `AbstractApi` is deprecated since 4.20.0 and will be removed
in the next Spring Boot line (4.30.0). Migration is usually just changing `extends AbstractApi` to
`extends AbstractRestClientApi` (the `client` field becomes a `RestClient`).

**Available request methods:**

| Method                                                               | Description                                  |
|----------------------------------------------------------------------|----------------------------------------------|
| `sendGet(path, Class<T>)`                                            | GET request, single object response          |
| `sendGet(path, Class<T>, Map params)`                                | GET with query parameters                    |
| `sendGet(path, ParameterizedTypeReference<T>)`                       | GET with generic response type               |
| `sendGet(path, ParameterizedTypeReference<T>, Map params)`           | GET with generic type + query parameters     |
| `sendGet(path, ParameterizedTypeReference<T>, Searcher)`             | GET with pagination/sorting support          |
| `sendGetList(path, Class<T>)`                                        | GET → `List<T>`                              |
| `sendGetList(path, Class<T>, Map params)`                            | GET → `List<T>` with query parameters        |
| `sendGetPaginator(path, Class<T>)`                                   | GET → `Paginator<T>`                         |
| `sendGetPaginator(path, Class<T>, Map params)`                       | GET → `Paginator<T>` with query parameters   |
| `sendGetPaginator(path, Class<T>, Searcher)`                         | GET → `Paginator<T>` with pagination/sorting |
| `sendPost(path, request)`                                            | POST, no response body                       |
| `sendPost(path, request, Class<T>)`                                  | POST → `T`                                   |
| `sendPost(path, request, ParameterizedTypeReference<T>, Map params)` | POST with generic type + query parameters    |
| `sendPostMap(path, MultiValueMap)`                                   | POST with form-encoded parameters            |
| `sendPut(path)`                                                      | PUT, no body                                 |
| `sendPut(path, request)`                                             | PUT with request body                        |
| `sendPut(path, request, Class<T>)`                                   | PUT → `T` (public in `AbstractRestClientApi`, protected in `AbstractApi`) |
| `sendDelete(path)`                                                   | DELETE, no response                          |
| `sendDelete(path, Class<T>)`                                         | DELETE → `T`                                 |

### ⚠️ Exception Model

| Class                   | Description                                                                                             |
|-------------------------|---------------------------------------------------------------------------------------------------------|
| `CustomClientException` | Thrown when a remote API returns an HTTP error (4xx/5xx). Carries `HttpStatusCode`, `ErrorMessage` and the (truncated) raw body (`getResponseBody()`). |
| `ErrorMessage`          | DTO that maps the remote API's error response body (`timestamp`, `status`, `error`, `message`, `path`). |
| `ErrorCode`             | Abstract base class for defining typed error codes with message templates (`{0}`, `{1}`, …).            |
| `ServiceException`      | Thrown for local/service-layer failures; wraps an `ErrorCode` instance.                                 |

---

## 🚀 Usage Guide

### 1️⃣ Create a typed API client

```java

@Component
public class ProductApi extends AbstractRestClientApi {

    public ProductApi(@Value("${services.products.url}") String url) {
        super(url);
    }

    @Override
    protected HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(KANOPUS_API_KEY, "your-api-key");
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
```

### 2️⃣ Call remote endpoints

```java
// GET single object
ProductDto product = productApi.sendGet("/products/42", ProductDto.class);

// GET list
List<ProductDto> products = productApi.sendGetList("/products", ProductDto.class);

// GET paginated
Paginator<ProductDto> page = productApi.sendGetPaginator("/products", ProductDto.class);

// GET with query parameters
Map<String, Object> params = Map.of("category", "electronics", "active", true);
List<ProductDto> filtered = productApi.sendGetList("/products", ProductDto.class, params);

// POST
ProductDto created = productApi.sendPost("/products", newProductDto, ProductDto.class);

// PUT
productApi.sendPut("/products/42",updatedDto);

// DELETE
productApi.sendDelete("/products/42");
```

### 3️⃣ Handle errors

```java
    try {
        ProductDto product = productApi.sendGet("/products/99", ProductDto.class);
    } catch(
        CustomClientException e){
        log.error("Status: {}, Message: {}", e.getStatus(), e.getDetails().getMessage());
    }
```


### 4️⃣ Timeouts and TLS

```java
HttpClientOptions options = HttpClientOptions.builder()
        .connectTimeout(Duration.ofSeconds(5))      // default 10s
        .responseTimeout(Duration.ofSeconds(20))    // default 60s (socket inactivity while waiting for data)
        .connectionRequestTimeout(Duration.ofSeconds(5)) // default 30s (lease from the pool)
        .trustStore(Path.of("/etc/app/truststore.p12"), password, "PKCS12") // or .trustStore(KeyStore) / .sslContext(ctx)
        .build();

public ProductApi(String url) {
    super(url, options);
}
```

`Duration.ZERO` disables a timeout. Without a trust store the JVM default (`cacerts`) and standard hostname
verification are used. Trusting all certificates is only possible through the deprecated
`AbstractApi(url, true)` constructor, which now logs a `WARN` on every instantiation.

## 📝 Changes in 4.20.0

- **New** `AbstractRestClientApi` (Spring `RestClient`) and `HttpClientOptions`.
- **Deprecated (for removal)**: `AbstractApi`, `AbstractApi(String, boolean)`, `AbstractApi#createHttpClientAcceptsUntrustedCerts()`,
  `AbstractApi.MyApiRestTemplateErrorHandler`. New constructor `AbstractApi(String, HttpClientOptions)`. `AbstractApi` is now `AutoCloseable`.
- **Dependency**: `spring-boot-starter-web` (which pulled Tomcat, Spring MVC and Boot) was replaced by `spring-web` +
  `tools.jackson.core:jackson-databind`. Applications that relied on this library to bring Spring Boot / Tomcat
  transitively must declare them explicitly.
- **Behavior changes**
  - Default timeouts: connect 10s, response 60s, pool lease 30s (previously HttpClient defaults: 3 min connect, no response timeout).
  - Query parameter names/values are strictly percent-encoded (`a&b` → `a%26b`, `1+1` → `1%2B1`, UTF-8). `null` values
    (and a `null` params map / `Searcher` / `Searcher` fields) are skipped instead of sending `?name`; `Iterable`/array
    values are sent as repeated parameters.
  - `sendPut(path)` sends an empty body (it used to serialize `java.lang.String.class` as the body).
  - Error bodies are parsed with Jackson (was Gson) in UTF-8 (or the declared charset). Empty or non-JSON bodies no
    longer throw `NullPointerException`/`JsonSyntaxException`; a `CustomClientException` with a synthetic
    `ErrorMessage` (status, reason phrase, truncated raw text) is thrown instead. `CustomClientException` accepts a
    `null` `ErrorMessage`.
  - Error logs are `WARN` for 4xx and `ERROR` for 5xx, include only method + path (no query string) and a message
    truncated to 512 chars; the logger category is now `cl.kanopus.rest.api.AbstractApi` / `AbstractRestClientApi`.
  - `ErrorCode`: constructor is now `protected` (subclasses outside the package are possible); placeholders are replaced
    in a single pass, so an argument containing `{0}` no longer causes an infinite loop.
  - The deprecated trust-all mode uses `DefaultClientTlsStrategy` (`setTlsSocketStrategy`) instead of the deprecated
    `SSLConnectionSocketFactory`, and logs a `WARN`.

## 👤 Author

⭐**Pablo Andrés Díaz Saavedra** — Founder of **Kanopus – Software Guided by the Stars**⭐

Kanopus is building a constellation of developers creating tools, libraries and platforms that simplify software engineering.

[GitHub](https://github.com/godheaven) | [LinkedIn](https://www.linkedin.com/in/pablo-diaz-saavedra-4b7b0522/) | [Website](https://kanopus.cl)

## 📄 License

This software is licensed under the Apache License, Version 2.0. See the LICENSE file for details.
I hope you enjoy it.

[![Apache License, Version 2.0](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg)](https://opensource.org/license/apache-2-0)

## 🛟 Support

For support or questions contact: 📧 [soporte@kanopus.cl](mailto:soporte@kanopus.cl)
