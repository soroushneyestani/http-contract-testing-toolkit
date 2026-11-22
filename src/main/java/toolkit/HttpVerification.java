package toolkit;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadFeature;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import toolkit.Contract.FieldType;

/** Observes one GET interaction against a local test provider. */
public final class HttpVerification {
    private static final int TIMEOUT_MS = 3_000;
    private static final int BODY_LIMIT = 65_536;
    private static final JsonFactory JSON = JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    private HttpVerification() {}

    /** Returns compatibility findings. Invalid input and transport failures are errors. */
    public static List<String> verify(Contract consumer, URI origin) throws IOException {
        URI request = requestUri(consumer, origin);
        HttpURLConnection connection = (HttpURLConnection) request.toURL().openConnection(Proxy.NO_PROXY);
        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Accept-Encoding", "identity");
            int status = connection.getResponseCode();
            if (status < 100 || status > 599) {
                throw new IOException("provider did not return a valid HTTP status");
            }
            if (status != consumer.status()) {
                return List.of("status mismatch: expected " + consumer.status() + ", got " + status);
            }
            if (!isJsonUtf8(connection.getContentType())) {
                return List.of("response Content-Type must be application/json with UTF-8");
            }
            String encoding = connection.getContentEncoding();
            if (encoding != null && !encoding.equalsIgnoreCase("identity")) {
                return List.of("response Content-Encoding must be identity");
            }

            // HTTP error statuses can themselves be the expected contract.
            try (var payload = status >= 400 ? connection.getErrorStream() : connection.getInputStream()) {
                if (payload == null) {
                    return List.of("response body must be a valid UTF-8 JSON object");
                }
                byte[] body = payload.readNBytes(BODY_LIMIT + 1);
                if (body.length > BODY_LIMIT) {
                    return List.of("response body exceeds " + BODY_LIMIT + " bytes");
                }
                try {
                    return Compatibility.checkResponse(consumer, status, jsonFieldTypes(body));
                } catch (JacksonException | CharacterCodingException | IllegalArgumentException invalidJson) {
                    return List.of("response body must be a valid UTF-8 JSON object with unique keys");
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    private static URI requestUri(Contract consumer, URI origin) {
        if (origin == null || !"http".equalsIgnoreCase(origin.getScheme())
                || !"127.0.0.1".equals(origin.getHost()) || origin.getPort() < 1 || origin.getPort() > 65535
                || origin.getRawUserInfo() != null || origin.getRawQuery() != null || origin.getRawFragment() != null
                || !(origin.getRawPath().isEmpty() || origin.getRawPath().equals("/"))) {
            throw new IllegalArgumentException("HTTP origin must be http://127.0.0.1:<port>");
        }
        if (!"GET".equals(consumer.method())) {
            throw new IllegalArgumentException("HTTP verification supports GET interactions only");
        }
        URI path = URI.create(consumer.path());
        if (path.isAbsolute() || path.getRawAuthority() != null || path.getRawQuery() != null
                || path.getRawFragment() != null || consumer.path().startsWith("//")) {
            throw new IllegalArgumentException("HTTP interaction must specify an absolute path without a query or fragment");
        }
        // Use a fixed origin rather than resolving a possibly authority-bearing input path.
        return URI.create("http://127.0.0.1:" + origin.getPort() + path.toASCIIString());
    }

    private static boolean isJsonUtf8(String header) {
        if (header == null) {
            return false;
        }
        String[] parts = header.split(";", -1);
        if (!parts[0].strip().equalsIgnoreCase("application/json")) {
            return false;
        }
        for (int index = 1; index < parts.length; index++) {
            String[] parameter = parts[index].split("=", 2);
            if (parameter[0].strip().equalsIgnoreCase("charset")) {
                String value = parameter.length == 2 ? parameter[1].strip() : "";
                if (!value.equalsIgnoreCase("utf-8") && !value.equalsIgnoreCase("\"utf-8\"")) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Map<String, FieldType> jsonFieldTypes(byte[] raw) throws IOException {
        String body = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(raw)).toString();
        Map<String, FieldType> fields = new HashMap<>();
        try (JsonParser parser = JSON.createParser(body)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new IllegalArgumentException("Expected a JSON object");
            }
            JsonToken field;
            while ((field = parser.nextToken()) != JsonToken.END_OBJECT) {
                if (field != JsonToken.FIELD_NAME) {
                    throw new IllegalArgumentException("Expected a JSON field");
                }
                String name = parser.currentName();
                fields.put(name, fieldType(parser.nextToken()));
                parser.skipChildren();
            }
            if (parser.nextToken() != null) {
                throw new IllegalArgumentException("Unexpected content after JSON object");
            }
        }
        return fields;
    }

    private static FieldType fieldType(JsonToken token) {
        if (token == null) {
            throw new IllegalArgumentException("Missing JSON value");
        }
        return switch (token) {
            case VALUE_STRING -> FieldType.STRING;
            case VALUE_NUMBER_INT -> FieldType.INTEGER;
            case VALUE_NUMBER_FLOAT -> FieldType.NUMBER;
            case VALUE_TRUE, VALUE_FALSE -> FieldType.BOOLEAN;
            case VALUE_NULL -> FieldType.NULL;
            case START_OBJECT -> FieldType.OBJECT;
            case START_ARRAY -> FieldType.ARRAY;
            default -> throw new IllegalArgumentException("Invalid JSON value");
        };
    }
}
