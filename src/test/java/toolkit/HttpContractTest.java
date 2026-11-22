package toolkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import toolkit.Contract.FieldType;
import toolkit.SampleProvider.State;

class HttpContractTest {
    private static final Contract CONSUMER = new Contract("GET", "/items", 200,
            Map.of("id", FieldType.STRING, "price", FieldType.NUMBER), Map.of("currency", FieldType.STRING));
    private static final String BASELINE = "{\"id\":\"sku-42\",\"price\":12}";
    private static final HttpClient DIRECT_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .proxy(ProxySelector.of(null))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Test
    void detectsBreakingResponseAndAcceptsAdditiveChangeAndRestoredBaseline() throws IOException {
        assertEquals(List.of(), verifySample(State.BASELINE));

        List<String> differences = verifySample(State.BREAKING);
        assertEquals(2, differences.size(), differences.toString());
        assertMentions(differences.get(0), "id", "string", "integer");
        assertMentions(differences.get(1), "price", "missing");

        assertEquals(List.of(), verifySample(State.ADDITIVE));
        assertEquals(List.of(), verifySample(State.BASELINE));
    }

    @Test
    void sampleProviderRequiresAnExactGetWithoutQuery() throws IOException, InterruptedException {
        try (SampleProvider provider = SampleProvider.start(State.BASELINE)) {
            URI origin = provider.origin();
            assertEquals("127.0.0.1", origin.getHost());
            assertTrue(origin.getPort() > 0);

            HttpResponse<String> matched = request(origin.resolve("/items"), "GET");
            assertEquals(200, matched.statusCode());
            assertEquals("application/json", matched.headers().firstValue("Content-Type").orElseThrow());
            assertEquals(BASELINE, matched.body());

            HttpResponse<String> wrongMethod = request(origin.resolve("/items"), "POST");
            assertEquals(405, wrongMethod.statusCode());
            assertEquals("GET", wrongMethod.headers().firstValue("Allow").orElseThrow());
            assertEquals(404, request(origin.resolve("/items/extra"), "GET").statusCode());
            assertEquals(404, request(origin.resolve("/items?debug=1"), "GET").statusCode());
            assertEquals(404, request(origin.resolve("/%69tems"), "GET").statusCode());
        }
        assertThrows(NullPointerException.class, () -> SampleProvider.start(null));
    }

    @Test
    void verifierUsesTheContractPathAndRejectsUnsupportedRequests() throws IOException {
        try (SampleProvider provider = SampleProvider.start(State.BASELINE)) {
            Contract wrongMethod = new Contract("POST", "/items", 200, Map.of());
            Contract wrongPath = new Contract("GET", "/other", 200, Map.of());
            assertStatusDifference(wrongPath, provider.origin(), 404);
            assertThrows(IllegalArgumentException.class, () -> HttpVerification.verify(wrongMethod, provider.origin()));
            for (String path : List.of("/items?test=1", "/items#fragment", "//" + provider.origin().getAuthority())) {
                Contract invalidPath = new Contract("GET", path, 200, Map.of());
                assertThrows(IllegalArgumentException.class,
                        () -> HttpVerification.verify(invalidPath, provider.origin()), path);
            }
        }
    }

    @Test
    void acceptsJsonTypesAndAdditionalValuesAndDistinguishesNullFromMissing() throws IOException {
        Contract consumer = new Contract("GET", "/items", 200, Map.of(
                "id", FieldType.STRING, "price", FieldType.NUMBER, "ratio", FieldType.NUMBER,
                "enabled", FieldType.BOOLEAN, "note", FieldType.NULL, "metadata", FieldType.OBJECT,
                "labels", FieldType.ARRAY));
        String body = "{\"id\":\"caf\u00e9\",\"price\":12,\"ratio\":12.5,\"enabled\":true,\"note\":null,"
                + "\"metadata\":{\"kind\":\"desk\"},\"labels\":[\"synthetic\"],\"other\":0}";
        try (Fixture fixture = Fixture.start(exchange -> respond(exchange, 200, "application/json", body))) {
            assertEquals(List.of(), HttpVerification.verify(consumer, fixture.origin()));

            Contract stricter = new Contract("GET", "/items", 200, Map.of(
                    "note", FieldType.STRING, "ratio", FieldType.INTEGER, "absent", FieldType.NULL),
                    Map.of("labels", FieldType.STRING));
            List<String> differences = HttpVerification.verify(stricter, fixture.origin());
            assertEquals(4, differences.size(), differences.toString());
            assertMentions(differences.get(0), "absent", "missing");
            assertMentions(differences.get(1), "labels", "string", "array");
            assertMentions(differences.get(2), "note", "string", "null");
            assertMentions(differences.get(3), "ratio", "integer", "number");
        }
    }

