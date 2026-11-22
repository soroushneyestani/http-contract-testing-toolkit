# Use offline response-schema fixtures

Status: accepted

Consumer-driven contract tools such as Pact verify expectations against provider behavior. The first toolkit milestone models compatibility with two small versioned files, one containing consumer requirements and one describing provider guarantees. This makes required-field, status and type changes reproducible with synthetic fixtures and runner-independent tests.

An HTTP server and HTTP replay would verify response behavior. An OpenAPI compatibility engine could support more routes, content types and constraints. Both need a wider protocol and schema design. The single-interaction TSV contract needs no runtime dependency and can detect the schema changes defined by v0.1. The contract describes top-level fields and leaves response-body and live-provider validation for later work.
