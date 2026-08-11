# spring-grpc

A small, two-service learning project for practicing **gRPC with Spring Boot
and Gradle** on **Java 21**.

- **`order-service`** — a Spring Boot REST API. It exposes an endpoint to
  trigger a payment status check for an order id.
- **`payment-service`** — a Spring Boot gRPC server. It looks up the payment
  status for an order id and returns it to whoever calls it over gRPC.

`order-service` is the **gRPC client**, `payment-service` is the **gRPC
server**. The contract between them is defined once, at the repository root,
in [`proto/payment.proto`](proto/payment.proto), and compiled into both
services at build time. See [docs/proto-pipeline.md](docs/proto-pipeline.md)
for how that works, and [docs/container-diagram.md](docs/container-diagram.md)
for the system diagram.

Both services hold their sample data (orders / payments) as bundled read-only
JSON fixtures loaded into memory at startup — there's no database to set up.

## Project layout

```
spring-grpc/
├── proto/payment.proto        # shared gRPC contract (single source of truth)
├── order-service/             # gRPC client + REST API
├── payment-service/           # gRPC server
├── docs/                      # container diagram, proto pipeline docs
├── docker-compose.yml         # run both services together
└── .github/workflows/ci.yml   # build + test pipeline
```

## Prerequisites

- Java 21 (for running locally without Docker)
- Docker + Docker Compose (for the Docker workflow below)

No local Gradle install is required — this project uses the Gradle wrapper
(`./gradlew`).

## Run with Docker Compose

This builds and starts both services together, wired to talk to each other
over the compose network:

```bash
docker compose up --build
```

- `payment-service` gRPC server comes up on `localhost:9090`
- `order-service` REST API comes up on `localhost:8080`

Stop everything with:

```bash
docker compose down
```

## Run locally with Gradle (no Docker)

In two terminals, from the repository root:

```bash
./gradlew :payment-service:bootRun
```

```bash
./gradlew :order-service:bootRun
```

`order-service` is configured to reach `payment-service` at
`localhost:9090` by default (see `order-service/src/main/resources/application.yml`).

## Calling the REST endpoint

`order-service` exposes:

```
GET /api/v1/orders/{orderId}/payment-status
```

Try it against one of the sample orders bundled in
[`order-service/src/main/resources/data/orders.json`](order-service/src/main/resources/data/orders.json):

```bash
curl -s http://localhost:8080/api/v1/orders/ORD-1001/payment-status | jq
```

```json
{
  "orderId": "ORD-1001",
  "paymentId": "PAY-5001",
  "status": "COMPLETED",
  "amount": 129.99,
  "currency": "USD"
}
```

Other ids to try:

| Order id | What happens |
|---|---|
| `ORD-1001` | `COMPLETED` payment |
| `ORD-1002` | `PENDING` payment |
| `ORD-1003` | `FAILED` payment |
| `ORD-1004` | `REFUNDED` payment |
| `ORD-1005` | Order exists, but no payment record → `404` |
| anything else | Order doesn't exist → `404` |

## Testing

```bash
./gradlew test
```

`order-service` includes an integration test
([`OrderPaymentStatusIntegrationTest`](order-service/src/test/java/com/example/orderservice/OrderPaymentStatusIntegrationTest.java))
that boots the full Spring context and exercises the REST endpoint against an
in-process fake `payment-service`, so it runs without Docker or a real gRPC
server. This test also runs in CI — see
[`.github/workflows/ci.yml`](.github/workflows/ci.yml).

## Regenerating gRPC/protobuf code

Generation from `proto/payment.proto` happens automatically as part of
`./gradlew build`. See [docs/proto-pipeline.md](docs/proto-pipeline.md) for
details and how to trigger it manually.
