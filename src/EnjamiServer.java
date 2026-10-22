
package enjami;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import enjami.AuthService;
import enjami.DepartmentService;
import enjami.JsonUtil;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class EnjamiServer {

    private static final String SESSION_COOKIE = "ENJAMI_SESSION";
    private static final int PORT = 8080;

    public static void main(String[] args) throws IOException {
        Database.initialize();
        try {
    EnjamiSchema.initialize();
} catch (java.sql.SQLException e) {
    throw new RuntimeException(
        "Failed to initialize ENJAMI feature tables", e);
}

        HttpServer server = HttpServer.create(
                new InetSocketAddress("localhost", PORT), 0);

        server.createContext("/api/health", exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed.");
                return;
            }

            sendJson(exchange, 200,
                    "{\"status\":\"success\","
                    + "\"message\":\"ENJAMI backend is running\"}");
        });

        server.createContext("/api/register", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                JsonObject body = readJson(exchange);

                String username = getString(body, "username");
                String email = getString(body, "email");
                String password = getString(body, "password");

                AuthService.AuthResult result =
                        AuthService.register(username, email, password);

                if (!result.success()) {
                    sendError(exchange, 400, result.message());
                    return;
                }

                sendJson(exchange, 201,
                        "{\"success\":true,"
                        + "\"message\":\"Registration successful. "
                        + "You can now log in.\"}");

            } catch (BadRequestException e) {
                sendError(exchange, 400, e.getMessage());
            } catch (IOException e) {
                sendError(exchange, 413, "Request body is too large.");
            } catch (Exception e) {
                System.err.println("Registration request failed.");
                sendError(exchange, 500, "Internal server error.");
            }
        });

        server.createContext("/api/login", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed.");
                return;
            }

            try {
                JsonObject body = readJson(exchange);

                String email = getString(body, "email");
                String password = getString(body, "password");

                AuthService.AuthResult result =
                        AuthService.login(email, password);

                if (!result.success()) {
                    sendError(exchange, 401, result.message());
                    return;
                }

                // Browser stores this cookie; token remains server-managed.
                exchange.getResponseHeaders().add(
                        "Set-Cookie",
                        SESSION_COOKIE + "=" + result.token()
                        + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=1800");

                AuthService.Session session = result.session();

                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("message", "Login successful.");

                JsonObject user = new JsonObject();
                user.addProperty("id", session.userId());
                user.addProperty("username", session.username());
                user.addProperty("email", session.email());
                user.addProperty("role", session.role());

                response.add("user", user);

                sendJson(exchange, 200, response.toString());

            } catch (BadRequestException e) {
                sendError(exchange, 400, e.getMessage());
            } catch (IOException e) {
                sendError(exchange, 413, "Request body is too large.");
            } catch (Exception e) {
                System.err.println("Login request failed.");
                sendError(exchange, 500, "Internal server error.");
            }
        });

        server.createContext("/api/logout", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed.");
                return;
            }

            String token = getCookie(exchange, SESSION_COOKIE);
            AuthService.logout(token);

            exchange.getResponseHeaders().add(
                    "Set-Cookie",
                    SESSION_COOKIE
                    + "=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0");

            sendJson(exchange, 200,
                    "{\"success\":true,\"message\":\"Logged out.\"}");
        });

        server.createContext("/api/me", exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed.");
                return;
            }

            String token = getCookie(exchange, SESSION_COOKIE);
            AuthService.Session session = AuthService.getSession(token);

            if (session == null) {
                sendError(exchange, 401, "Please log in.");
                return;
            }

            JsonObject response = new JsonObject();
            response.addProperty("success", true);

            JsonObject user = new JsonObject();
            user.addProperty("id", session.userId());
            user.addProperty("username", session.username());
            user.addProperty("email", session.email());
            user.addProperty("role", session.role());

            response.add("user", user);
            sendJson(exchange, 200, response.toString());
        });
        // Temporary endpoints for testing role-based access.
        server.createContext("/api/rbac/student",
                exchange -> handleRoleTest(exchange, "STUDENT"));

        server.createContext("/api/rbac/cr",
                exchange -> handleRoleTest(exchange, "CR"));

        server.createContext("/api/rbac/teacher",
                exchange -> handleRoleTest(exchange, "TEACHER"));

        server.createContext("/api/rbac/hod",
                exchange -> handleRoleTest(exchange, "HOD"));
                // HOD-controlled user management