    @Test
    void rejectsAnUnexpectedStatusWithAnOtherwiseCompatibleBody() throws IOException {
        try (Fixture fixture = Fixture.start(exchange -> respond(exchange, 201, "application/json", BASELINE))) {
            assertStatusDifference(CONSUMER, fixture.origin(), 201);
        }
    }

    @Test
    void rejectsMalformedJsonNonJsonMediaTypeAndOversizedBody() throws IOException {
        String oversizedBody = "{\"id\":\"" + "x".repeat(65_536) + "\",\"price\":12}";
        try (Fixture badJson = Fixture.start(exchange -> respond(exchange, 200, "application/json", "{\"id\":"));
             Fixture wrongType = Fixture.start(exchange -> respond(exchange, 200, "text/plain", BASELINE));
             Fixture malformedType = Fixture.start(exchange -> respond(exchange, 200, ";", BASELINE));
             Fixture oversized = Fixture.start(exchange -> respond(exchange, 200, "application/json", oversizedBody))) {
            assertSingleFinding(badJson.origin(), "json");
            assertSingleFinding(wrongType.origin(), "content-type");
            assertSingleFinding(malformedType.origin(), "content-type");
            assertSingleFinding(oversized.origin(), "65536");
        }
    }

    @Test
    void distinguishesAConnectionFailureFromAContractFinding() throws IOException {
        try (Fixture fixture = Fixture.start(HttpExchange::close)) {
            assertThrows(IOException.class, () -> HttpVerification.verify(CONSUMER, fixture.origin()));
        }
    }

    @Test
    void reportsRedirectWithoutReplayingTheRequest() throws IOException {
        AtomicInteger targetCalls = new AtomicInteger();
        try (Fixture fixture = Fixture.start(exchange -> {
            if ("/items".equals(exchange.getRequestURI().getPath())) {
                exchange.getResponseHeaders().set("Location", "/redirect-target");
                respond(exchange, 302, "application/json", BASELINE);
            } else {
                targetCalls.incrementAndGet();
                respond(exchange, 200, "application/json", BASELINE);
            }
        })) {
            assertStatusDifference(CONSUMER, fixture.origin(), 302);
            assertEquals(0, targetCalls.get());
        }
    }

    private static List<String> verifySample(State state) throws IOException {
        try (SampleProvider provider = SampleProvider.start(state)) {
            return HttpVerification.verify(CONSUMER, provider.origin());
        }
    }

    private static HttpResponse<String> request(URI uri, String method) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(3))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();
        return DIRECT_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static void assertStatusDifference(Contract contract, URI origin, int actual) throws IOException {
        List<String> differences = HttpVerification.verify(contract, origin);
        assertEquals(1, differences.size(), differences.toString());
        assertMentions(differences.get(0), "status", Integer.toString(contract.status()), Integer.toString(actual));
    }

    private static void assertSingleFinding(URI origin, String fact) throws IOException {
        List<String> differences = HttpVerification.verify(CONSUMER, origin);
        assertEquals(1, differences.size(), differences.toString());
        assertMentions(differences.get(0), fact);
    }

    private static void assertMentions(String diagnostic, String... facts) {
        String normalized = diagnostic.toLowerCase(Locale.ROOT);
        for (String fact : facts) {
            assertTrue(normalized.contains(fact), "Expected '" + fact + "' in: " + diagnostic);
        }
    }

    private static void respond(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        try (exchange) {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }

    private record Fixture(HttpServer server) implements AutoCloseable {
        static Fixture start(HttpHandler handler) throws IOException {
            InetAddress loopback = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
            HttpServer server = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
            server.createContext("/", handler);
            server.start();
            return new Fixture(server);
        }

        URI origin() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
