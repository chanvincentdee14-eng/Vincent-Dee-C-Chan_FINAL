package chc;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * Database access for XAMPP's MySQL using MySQL Connector/J.
 * Settings are read from config.properties (next to the program); the defaults match a fresh XAMPP.
 */
public final class Db {
    private static String host = "localhost";
    private static String port = "3306";
    private static String name = "chc_queue";
    private static String user = "root";
    private static String pass = "";

    private Db() { }

    static {
        File f = new File("config.properties");
        if (f.isFile()) {
            Properties p = new Properties();
            try (InputStream in = new FileInputStream(f)) {
                p.load(in);
                host = p.getProperty("db.host", host).trim();
                port = p.getProperty("db.port", port).trim();
                name = p.getProperty("db.name", name).trim();
                user = p.getProperty("db.user", user).trim();
                pass = p.getProperty("db.password", pass);
            } catch (Exception ignored) {
                // keep defaults
            }
        }
    }

    public static String describe() { return host + ":" + port + "/" + name; }

    private static String url(String db) {
        // serverTimezone: XAMPP runs on this same computer, so it uses the same time zone as Java
        return "jdbc:mysql://" + host + ":" + port + "/" + db
                + "?connectTimeout=3000&socketTimeout=15000&useSSL=false&allowPublicKeyRetrieval=true"
                + "&serverTimezone=" + java.util.TimeZone.getDefault().getID();
    }

    public static Connection get() throws SQLException {
        return DriverManager.getConnection(url(name), user, pass);
    }

    /** Creates the database, tables and default accounts when they do not exist yet. */
    public static void init() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL Connector/J is missing. Put mysql-connector-j-8.x.jar in the lib folder "
                    + "(see lib/README.txt).", e);
        }
        try (Connection c = DriverManager.getConnection(url(""), user, pass);
             Statement s = c.createStatement()) {
            s.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + name
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
        try (Connection c = get(); Statement s = c.createStatement()) {
            // older versions stored hashed passwords: rebuild the users table with plain passwords
            try (ResultSet old = c.getMetaData().getColumns(c.getCatalog(), null, "users", "password_hash")) {
                if (old.next()) s.executeUpdate("DROP TABLE users");
            }
            s.executeUpdate("CREATE TABLE IF NOT EXISTS users ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY,"
                    + "username VARCHAR(50) NOT NULL UNIQUE,"
                    + "password VARCHAR(100) NOT NULL,"
                    + "role ENUM('ADMIN','STAFF') NOT NULL DEFAULT 'STAFF',"
                    + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP"
                    + ") ENGINE=InnoDB");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS visits ("
                    + "id INT AUTO_INCREMENT PRIMARY KEY,"
                    + "queue_no VARCHAR(12) NOT NULL,"
                    + "line_seq BIGINT NOT NULL,"
                    + "full_name VARCHAR(100) NOT NULL,"
                    + "contact VARCHAR(30) NULL,"
                    + "birthday DATE NULL,"
                    + "address VARCHAR(255) NULL,"
                    + "status ENUM('WAITING','SERVING','ON_HOLD','COMPLETED','DEQUEUED','DELETED') NOT NULL DEFAULT 'WAITING',"
                    + "previous_numbers VARCHAR(120) NULL,"
                    + "registered_by VARCHAR(50) NULL,"
                    + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "called_at DATETIME NULL,"
                    + "finished_at DATETIME NULL,"
                    + "INDEX idx_visits_status (status),"
                    + "INDEX idx_visits_created (created_at),"
                    + "INDEX idx_visits_line (line_seq)"
                    + ") ENGINE=InnoDB");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS counters ("
                    + "name VARCHAR(30) PRIMARY KEY,"
                    + "value BIGINT NOT NULL DEFAULT 0"
                    + ") ENGINE=InnoDB");
        }
        new UserDao().ensureDefaultUsers();
    }

    /** Atomically returns the next number of a named counter (1, 2, 3, ...). */
    static long nextValue(Connection c, String counter) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT IGNORE INTO counters(name, value) VALUES (?, 0)")) {
            ps.setString(1, counter);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE counters SET value = LAST_INSERT_ID(value + 1) WHERE name = ?")) {
            ps.setString(1, counter);
            ps.executeUpdate();
        }
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT LAST_INSERT_ID()")) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
