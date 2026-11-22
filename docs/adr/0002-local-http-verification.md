# Verify observed responses over loopback HTTP

Status **Accepted**

## Context

Comparing two declarations can show whether a proposed provider promises the fields a consumer needs. The comparison gives no evidence that a running provider returns those fields. A second verification path needs an actual HTTP exchange against a synthetic provider that can serve compatible and breaking responses on demand. [Offline fixture decision](0001-offline-schema-fixtures.md)

Pact-JVM `4.6.15` is an implementation of the Pact V4 specification. A Pact workflow generates pact files from consumer tests and replays their interactions during provider verification. Adopting it would introduce Pact's interaction and matching model alongside the existing single-interaction TSV contract. [How Pact works](https://docs.pact.io/getting_started/how_pact_works) · [Pact-JVM versions](https://docs.pact.io/implementation_guides/jvm/readme)

## Decision

Keep the original TSV format and declaration comparison. Extend the contract with optional response fields. Add one HTTP verification mode that sends the declared GET to an explicit IPv4 loopback port and compares the observed status and JSON object with the consumer's expected fields. The example provider uses loopback sockets and synthetic state so the demonstration can run without another service.

Use `HttpURLConnection` for GET and `HttpServer` for the synthetic provider, both from the JDK. Use a pinned Jackson Core streaming parser for JSON syntax and types. Cap the response body, set connection and read timeouts and disable redirects and proxy use. [Java 17 HTTP connections](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/HttpURLConnection.html) · [Java 17 HTTP server](https://docs.oracle.com/en/java/javase/17/docs/api/jdk.httpserver/com/sun/net/httpserver/HttpServer.html) · [Jackson Core](https://github.com/FasterXML/jackson-core)

## Consequences

The offline mode establishes what the provider promises. The HTTP mode shows what a particular local request returned. The demo can show a breaking implementation even when the declared contract stayed compatible. Consumers get coverage for top-level presence, types and exact status. Matching nested shapes, request bodies, authentication, OpenAPI and Pact file compatibility, and Pact Broker workflows are outside this design. [Verification contract](../SPEC.md)
