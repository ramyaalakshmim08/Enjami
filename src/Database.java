
package enjami;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Database {

    private static final String DB_URL = "jdbc:sqlite:database/enjami.db";

    public static Connection connect() throws SQLException {
        try {
            Path folder = Paths.get("database");
            Files.createDirectories(folder);
        } catch (Exception e) {
            throw new SQLException("Could not create database folder.", e);
        }

        Connection conn = DriverManager.getConnection(DB_URL);

        try (var statement = conn.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            return conn;
        } catch (SQLException e) {
            conn.close();
            throw e;
        }
    }

    public static void initialize() {
        String sql = """
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT NOT NULL,
                email TEXT UNIQUE NOT NULL,
                password_hash TEXT,
                password_salt TEXT,
                role TEXT NOT NULL DEFAULT 'STUDENT'
            )
            """;

        try (Connection conn = connect();
             var statement = conn.createStatement()) {

            statement.execute(sql);

            // Add missing columns to an existing database safely.
            ensureColumn(conn, "password_hash", "TEXT");
            ensureColumn(conn, "password_salt", "TEXT");
            ensureColumn(conn, "role",
                    "TEXT NOT NULL DEFAULT 'STUDENT'");

            System.out.println("SQLite connected successfully!");
            System.out.println("ENJAMI users table is ready.");

        } catch (SQLException e) {
            System.err.println("SQLite initialization failed:");
            e.printStackTrace();
        }
    }

    private static void ensureColumn(
            Connection conn, String columnName, String columnType)
            throws SQLException {

        boolean exists = false;

        try (var statement = conn.createStatement();
             ResultSet rs = statement.executeQuery(
                     "PRAGMA table_info(users)")) {

            while (rs.next()) {
                if (columnName.equalsIgnoreCase(rs.getString("name"))) {
                    exists = true;
                    break;
                }
            }
        }

        if (!exists) {
            String sql = "ALTER TABLE users ADD COLUMN "
                    + columnName + " " + columnType;

            try (var statement = conn.createStatement()) {
                statement.execute(sql);
            }

            System.out.println("Added missing column: " + columnName);
        }
    }

    public static boolean addUser(String username, String email)
            throws SQLException {

        String sql = """
            INSERT INTO users (username, email)
            VALUES (?, ?)
            """;

        try (Connection conn = connect();
             var stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            stmt.setString(2, email);

            return stmt.executeUpdate() == 1;
        }
    }

    public static void listUsers() throws SQLException {
        String sql = "SELECT id, username, email, role FROM users";

        try (Connection conn = connect();
             var stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                System.out.println(
                    rs.getInt("id") + " | "
                    + rs.getString("username") + " | "
                    + rs.getString("email") + " | "
                    + rs.getString("role")
                );
            }
        }
    }
}

