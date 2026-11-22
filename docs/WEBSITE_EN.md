# Catching breaking API responses with Contract Testing Toolkit

An API can still return `200 OK` after a provider changes a response in a way that breaks its consumer. A catalog client expecting a string identifier and a numeric price, for example, cannot safely use a response with a numeric identifier and no price. Version v0.1.0 makes this case reproducible with a small Java CLI and a synthetic local HTTP provider.

## Two checks for two sources of evidence

The offline check compares a consumer's expectations with a provider's declared guarantees. Both use a versioned TSV fixture for one HTTP interaction. The comparison reports method, path and status changes and checks the response fields that matter to the consumer. A provider can add fields freely, and an integer satisfies a general numeric requirement. An optional field is checked when present.

The observed-response check sends the consumer's `GET /items` request to a local provider and checks the actual status and JSON object. The demo walks through a compatible response, a breaking response, an additive change, and recovery. The breaking response changes `id` from a string to an integer and removes `price`. The additive response supplies optional `currency` and adds an unused `label`. Watching the same consumer contract accept the baseline and additive responses clarifies where compatibility comes from.

## Design and verification

The CLI separates contract parsing, comparison, HTTP transport, and response validation. It uses the JDK HTTP facilities and a pinned Jackson Core parser. HTTP verification is confined to an explicit IPv4 loopback endpoint with connection and read timeouts and a 64 KiB cap on the response body. Compatible checks return `0`, incompatible results return `1`, and invocation or transport failures return `2`, so automation can distinguish a contract regression from a broken test setup.

JUnit tests use synthetic fixtures and produce XML reports alongside the CLI checks. The local demo verifies one interaction at a time. It covers exact status and top-level JSON field types, with nested matching, authentication and cross-service orchestration outside its scope. Pact inspired the consumer-driven model. This toolkit uses its own TSV format and verification workflow. [Pact workflow](https://docs.pact.io/getting_started/how_pact_works)