server.createContext("/api/admin/users", exchange -> {
    try {
        String path = exchange.getRequestURI().getPath();

        if ("/api/admin/users".equals(path)) {
            handleAdminUserList(exchange);
        } else if (path.matches(
                "/api/admin/users/[0-9]+/role")) {
            handleRoleAssignment(exchange);
        } else {
            sendError(exchange, 404, "Endpoint not found.");
        }
    } finally {
        exchange.close();
    }server.createContext("/api/hod/departments", exchange -> {
    try {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        AuthService.Session session = requireLogin(exchange);
        if (session == null) return;

        if ("GET".equals(method)
                && "/api/hod/departments".equals(path)) {
            handleDepartmentList(exchange, session);
        } else if ("POST".equals(method)
                && "/api/hod/departments".equals(path)) {
            handleDepartmentCreate(exchange, session);
        } else if ("PUT".equals(method)
                && path.matches("/api/hod/departments/[0-9]+")) {
            handleDepartmentRename(exchange, session, path);
        } else {
            sendError(exchange, 404, "Endpoint not found.");
        }
    } finally {
        exchange.close();
    }
});
});


        server.start();

        System.out.println("ENJAMI backend is running!");
        System.out.println("Health: http://localhost:" + PORT + "/api/health");
        System.out.println("Registration: POST /api/register");
        System.out.println("Login: POST /api/login");
        System.out.println("Logout: POST /api/logout");
        System.out.println("Current user: GET /api/me");
    }

    private static void handleRoleTest(
            HttpExchange exchange, String requiredRole)
            throws IOException {

        try {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed.");
                return;
            }

            String token = getCookie(exchange, SESSION_COOKIE);
            AuthService.Session session = AuthService.getSession(token);

            if (session == null) {
                sendError(exchange, 401, "Please log in.");
                return;
            }

            if (!requiredRole.equals(session.role())) {
                sendError(exchange, 403,
                        "You do not have permission to access this resource.");
                return;
            }

            JsonObject response = new JsonObject();
            response.addProperty("success", true);
            response.addProperty("message",
                    "Role authorization successful.");
            response.addProperty("requiredRole", requiredRole);
            response.addProperty("username", session.username());

            sendJson(exchange, 200, response.toString());

        } finally {
            exchange.close();
        }
    }

    private static JsonObject readJson(HttpExchange exchange)
            throws IOException, BadRequestException {

        String contentType =
                exchange.getRequestHeaders().getFirst("Content-Type");

        if (contentType == null
                || !contentType.toLowerCase().startsWith("application/json")) {
            throw new BadRequestException(
                    "Content-Type must be application/json.");
        }

        String body = JsonUtil.readRequestBody(exchange);

        try {
            if (body.isBlank()) {
                throw new BadRequestException("JSON body is required.");
            }

            JsonObject object = JsonParser.parseString(body).getAsJsonObject();

            return object;

        } catch (JsonParseException | IllegalStateException e) {
            throw new BadRequestException("Invalid JSON object.");
        }
    }

    private static String getString(JsonObject body, String key)
            throws BadRequestException {

        if (!body.has(key) || body.get(key).isJsonNull()
                || !body.get(key).isJsonPrimitive()
                || !body.getAsJsonPrimitive(key).isString()) {
            throw new BadRequestException(
                    "Missing or invalid field: " + key);
        }

        return body.get(key).getAsString();
    }

    private static String getCookie(
            HttpExchange exchange, String cookieName) {

        String cookieHeader =
                exchange.getRequestHeaders().getFirst("Cookie");

        if (cookieHeader == null) {
            return null;
        }

        for (String part : cookieHeader.split(";")) {
            String[] pair = part.trim().split("=", 2);

            if (pair.length == 2 && pair[0].equals(cookieName)) {
                return pair[1];
            }
        }

        return null;
    }

    private static void sendJson(
            HttpExchange exchange, int status, String json)
            throws IOException {

        byte[] data = json.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
                "Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");

        exchange.sendResponseHeaders(status, data.length);

        try (var output = exchange.getResponseBody()) {
            output.write(data);
        }
    }

    private static void sendError(
            HttpExchange exchange, int status, String message)
            throws IOException {

        sendJson(exchange, status, JsonUtil.error(message));
    }

    private static class BadRequestException extends Exception {
        BadRequestException(String message) {
            super(message);
        }
    }private static AuthService.Session requireLogin(
        HttpExchange exchange) throws IOException {

    String token = getCookie(exchange, SESSION_COOKIE);
    AuthService.Session session = AuthService.getSession(token);

    if (session == null) {
        sendError(exchange, 401, "Please log in.");
    }

    return session;
}

