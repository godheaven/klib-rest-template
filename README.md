<p style="text-align:left">
  <img src="https://www.kanopus.cl/assets/kanopus_black.png" width="220" alt="Kanopus logo"/>
</p>

![Maven](https://img.shields.io/maven-central/v/cl.kanopus.util/klib-rest-template) ![License](https://img.shields.io/badge/license-Apache%20License%202.0-blue) ![Java](https://img.shields.io/badge/java-17+-orange)

# klib-rest-template

A reusable Java library that provides an abstract HTTP client base class for building strongly-typed REST API
integrations within the Kanopus ecosystem.
Built on top of **Spring Boot 4.0 / Spring Framework 7.0**, **Apache HttpClient 5.x**, and **klib-common**.

## ✨ Features

- A configurable `RestTemplate` pre-wired with **Apache HttpClient 5.x** as the underlying transport.
- **SSL/TLS trust-all** support (useful for internal/dev environments — **not recommended for production**).
- Automatic **error handling**: HTTP 4xx/5xx responses are parsed and surfaced as `CustomClientException`.
- Ready-to-use helper methods for `GET`, `POST`, `PUT`, and `DELETE` requests, including generic and parameterized
  response types (`List<T>`, `Paginator<T>`).

## 🚀 Installation

Add the dependency to your `pom.xml`:

```xml

<dependency>
	<groupId>cl.kanopus.util</groupId>
	<artifactId>klib-rest-template</artifactId>
	<version>4.05.1</version>
</dependency>
```

> The version follows the parent `kanopus-core-parent` release train.

---

## 🧱 Core Classes

### 🔷 AbstractApi

`cl.kanopus.rest.api.AbstractApi` is the base class you extend to create a typed client for a specific REST service.

```
AbstractApi (abstract)
 ├── client        : RestTemplate  (pre-configured, SSL-permissive)
 ├── KANOPUS_API_KEY : "X-API-Key"
 └── createHeaders() : HttpHeaders  (abstract — implement your auth/headers here)
```

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
| `sendDelete(path)`                                                   | DELETE, no response                          |
| `sendDelete(path, Class<T>)`                                         | DELETE → `T`                                 |

### ⚠️ Exception Model

| Class                   | Description                                                                                             |
|-------------------------|---------------------------------------------------------------------------------------------------------|
| `CustomClientException` | Thrown when a remote API returns an HTTP error (4xx/5xx). Carries `HttpStatusCode` and `ErrorMessage`.  |
| `ErrorMessage`          | DTO that maps the remote API's error response body (`timestamp`, `status`, `error`, `message`, `path`). |
| `ErrorCode`             | Abstract base class for defining typed error codes with message templates (`{0}`, `{1}`, …).            |
| `ServiceException`      | Thrown for local/service-layer failures; wraps an `ErrorCode` instance.                                 |

---

## 🚀 Usage Guide

### 1️⃣ Create a typed API client

```java

@Component
public class ProductApi extends AbstractApi {

    @Value("${services.products.url}")
    public ProductApi(String url) {
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
