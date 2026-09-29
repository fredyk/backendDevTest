# Backend dev technical test
We want to offer a new feature to our customers showing similar products to the one they are currently seeing. To do this we agreed with our front-end applications to create a new REST API operation that will provide them the product detail of the similar products for a given one. [Here](./similarProducts.yaml) is the contract we agreed.

We already have an endpoint that provides the product Ids similar for a given one. We also have another endpoint that returns the product detail by product Id. [Here](./existingApis.yaml) is the documentation of the existing APIs.

**Create a Spring boot application that exposes the agreed REST API on port 5000.**

![Diagram](./assets/diagram.jpg "Diagram")

Note that _Test_ and _Mocks_ components are given, you must only implement _yourApp_.

## Testing and Self-evaluation
You can run the same test we will put through your application. You just need to have docker installed.

First of all, you may need to enable file sharing for the `shared` folder on your docker dashboard -> settings -> resources -> file sharing.

Then you can start the mocks and other needed infrastructure with the following command.
```
docker-compose up -d simulado influxdb grafana
```
Check that mocks are working with a sample request to [http://localhost:3001/product/1/similarids](http://localhost:3001/product/1/similarids).

To execute the test run:
```
docker-compose run --rm k6 run scripts/test.js
```
Browse [http://localhost:3000/d/Le2Ku9NMk/k6-performance-test](http://localhost:3000/d/Le2Ku9NMk/k6-performance-test) to view the results.

## Evaluation
The following topics will be considered:
- Code clarity and maintainability
- Performance
- Resilience

---

# Solution

`GET /product/{productId}/similar` on port 5000, built with Spring Boot 3.5 on Java 21.

## Run it

Everything in Docker, the app included:
```
docker compose up -d --build simulado influxdb grafana app
docker compose run --rm k6 run scripts/test.js
```

Or the app on the host, against the mocks in Docker (needs JDK 21):
```
docker compose up -d simulado influxdb grafana
./mvnw spring-boot:run
```

Tests (unit, plus integration tests against WireMock):
```
./mvnw verify
```

On rootless Docker `host-gateway` does not reach the host, so k6 cannot see the app. Pass the host IP instead: `K6_HOST_GATEWAY=<host ip> docker compose run --rm k6 run scripts/test.js`. Without the variable the compose file behaves exactly as the original.

## Architecture

Ports and adapters, so the use case knows nothing about HTTP or Spring:

```
com.itx.similarproducts
├── domain           ProductDetail, the two ports to the existing APIs, their exceptions
├── application      GetSimilarProductsService: the use case
└── infrastructure
    ├── client       RestClient adapters for /similarids and /product/{id}
    ├── controller   GET /product/{productId}/similar
    └── config       wiring, HTTP client, cache, executor
```

## How each case in the mocks is handled

| Case | Behaviour |
|---|---|
| Product does not exist (`/similarids` answers 404) | `404` with no body |
| A similar product answers 404 or 500 | It is left out; the rest are returned in order |
| A similar product is slow (100 ms, 1 s) | Details are fetched in parallel, so the response takes as long as the slowest one, not the sum |
| A similar product is very slow (5 s, 50 s) | 2 s read timeout; it is left out |
| The existing API keeps failing | A circuit breaker opens and answers from the fallback without calling it, until it recovers |
| The same product requested by many users | Responses are cached for 5 s |

## Decisions

- **Virtual threads** for both Tomcat and the detail requests. The work is blocking I/O, so one virtual thread per request is cheaper and simpler than sizing a pool or going reactive.
- **Order is preserved** by collecting the futures in the order of the similar ids, not in the order they complete.
- **Partial responses over errors.** One failing neighbour should not cost the customer the whole list. Only a missing main product is an error.
- **One circuit breaker** shared by both endpoints, since they are the same upstream service. A 404 is an answer, not an outage, so it does not count as a failure.
- **Fallback methods are public.** Resilience4j invokes them by reflection; on private methods it toggles accessibility on every call, which raced under load and produced 500s.
- **Only successes are cached.** A 404 or a failure is asked again next time, so a product that comes back is visible straight away.
- **Timeouts and TTL are properties** (`existing.api.*`) and the base URL can be overridden with `EXISTING_API_BASE_URL`.

## Results

k6 against the app in Docker, all five scenarios at 200 VUs:

| Metric | Value |
|---|---|
| Requests | 16,479 (265/s) |
| Median | 2.75 ms |
| p90 | 68 ms |
| Max | 2.07 s (bounded by the read timeout) |
| Errors in the app log | 0 |
