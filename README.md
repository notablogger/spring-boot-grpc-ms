# spring-grpc

A small, two-service learning project for practicing **gRPC with Spring Boot
and Gradle** on **Java 21**, secured end-to-end with **Keycloak** as a shared
identity provider.

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

Every call is authenticated: a caller gets a JWT from Keycloak, order-service
validates it and enforces `customer`/`admin` access rules, then relays the
same token to payment-service, which independently re-validates it. See
[docs/auth.md](docs/auth.md) for the full picture.

Both services hold their sample data (orders / payments) as bundled read-only
JSON fixtures loaded into memory at startup — there's no database to set up.

## Project layout

```
spring-grpc/
├── proto/payment.proto        # shared gRPC contract (single source of truth)
├── order-service/             # gRPC client + REST API
├── payment-service/           # gRPC server
├── keycloak/realm-export.json # pre-configured realm, roles, test users
├── docs/                      # container diagram, proto pipeline, auth docs
├── docker-compose.yml         # runs Keycloak only
└── .github/workflows/ci.yml   # build + test pipeline
```

## Prerequisites

- Java 21
- Docker + Docker Compose (for Keycloak — see below)

No local Gradle install is required — this project uses the Gradle wrapper
(`./gradlew`).

## Running it

Both services are run directly with Gradle; Docker is only used for Keycloak.

**1. Start Keycloak:**

```bash
docker compose up keycloak
```

This imports the `spring-grpc` realm automatically (roles, a test client, and
three test users — see [docs/auth.md](docs/auth.md)). Keycloak comes up on
`http://localhost:8081`.

**2. Start both services**, in two more terminals from the repository root:

```bash
./gradlew :payment-service:bootRun
```

```bash
./gradlew :order-service:bootRun
```

- `payment-service` gRPC server comes up on `localhost:9090`
- `order-service` REST API comes up on `localhost:8080`, and is configured to
  reach `payment-service` at `localhost:9090` and Keycloak at
  `localhost:8081` by default (see each service's `application.yml`).

## Calling the REST endpoint

`order-service` exposes:

```
GET /api/v1/orders/{orderId}/payment-status
```

This requires a bearer token. Get one from Keycloak first:

```bash
TOKEN=$(curl -s -X POST http://localhost:8081/realms/spring-grpc/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=password' \
  -d 'client_id=spring-grpc-app' \
  -d 'username=cust-01' \
  -d 'password=customer123' \
  | jq -r .access_token)
```

Then call the endpoint, against one of the sample orders bundled in
[`order-service/src/main/resources/data/orders.json`](order-service/src/main/resources/data/orders.json):

```bash
curl -s http://localhost:8080/api/v1/orders/ORD-1001/payment-status \
  -H "Authorization: Bearer $TOKEN" | jq
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

Other ids to try (as `cust-01`, whose orders are `ORD-1001` and `ORD-1004`):

| Order id | What happens |
|---|---|
| `ORD-1001` | `COMPLETED` payment |
| `ORD-1004` | `REFUNDED` payment |
| `ORD-1002`, `ORD-1003`, `ORD-1005` | Belong to a different customer → `403` |
| anything nonexistent | Order doesn't exist → `404` |
| (no `Authorization` header) | → `401` |

Log in as `admin` / `admin123` instead to check any order regardless of who
it belongs to, including `ORD-1002` (`PENDING`), `ORD-1003` (`FAILED`), and
`ORD-1005` (order exists, but no payment record → `404`). See
[docs/auth.md](docs/auth.md) for the full set of test users and the
role/ownership rules.

## Testing

```bash
./gradlew test
```

`order-service` includes an integration test
([`OrderPaymentStatusIntegrationTest`](order-service/src/test/java/com/example/orderservice/OrderPaymentStatusIntegrationTest.java))
that boots the full Spring context and exercises the REST endpoint — covering
authentication, role gating, and order-ownership — against an in-process fake
`payment-service`, so it runs without Docker, a real gRPC server, or a real
Keycloak. `payment-service` has its own
([`PaymentServiceSecurityIntegrationTest`](payment-service/src/test/java/com/example/paymentservice/security/PaymentServiceSecurityIntegrationTest.java))
exercising its real security interceptor chain the same way. Both run in CI —
see [`.github/workflows/ci.yml`](.github/workflows/ci.yml).

## Regenerating gRPC/protobuf code

Generation from `proto/payment.proto` happens automatically as part of
`./gradlew build`. See [docs/proto-pipeline.md](docs/proto-pipeline.md) for
details and how to trigger it manually.
