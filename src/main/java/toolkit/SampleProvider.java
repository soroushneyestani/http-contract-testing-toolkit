package toolkit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** A synthetic HTTP provider for local contract verification. */
public final class SampleProvider implements AutoCloseable {
    public enum State {
        BASELINE, BREAKING, ADDITIVE
    }

    private final HttpServer server;

    private SampleProvider(HttpServer server) {
        this.server = server;
    }

    /** Starts a provider on an available IPv4 loopback port. */
    public static SampleProvider start(State state) throws IOException {
        String body = switch (Objects.requireNonNull(state, "state")) {
            case BASELINE -> "{\"id\":\"sku-42\",\"price\":12}";
            case BREAKING -> "{\"id\":42}";
            case ADDITIVE -> "{\"id\":\"sku-42\",\"price\":12,\"currency\":\"EUR\",\"label\":\"Desk lamp\"}";
        };
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        InetAddress loopback = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
        HttpServer server = HttpServer.create(new InetSocketAddress(loopback, 0), 0);
        server.createContext("/", exchange -> respond(exchange, payload));
        server.start();
        return new SampleProvider(server);
    }

    /** Returns the provider's HTTP origin, without a path. */
    public URI origin() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @Override
    public void close() {
        server.stop(1);
    }

    public static void main(String[] args) throws IOException {
        State state = args.length == 1 ? switch (args[0]) {
            case "baseline" -> State.BASELINE;
            case "breaking" -> State.BREAKING;
            case "additive" -> State.ADDITIVE;
            default -> null;
        } : null;
        if (state == null) {
            System.err.println("Usage: java toolkit.SampleProvider <baseline|breaking|additive>");
            System.exit(2);
            return;
        }
        SampleProvider provider = start(state);
        Runtime.getRuntime().addShutdownHook(new Thread(provider::close, "sample-provider-shutdown"));
        System.out.println(provider.origin());
    }

    private static void respond(HttpExchange exchange, byte[] payload) throws IOException {
        try (exchange) {
            URI uri = exchange.getRequestURI();
            if (!"/items".equals(uri.getRawPath()) || uri.getRawQuery() != null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET");
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
        }
    }
}
