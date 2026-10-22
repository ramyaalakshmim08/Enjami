package enjami;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class DepartmentService {

    private DepartmentService() {}

    public record Department(int id, String name) {}

    private static void requireHod(Connection conn, int actorId)
            throws SQLException {

        String sql = "SELECT role FROM users WHERE id = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, actorId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next() || !"HOD".equals(rs.getString("role"))) {
                    throw new SecurityException("HOD permission required.");
                }
            }
        }
    }

    public static List<Department> list(int actorId)
            throws SQLException {

        try (Connection conn = Database.connect()) {
            requireHod(conn, actorId);

            List<Department> departments = new ArrayList<>();

            String sql = "SELECT id, name FROM departments ORDER BY name";

            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    departments.add(new Department(
                            rs.getInt("id"),
                            rs.getString("name")));
                }
            }

            return departments;
        }
    }

    public static Department create(int actorId, String name)
            throws SQLException {

        String cleanName = validateName(name);

        try (Connection conn = Database.connect()) {
            conn.setAutoCommit(false);

            try {
                requireHod(conn, actorId);

                int id;

                String sql = "INSERT INTO departments (name) VALUES (?)";

                try (PreparedStatement stmt = conn.prepareStatement(
                        sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {

                    stmt.setString(1, cleanName);
                    stmt.executeUpdate();

                    try (ResultSet keys = stmt.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException(
                                    "Could not retrieve department ID.");
                        }
                        id = keys.getInt(1);
                    }
                }

                logAction(conn, actorId, "DEPARTMENT_CREATED",
                        "Department ID " + id + ": " + cleanName);

                conn.commit();
                return new Department(id, cleanName);

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

    public static Department rename(
            int actorId, int departmentId, String name)
            throws SQLException {

        String cleanName = validateName(name);

        if (departmentId <= 0) {
            throw new IllegalArgumentException(
                    "Department ID must be positive.");
        }

        try (Connection conn = Database.connect()) {
            conn.setAutoCommit(false);

            try {
                requireHod(conn, actorId);

                String oldName;

                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT name FROM departments WHERE id = ?")) {

                    stmt.setInt(1, departmentId);

                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalArgumentException(
                                    "Department not found.");
                        }
                        oldName = rs.getString("name");
                    }
                }

                try (PreparedStatement stmt = conn.prepareStatement(
                        "UPDATE departments SET name = ? WHERE id = ?")) {

                    stmt.setString(1, cleanName);
                    stmt.setInt(2, departmentId);
                    stmt.executeUpdate();
                }

                logAction(conn, actorId, "DEPARTMENT_RENAMED",
                        "Department ID " + departmentId + ": "
                                + oldName + " -> " + cleanName);

                conn.commit();
                return new Department(departmentId, cleanName);

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

    private static String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Department name is required.");
        }

        String cleanName = name.trim();

        if (cleanName.length() > 100) {
            throw new IllegalArgumentException(
                    "Department name must be at most 100 characters.");
        }

        return cleanName;
    }

    private static void logAction(
            Connection conn, int actorId, String action, String details)
            throws SQLException {

        String sql = """
                INSERT INTO audit_logs (actor_id, action, details)
                VALUES (?, ?, ?)
                """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, actorId);
            stmt.setString(2, action);
            stmt.setString(3, details);
            stmt.executeUpdate();
        }
    }
}