private static void handleAdminUserList(
        HttpExchange exchange) throws IOException {

    if (!"GET".equals(exchange.getRequestMethod())) {
        sendError(exchange, 405, "Method not allowed.");
        return;
    }

    AuthService.Session session = requireLogin(exchange);

    if (session == null) {
        return;
    }

    try {
        var users = RoleService.listUsers(session.userId());

        var response = new com.google.gson.JsonObject();
        response.addProperty("success", true);

        var array = new com.google.gson.JsonArray();

        for (var user : users) {
            var item = new com.google.gson.JsonObject();
            item.addProperty("id", user.id());
            item.addProperty("username", user.username());
            item.addProperty("email", user.email());
            item.addProperty("role", user.role());
            array.add(item);
        }

        response.add("users", array);
        sendJson(exchange, 200, response.toString());

    } catch (SecurityException e) {
        sendError(exchange, 403, "HOD permission required.");
    } catch (java.sql.SQLException e) {
        System.err.println("User listing database error.");
        sendError(exchange, 500, "Internal server error.");
    }
}
private static void handleRoleAssignment(
        HttpExchange exchange) throws IOException {

    if (!"PUT".equals(exchange.getRequestMethod())) {
        sendError(exchange, 405, "Method not allowed.");
        return;
    }

    AuthService.Session session = requireLogin(exchange);

    if (session == null) {
        return;
    }

    String path = exchange.getRequestURI().getPath();
    String[] parts = path.split("/");

    try {
        int targetId = Integer.parseInt(parts[4]);

        JsonObject body = readJson(exchange);
        String role = getString(body, "role");

        RoleService.Result result =
                RoleService.assignRole(
                        session.userId(), targetId, role);

        if (!result.success()) {
            int status = "User not found.".equals(result.message())
                    ? 404 : 400;

            sendError(exchange, status, result.message());
            return;
        }

        JsonObject response = new JsonObject();
        response.addProperty("success", true);
        response.addProperty("message", result.message());

        sendJson(exchange, 200, response.toString());

    } catch (SecurityException e) {
        sendError(exchange, 403, "HOD permission required.");
    } catch (BadRequestException e) {
        sendError(exchange, 400, e.getMessage());
    } catch (NumberFormatException e) {
        sendError(exchange, 400, "Invalid user ID.");
    } catch (java.sql.SQLException e) {
        System.err.println("Role assignment database error.");
        sendError(exchange, 500, "Internal server error.");
    }
}private static void handleDepartmentList(
        HttpExchange exchange, AuthService.Session session)
        throws IOException {

    try {
        var departments =
                DepartmentService.list(session.userId());

        JsonObject response = new JsonObject();
        response.addProperty("success", true);

        var array = new com.google.gson.JsonArray();

        for (var department : departments) {
            JsonObject item = new JsonObject();
            item.addProperty("id", department.id());
            item.addProperty("name", department.name());
            array.add(item);
        }

        response.add("departments", array);
        sendJson(exchange, 200, response.toString());

    } catch (SecurityException e) {
        sendError(exchange, 403, "HOD permission required.");
    } catch (java.sql.SQLException e) {
        sendError(exchange, 500, "Database error.");
    }
}

private static void handleDepartmentCreate(
        HttpExchange exchange, AuthService.Session session)
        throws IOException {

    if (!"POST".equals(exchange.getRequestMethod())) {
        sendError(exchange, 405, "Method not allowed.");
        return;
    }

    try {
        JsonObject body = readJson(exchange);
        String name = getString(body, "name");

        var department =
                DepartmentService.create(session.userId(), name);

        JsonObject response = new JsonObject();
        response.addProperty("success", true);
        response.addProperty("id", department.id());
        response.addProperty("name", department.name());

        sendJson(exchange, 201, response.toString());

    } catch (BadRequestException | IllegalArgumentException e) {
        sendError(exchange, 400, e.getMessage());
    } catch (SecurityException e) {
        sendError(exchange, 403, "HOD permission required.");
    } catch (java.sql.SQLException e) {
        if (e.getMessage() != null
                && e.getMessage().toLowerCase().contains("unique")) {
            sendError(exchange, 409, "Department already exists.");
        } else {
            sendError(exchange, 500, "Database error.");
        }
    }
}

private static void handleDepartmentRename(
        HttpExchange exchange, AuthService.Session session, String path)
        throws IOException {

    if (!"PUT".equals(exchange.getRequestMethod())) {
        sendError(exchange, 405, "Method not allowed.");
        return;
    }

    try {
        int id = Integer.parseInt(
                path.substring("/api/hod/departments/".length()));

        JsonObject body = readJson(exchange);
        String name = getString(body, "name");

        var department =
                DepartmentService.rename(session.userId(), id, name);

        JsonObject response = new JsonObject();
        response.addProperty("success", true);
        response.addProperty("id", department.id());
        response.addProperty("name", department.name());

        sendJson(exchange, 200, response.toString());

    } catch (BadRequestException | IllegalArgumentException e) {
        int status = "Department not found.".equals(e.getMessage())
                ? 404 : 400;
        sendError(exchange, status, e.getMessage());
    } catch (SecurityException e) {
        sendError(exchange, 403, "HOD permission required.");
    } catch (java.sql.SQLException e) {
        if (e.getMessage() != null
                && e.getMessage().toLowerCase().contains("unique")) {
            sendError(exchange, 409, "Department name already exists.");
        } else {
            sendError(exchange, 500, "Database error.");
        }
    }
}


}
