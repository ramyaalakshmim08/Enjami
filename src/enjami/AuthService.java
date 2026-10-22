
package enjami;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long SESSION_LIFETIME_SECONDS = 30 * 60;

    // Server-side session store. Sessions expire after 30 minutes.
    private static final Map<String, Session> SESSIONS =
            new ConcurrentHashMap<>();

    private AuthService() {
    }

    public record Session(
            int userId,
            String username,
            String email,
            String role,
            long expiresAt) {
    }

    public record AuthResult(
            boolean success,
            String message,
            String token,
            Session session) {
    }

    public static AuthResult register(
            String username, String email, String password) {

        if (username == null || username.isBlank()
                || username.length() > 80) {
            return failure("Enter a valid username.");
        }

        if (email == null || email.length() > 254
                || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            return failure("Enter a valid email address.");
        }

        if (password == null || password.length() < 12
                || password.length() > 128) {
            return failure("Password must contain 12 to 128 characters.");
        }

        try {
            PasswordUtil.PasswordHash passwordHash =
                    PasswordUtil.hashPassword(password);

            String sql = """
                INSERT INTO users
                    (username, email, password_hash, password_salt, role)
                VALUES (?, ?, ?, ?, 'STUDENT')
                """;

            try (Connection conn = Database.connect();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, username.trim());
                stmt.setString(2, email.trim().toLowerCase());
                stmt.setString(3, passwordHash.hash());
                stmt.setString(4, passwordHash.salt());

                stmt.executeUpdate();
                return new AuthResult(
                        true, "Registration successful.", null, null);
            }

        } catch (SQLException e) {
            // Do not expose SQL errors to the client.
            if (e.getMessage() != null
                    && e.getMessage().toLowerCase().contains("unique")) {
                return failure("An account with that email already exists.");
            }

            System.err.println("Registration database error.");
            return failure("Registration could not be completed.");

        } catch (GeneralSecurityException e) {
            System.err.println("Password hashing failed.");
            return failure("Registration could not be completed.");
        }
    }

    public static AuthResult login(String email, String password) {
        if (email == null || password == null
                || email.length() > 254 || password.length() > 128) {
            return failure("Invalid email or password.");
        }

        String sql = """
            SELECT id, username, email, password_hash, password_salt, role
            FROM users
            WHERE email = ?
            """;

        try (Connection conn = Database.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, email.trim().toLowerCase());

            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return failure("Invalid email or password.");
                }

                String storedHash = rs.getString("password_hash");
                String storedSalt = rs.getString("password_salt");

                boolean valid = PasswordUtil.verifyPassword(
                        password, storedSalt, storedHash);

                if (!valid) {
                    return failure("Invalid email or password.");
                }

                int userId = rs.getInt("id");
                String username = rs.getString("username");
                String userEmail = rs.getString("email");
                String role = rs.getString("role");

                String token = newSessionToken();
                long expiry = Instant.now().getEpochSecond()
                        + SESSION_LIFETIME_SECONDS;

                Session session = new Session(
                        userId, username, userEmail, role, expiry);

                SESSIONS.put(token, session);

                return new AuthResult(
                        true, "Login successful.", token, session);
            }

        } catch (SQLException e) {
            System.err.println("Login database error.");
            return failure("Login could not be completed.");

        } catch (GeneralSecurityException e) {
            System.err.println("Password verification failed.");
            return failure("Login could not be completed.");
        }
    }

    public static Session getSession(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        Session session = SESSIONS.get(token);

        if (session == null) {
            return null;
        }

        if (Instant.now().getEpochSecond() >= session.expiresAt()) {
            SESSIONS.remove(token, session);
            return null;
        }

        return session;
    }

    public static void logout(String token) {
        if (token != null) {
            SESSIONS.remove(token);
        }
    }

    private static String newSessionToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static AuthResult failure(String message) {
        return new AuthResult(false, message, null, null);
    }public static void logoutAllForUser(int userId) {
    SESSIONS.entrySet().removeIf(
            entry -> entry.getValue().userId() == userId);
}

    }

