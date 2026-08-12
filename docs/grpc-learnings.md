# gRPC Learnings

A plain-language reference of the gRPC concepts, trade-offs, and mistakes we
ran into while building and revising the `WatchPaymentStatus` feature in
this project.

## The four RPC shapes

Every gRPC call is really just a request and a response, and either side can
be a single message or a stream of messages. "Unary" is just the name for
the case where both sides are a single message. The four "types" people talk
about are just the four combinations of that:

| Shape | Client sends | Server sends back | Where you'd see this in the wild | Used here? |
|---|---|---|---|---|
| Unary | one message | one message | a normal REST `GET` or `POST` | Yes — `CheckPaymentStatus` |
| Server streaming | one message | many messages, over time | a live stock ticker, `kubectl get pods --watch` | Yes — `WatchPaymentStatus` |
| Client streaming | many messages, over time | one message | uploading a large file in chunks | No |
| Bidirectional streaming | many messages, over time | many messages, over time | a chat app, a live video call | No |

![The four gRPC RPC shapes](images/grpc-rpc-types.svg)

This project only needed the first two — order-service always sends a single
request, it just sometimes wants more than one answer back over time.

## What a "stream" actually is

A gRPC call opens one connection (technically, one HTTP/2 stream) and sends
messages down it, one at a time. A unary call just happens to send exactly
one message before closing that connection. A streaming call sends more
than one, spread out over time, before closing it the same way. There's no
separate "streaming mode" under the hood — just a different number of
messages on the same kind of connection.

## How a stream can end

| How it ends | Who notices | How fast | Set up in this project? |
|---|---|---|---|
| Explicit cancel | Both sides, if wired up to do so | Instant | No |
| Normal completion | Both sides | Instant | Yes |
| Process crash | Both sides, automatically | A few seconds | Yes, automatically |
| Silent network failure (no clean disconnect) | Nobody, unless keepalive is configured | Never, without keepalive | No |

![Four ways a WatchPaymentStatus stream can end](images/grpc-stream-end-paths.svg)

**Explicit cancel** is a real gRPC feature — you can interrupt a caller's
thread to abandon a call early, and the server can register a callback to
find out when that happens. We tried this (see "What changed" below) and
then removed it: both sides of `WatchPaymentStatus` already stop on their
own once a payment reaches a final status, so there was nothing left for an
explicit cancel to do.

**Silent network failure** is the one gap this project has, on purpose, for
now. If a connection just goes dead — no crash, no clean close, packets
simply stop arriving — neither side finds out unless "keepalive" is turned
on (periodic pings that time out if nothing answers). We haven't configured
it here.

**Deadlines** are a separate idea from all of the above: a deadline says "I
won't wait longer than X for a reply," regardless of why the other side is
slow. A stream that's *meant* to stay open (like our watch) shouldn't have
one. A quick, single-answer call made while a user is waiting on a web page
generally should — otherwise a slow or stuck server can hang that request
forever. This project doesn't set one on `CheckPaymentStatus` either, which
is a gap worth fixing before this went anywhere real.

## Pushing updates to multiple listeners isn't built in

gRPC has no built-in way to say "notify everyone currently watching this."
Neither grpc-java nor Spring's gRPC support (`spring-boot-starter-grpc-server`)
gives you a subscriber list or a broadcast helper — you build that part
yourself, the same way you would in comparable systems:

- A chat server built on websockets keeps a list of open connections per
  chat room, so a message from one user can be sent to everyone else in
  that room.
- Kubernetes' own API server keeps a similar list of open "watch"
  connections, so it can tell every client watching a resource when it
  changes.

This project's first version of `WatchPaymentStatus` did the same thing —
`PaymentWatchRegistry`, a list of open connections per order, fed by the
REST endpoint that changes a payment's status. It worked, but came with two
real problems:

- **Thread safety**: the object you call to send a message (`StreamObserver`)
  isn't safe to call from two threads at once. A shared list makes that
  mistake easy to make by accident.
- **One instance only**: that list lives in one server's memory. Run two
  copies of payment-service behind a load balancer, and each one only knows
  about the listeners connected to *it* — half your updates silently go
  nowhere.

![An in-memory watch registry is per-instance state](images/watch-registry-single-instance.svg)

## gRPC streaming vs. a message broker (Kafka)

