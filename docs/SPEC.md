# Contract verification v0.1

The toolkit verifies one consumer interaction at a time. The offline mode compares a consumer contract with a proposed provider contract. The HTTP mode issues a GET to a local provider and checks its observed response.

## Contract format

Use UTF-8 text and tab-separated columns. The version and interaction rows are required. Field names must be unique across `field` and `optional` rows.

| Row | Columns |
| --- | --- |
| Version | `version`, `1` |
| Interaction | `interaction`, uppercase ASCII method, slash-prefixed path with no whitespace, three ASCII digits for status from `100` to `599` |
| Required field | `field`, name, lowercase type |
| Optional field | `optional`, name, lowercase type |

Field names begin with an ASCII letter or underscore and continue with ASCII letters, digits, or underscores. Available types are `string`, `integer`, `number`, `boolean`, `null`, `object`, and `array`. `object` and `array` constrain only the type of the field. An integer satisfies a `number` expectation. Other types match exactly. Optional fields may be absent. A present `null` value matches only an explicit `null` requirement.

## Offline declaration comparison

```text
toolkit.Main <consumer.tsv> <provider.tsv>
```

The provider declaration must match the consumer's method, path and status exactly. Each required consumer field must be guaranteed by a provider `field` row with a compatible type. A provider `optional` row does not guarantee that a required value will appear. The provider can omit a consumer's optional field. If the provider declares it as required or optional, its type must match the consumer's expectation. Additional provider fields are ignored. Findings are ordered by method, path, status and field name.

## Observed response

```text
toolkit.Main --http <consumer.tsv> <http://127.0.0.1:port>
```

The consumer contract must declare `GET` and a URI path without a query, fragment or leading `//`. Supply an HTTP origin at `127.0.0.1` with an explicit port from `1` to `65535`. The origin may have a trailing slash. User info, query, fragment and other paths are invalid. The verifier sends the GET with `Accept: application/json` and `Accept-Encoding: identity`. It disables redirects and bypasses proxies. Connection and read timeouts are 3 seconds each. The response body limit is 65,536 bytes.

The JDK transport can retry a GET if the connection closes before a response arrives.

The server must return the declared status and a single UTF-8 JSON object with unique keys. The `Content-Type` must be `application/json`, with `charset=UTF-8` allowed. `Content-Encoding` must be absent or `identity`. Observed integers have no fractional or exponent part. Required fields must be present and compatible, and optional fields are checked when present. Extra properties are ignored for type matching. JSON syntax is checked across the body. The verifier checks status before inspecting media type, encoding and JSON. Field findings are ordered by field name. [JSON syntax reference](https://www.rfc-editor.org/rfc/rfc8259.html)

## Exit codes

| Exit | Result |
| --- | --- |
| `0` | The declaration or observed response is compatible. |
| `1` | The result is incompatible. Findings go to stdout. Unexpected status, invalid media type or encoding, an oversized body, invalid JSON and field mismatches are incompatibilities. |
| `2` | The invocation, file, contract, endpoint or transport failed. Diagnostics go to stderr. |

## Synthetic demonstration

```text
toolkit.Main --demo <consumer.tsv>
toolkit.SampleProvider baseline|breaking|additive
```

The demo checks four successive states with the supplied consumer file. With `fixtures/consumer-http.tsv`, the baseline passes, the breaking state fails, the additive state passes and the recovered baseline passes. The independent sample provider starts the chosen state on an operating-system-assigned loopback port and prints its origin. It serves `GET /items`. Use the verification script to build the CLI and run the tests. [Quick start](../README.md)
