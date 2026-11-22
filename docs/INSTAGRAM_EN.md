# Instagram caption

`200 OK` can still break an API consumer.

I built Contract Testing Toolkit v0.1.0 as a small Java toolkit to show how. It compares declared provider guarantees and checks the JSON actually returned by a synthetic local HTTP provider.

The demo checks the same consumer contract against a baseline, a breaking change, a compatible addition, and a restored baseline. Changing the ID type and dropping a required price fails the check. Adding a field the consumer can ignore passes.

JUnit XML and distinct CLI exit codes make the result usable in automation.

#Java #ContractTesting #SoftwareTesting #BackendEngineering