The problems above aren't reasons to avoid gRPC streaming — they're the
signal for when a broker like Kafka is the better tool instead.

| | gRPC streaming | Kafka (or similar) |
|---|---|---|
| If nobody's listening when it happens | the update is lost | saved, and a late listener can catch up |
| Does the sender need a live connection to each listener? | yes | no — it just publishes and moves on |
| Many independent listeners | each needs its own connection; your code fans out | the broker fans out for you |
| Extra infrastructure to run | none | a broker cluster |

Kafka-style systems are the standard choice for things like order-processing
pipelines, audit trails, or anywhere multiple unrelated services need to
react to "this happened" independently. The two aren't really competitors,
either — plenty of real systems use Kafka as the durable backbone and gRPC
(or websockets) as the "last mile" that pushes a live update to whoever's
actually looking at a screen right now.

The real question to ask isn't "does gRPC streaming have limits" (it always
will) — it's "do I need replay for someone who wasn't listening yet, a
permanent record of every change, or several independent services reacting
to the same event?" If yes to any of those, that's Kafka. If it's really
just "tell whoever's currently watching," gRPC streaming is enough.

## What changed in this project, and why

`WatchPaymentStatus` went through two designs:

1. **Registry-based push** (removed): payment-service kept a list of open
   connections and pushed new values into them directly from the REST
   endpoint. Removed because of the two problems above — shared state that
   wasn't thread-safe, and an assumption that only one instance would ever
   run.
2. **Caller-driven polling**: each call to `WatchPaymentStatus` just checks
   the payment's status, waits, and checks again — up to `watch_count`
   times, `watch_interval_seconds` apart, set by the caller (order-service).
   Nothing is shared between the REST write and the gRPC read; a status
   change becomes visible on the *next* check, not instantly.
3. **Caller-chosen target status** (current): instead of stopping at *any*
   final status, the caller now also says which status it's actually
   waiting for (`target_status`), and the watch only stops early once the
   payment reaches exactly that one. Watching for `COMPLETED` on a payment
   that ends up `FAILED` no longer stops early — it keeps polling, seeing
   the same unchanged value, until `watch_count` runs out. This trades some
   wasted polling (if the target status never happens) for letting the
   caller — not payment-service — decide what "done" means for its own
   purposes.

The trade: a little latency (up to one interval's delay) in exchange for no
shared state to get wrong and no assumption about how many servers are
running.

A manual "stop watching" endpoint was added on top of that, then removed
again — once both sides already stopped on their own the moment a payment
settled, there was nothing left for a manual cancel to actually do.

Around the same time, payment-service dropped its own domain `PaymentStatus`
enum — a copy of the proto enum, translated back and forth by hand in
`PaymentProtoMapper.toProtoStatus()` — and switched to using the generated
proto enum directly in `Payment`, the repository, and the REST controller.
That removes a translation step that had to be updated by hand every time a
new status was added to the proto (three were: `AUTHORISED`,
`PARTIALLY_REFUNDED`, `VOIDED`). The cost is coupling the domain model
directly to the wire format, which is usually worth avoiding for exactly the
reason the old domain enum existed in the first place — so the wire contract
could change without touching internal code. For a payment lifecycle that's
realistically defined by the proto contract anyway, this is a reasonable
simplification at this project's size; it's worth revisiting if the domain
model ever needs to evolve independently of the wire format.

## Checklist

| Do this | Why |
|---|---|
| Reuse one request/response message across RPCs that share the same shape | Less duplication; don't invent a new message type just for its own sake |
| Prefix proto enum values with the enum's name (e.g. `PAYMENT_STATUS_PENDING`) | Enum values share a namespace with the whole file, not just their own enum — this project doesn't do this yet |
| Set a deadline on any quick call made while something else is waiting on it | Otherwise a stuck server can hang your request forever |
| Turn on gRPC keepalive for any stream expected to stay open more than a few seconds | It's the only way to detect a silently dead connection |
| Never call a `StreamObserver`'s methods from two threads at once | It isn't documented as safe, and a shared registry makes this mistake easy |
| Prefer a bounded, caller-driven stream over an open-ended one with shared server state | Simpler, and avoids the two problems above, when a live push isn't a hard requirement |
| Reach for a broker (Kafka or similar) for durability, replay, or multiple independent listeners | Not just because a hand-rolled in-memory version has limits — every approach does |
