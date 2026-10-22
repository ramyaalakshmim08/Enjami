package enjami;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public final class RoleService {

    private static final List<String> ALLOWED_ROLES =
            List.of("STUDENT", "CR", "TEACHER");

    private RoleService() {}

    public record User(
            int id,
            String username,
            String email,
            String role) {}

    public record Result(
            boolean success,
            String message) {}

    private static void requireHod(
            Connection conn, int actorId) throws SQLException {

        String sql = "SELECT role FROM users WHERE id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, actorId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()
                        || !"HOD".equals(rs.getString("role"))) {
                    throw new SecurityException(
                            "Only the HOD can manage user roles.");
                }
            }
        }
    }

    public static List<User> listUsers(int actorId)
            throws SQLException {

        try (Connection conn = Database.connect()) {
            requireHod(conn, actorId);

            String sql =
                    "SELECT id, username, email, role "
                    + "FROM users ORDER BY id";

            try (PreparedStatement stmt =
                         conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                List<User> users = new java.util.ArrayList<>();

                while (rs.next()) {
                    users.add(new User(
                            rs.getInt("id"),
                            rs.getString("username"),
                            rs.getString("email"),
                            rs.getString("role")));
                }

                return users;
            }
        }
    }

    public static Result assignRole(
            int actorId, int targetId, String requestedRole)
            throws SQLException {

        if (requestedRole == null) {
            return new Result(false, "Role is required.");
        }

        String role = requestedRole.trim().toUpperCase();

        if (!ALLOWED_ROLES.contains(role)) {
            return new Result(
                    false,
                    "Allowed roles: STUDENT, CR, TEACHER.");
        }

        try (Connection conn = Database.connect()) {
            conn.setAutoCommit(false);

            try {
                requireHod(conn, actorId);

                String oldRole;

                try (PreparedStatement stmt =
                        conn.prepareStatement(
                                "SELECT role FROM users WHERE id = ?")) {

                    stmt.setInt(1, targetId);

                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return new Result(
                                    false, "User not found.");
                        }

                        oldRole = rs.getString("role");
                    }
                }

                // HOD accounts cannot be changed through this endpoint.
                if ("HOD".equals(oldRole)) {
                    conn.rollback();
                    return new Result(
                            false,
                            "HOD accounts cannot be changed here.");
                }

                if (oldRole.equals(role)) {
                    conn.rollback();
                    return new Result(
                            true, "User already has this role.");
                }

                try (PreparedStatement stmt =
                        conn.prepareStatement(
                                "UPDATE users SET role = ? WHERE id = ?")) {

                    stmt.setString(1, role);
                    stmt.setInt(2, targetId);

                    if (stmt.executeUpdate() != 1) {
                        throw new SQLException(
                                "Role update failed.");
                    }
                }

                String details =
                        "User ID " + targetId
                        + ": " + oldRole + " -> " + role;

                try (PreparedStatement stmt =
                        conn.prepareStatement("""
                            INSERT INTO audit_logs
                                (actor_id, action, details)
                            VALUES (?, ?, ?)
                            """)) {

                    stmt.setInt(1, actorId);
                    stmt.setString(2, "ROLE_CHANGED");
                    stmt.setString(3, details);
                    stmt.executeUpdate();
                }

                conn.commit();

                // Invalidate sessions so old roles cannot persist.
                AuthService.logoutAllForUser(targetId);

                return new Result(
                        true, "Role updated successfully.");

            } catch (SQLException | RuntimeException e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }

                throw e;
            }
        }
    }
}
