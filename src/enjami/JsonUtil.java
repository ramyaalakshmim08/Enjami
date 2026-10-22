package enjami;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class JsonUtil {

    private static final int MAX_REQUEST_SIZE = 1_048_576; // 1 MB

    private JsonUtil() {
        // Utility class: do not create objects of this class.
    }

    // Escape special characters so a Java string can be placed safely in JSON.
    public static String escape(String value) {
        if (value == null) {
            return "";
        }

        StringBuilder result = new StringBuilder();

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);

            switch (c) {
                case '"'  -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (c < 0x20) {
                        result.append(String.format("\\u%04x", (int) c));
                    } else {
                        result.append(c);
                    }
                }
            }
        }

        return result.toString();
    }

    // Send a JSON response with the requested HTTP status code.
    public static void sendJson(
            HttpExchange exchange, int statusCode, String json)
            throws IOException {

        byte[] response = json.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
                "Content-Type", "application/json; charset=UTF-8");

        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");

        exchange.sendResponseHeaders(statusCode, response.length);

        try (var output = exchange.getResponseBody()) {
            output.write(response);
        }
    }

    // Read a request body, rejecting bodies larger than 1 MB.
    public static String readRequestBody(HttpExchange exchange)
            throws IOException {

        try (InputStream input = exchange.getRequestBody()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            StringBuilder body = new StringBuilder();

            while ((count = input.read(buffer)) != -1) {
                total += count;

                if (total > MAX_REQUEST_SIZE) {
                    throw new IOException("Request body is too large.");
                }

                body.append(new String(
                        buffer, 0, count, StandardCharsets.UTF_8));
            }

            return body.toString();
        }
    }

    // Create a simple JSON success response.
    public static String success(String message) {
        return "{\"success\":true,\"message\":\""
                + escape(message) + "\"}";
    }

    // Create a simple JSON error response.
    public static String error(String message) {
        return "{\"success\":false,\"error\":\""
                + escape(message) + "\"}";
    }
}