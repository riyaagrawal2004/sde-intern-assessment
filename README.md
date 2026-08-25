\# SDE Intern Assessment - Order Update \& Position Maintaining Services



Two independently runnable Java services that process a stream of trading

order events and maintain the current net position per symbol.



\## Architecture \& Communication Choices



\### Two separate services, one process each

\- Order Update Service (com.indothai.orderupdate) reads order\_updates.csv

&#x20; incrementally, validates each row against the Event Contract, throttles

&#x20; emission to a configurable rate, and sends valid events to the Position

&#x20; Maintaining Service.

\- Position Maintaining Service (com.indothai.position) receives events

&#x20; over HTTP, maintains an in-memory net position per symbol, and exposes

&#x20; GET /position.



They run as separate JVM processes, matching the requirement that both

services be independently runnable and communicate over a defined interface.



\### Why plain HTTP + JSON for inter-service communication

\- The assessment explicitly allows it: "HTTP between the two services is an

&#x20; acceptable solution."

\- It requires no extra infrastructure (no broker/queue to install or run),

&#x20; which fits the stated preference for a simple, correct, and well-tested

&#x20; solution over unnecessary infrastructure or abstraction.

\- It's trivial to test and debug (curl, Postman, or a plain HttpClient in

&#x20; tests) and is self-documenting via the two named endpoints.



\### Event payload / schema

A flat JSON object is sent on POST /events:



&#x20;   {

&#x20;     "event\_id": "evt-0001",

&#x20;     "symbol": "RELIANCE",

&#x20;     "transaction\_type": "BUY",

&#x20;     "quantity": 90

&#x20;   }



No external JSON library is used since the payload is intentionally flat (no

nesting or arrays), so a small hand-rolled parser/serializer (SimpleJson)

keeps the dependency footprint at zero for something this small.



\### How connection / delivery errors are surfaced

\- Order Update Service to Position Service: PositionServiceClient.send()

&#x20; never throws. Connection errors, timeouts, and non-2xx responses are caught

&#x20; and logged with the event\_id, and the service moves on to the next CSV

&#x20; row. A single failed send does not stop the rest of the file from being

&#x20; processed.

\- Position Service HTTP layer: malformed JSON or a payload that fails

&#x20; Event Contract validation gets an HTTP 400 response with a JSON error

&#x20; body; wrong HTTP method gets 405. The server never crashes on bad input.



\### Delivery limitations

\- Delivery is at-most-once / best-effort: one HTTP POST per event, no

&#x20; retry queue, no acknowledgment beyond the immediate HTTP response.

\- Durable delivery, exactly-once broker guarantees, and recovery after a

&#x20; full process restart are explicitly out of scope per the assessment, and

&#x20; are not implemented here.

\- In-memory idempotency state (seenEventIds) and positions reset if the

&#x20; Position Maintaining Service restarts.



\### Concurrency

PositionStore guards both the position map and the seenEventIds set with

a single ReentrantLock, so duplicate-check and position-update happen as one

atomic operation, and GET /position reads are consistent even while events

are concurrently being applied. The HTTP server uses a fixed thread pool so

/position remains responsive while /events is being posted to.



\### Validation placement

\- Event Contract structural validation (non-empty event\_id/symbol,

&#x20; transaction\_type exactly BUY/SELL, positive integer quantity)

&#x20; happens in the Order Update Service's RowValidator, before an event is

&#x20; ever sent.

\- The same validation is repeated defensively in the Position Service's

&#x20; HTTP handler, since it is a separate process and must not crash or behave

&#x20; incorrectly if called by any other client with bad data.

\- Duplicate event\_id detection happens in the Position Maintaining

&#x20; Service (PositionStore), since idempotency is about state the position

&#x20; service owns.



\## Setup and Run Instructions



\### Requirements

\- Java 17

\- Maven (or Eclipse with the m2e plugin, which bundles Maven)



\### Build



&#x20;   mvn clean compile



\### Run the Position Maintaining Service



&#x20;   mvn compile exec:java -Dexec.mainClass="com.indothai.position.PositionServiceMain" -Dexec.args="0.0.0.0 8080"



Or, from an IDE: run PositionServiceMain.main() directly.



Arguments (both optional, shown with their defaults):



&#x20;   \[host]   default: 0.0.0.0

&#x20;   \[port]   default: 8080



\### Run the Order Update Service

In a second terminal, once the Position Service is up:



&#x20;   mvn compile exec:java -Dexec.mainClass="com.indothai.orderupdate.OrderUpdateServiceMain" -Dexec.args="order\_updates.csv http://localhost:8080 50"



Or run OrderUpdateServiceMain.main() directly from an IDE.



Arguments (all optional, shown with their defaults):



&#x20;   \[csvFilePath]              default: order\_updates.csv

&#x20;   \[positionServiceBaseUrl]   default: http://localhost:8080

&#x20;   \[maxEventsPerSecond]       default: 50



Note: if running via IDE "Run As", the working directory is normally the

project root, so the default relative CSV path resolves correctly there.



\## Configuration Options



| Setting | Service | How to set | Default |

|---|---|---|---|

| Host to bind | Position Maintaining | 1st CLI arg | 0.0.0.0 |

| Port to bind | Position Maintaining | 2nd CLI arg | 8080 |

| CSV input file path | Order Update | 1st CLI arg | order\_updates.csv |

| Position Service base URL | Order Update | 2nd CLI arg | http://localhost:8080 |

| Max events per second (throttle) | Order Update | 3rd CLI arg | 50 |



\## Running the Tests



&#x20;   mvn test



Or in Eclipse: right-click src/test/java -> Run As -> JUnit Test.



Test classes:

\- PositionStoreTest: BUY/SELL position math, multiple symbols, negative

&#x20; and zero net positions, duplicate event\_id handling (including when

&#x20; other fields differ on the duplicate).

\- RowValidatorTest: invalid transaction types, zero/negative/non-integer/

&#x20; blank quantities, blank/null event IDs and symbols, symbol case

&#x20; preservation.

\- CsvReaderTest: streaming reads, continuing with later rows after a

&#x20; malformed row, blank-line handling.

\- PositionHttpServerTest: GET /position (empty and populated),

&#x20; POST /events happy path, duplicate event via HTTP, invalid

&#x20; transaction\_type via HTTP (400), malformed JSON body doesn't crash the

&#x20; server, wrong HTTP method (405).



\## Example API Usage



Request:



&#x20;   curl -X POST http://localhost:8080/events -H "Content-Type: application/json" -d "{\\"event\_id\\":\\"evt-0001\\",\\"symbol\\":\\"RELIANCE\\",\\"transaction\_type\\":\\"BUY\\",\\"quantity\\":90}"



Response:



&#x20;   {"status":"applied","event\_id":"evt-0001"}



Request:



&#x20;   curl http://localhost:8080/position



Response:



&#x20;   {"RELIANCE":90,"TCS":-75}



\## Known Limitations / Trade-offs



\- No durable/exactly-once delivery. As scoped, delivery is at-most-once

&#x20; per HTTP call, with no retry or dead-letter mechanism.

\- In-memory state only. Positions and dedup state are lost on Position

&#x20; Service restart; persistence is explicitly out of scope.

\- Minimal hand-rolled JSON. SimpleJson only supports flat JSON objects

&#x20; (no nesting/arrays), sufficient for this event schema, not general-purpose.

\- Simple CSV parsing. CsvReader splits on commas and does not handle

&#x20; quoted fields containing commas; not needed for the given dataset.

\- Throttle precision. The rate limiter is sleep-based and approximate

&#x20; (not a precise token bucket), per the assessment's own guidance that

&#x20; exact sub-millisecond timing is not expected.



\## AI-Assisted Tools Disclosure



Parts of this solution (design discussion, boilerplate, and test scaffolding)

were developed with the help of an AI assistant (Claude). All code and design

decisions were reviewed and can be explained/defended by the author.

