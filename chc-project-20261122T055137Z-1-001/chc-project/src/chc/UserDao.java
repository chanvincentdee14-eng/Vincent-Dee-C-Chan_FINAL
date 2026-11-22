package chc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** The MySQL implementation of {@link UserRepository}: login and staff-account management. */
public class UserDao implements UserRepository {

    /** Returns the user when the username and password are right, otherwise null. */
    @Override public User authenticate(String username, String password) throws ServiceException {
        if (username == null || password == null) return null;
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, username, password, role FROM users WHERE username = ?")) {
            ps.setString(1, username.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                if (!password.equals(rs.getString("password"))) return null;
                return User.create(rs.getInt("id"), rs.getString("username"), rs.getString("role"));
            }
        } catch (SQLException e) {
            throw new ServiceException("Cannot reach the database. Is MySQL running in XAMPP?", e);
        }
    }

    @Override public List<User> list() throws ServiceException {
        List<User> out = new ArrayList<User>();
        try (Connection c = Db.get(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, username, role FROM users ORDER BY role, username")) {
            while (rs.next()) out.add(User.create(rs.getInt(1), rs.getString(2), rs.getString(3)));
        } catch (SQLException e) {
            throw new ServiceException("Could not load accounts: " + e.getMessage(), e);
        }
        return out;
    }

    @Override public void add(String username, String password, String role) throws ServiceException {
        username = username == null ? "" : username.trim();
        if (!username.matches("[A-Za-z0-9_.-]{3,30}"))
            throw new ServiceException("Username must be 3-30 characters (letters, numbers, . _ -).");
        checkPassword(password);
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO users(username, password, role) VALUES (?,?,?)")) {
            ps.setString(1, username);
            ps.setString(2, password);
            ps.setString(3, role);
            ps.executeUpdate();
        } catch (java.sql.SQLIntegrityConstraintViolationException e) {
            throw new ServiceException("The username \"" + username + "\" already exists.");
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) throw new ServiceException("The username \"" + username + "\" already exists.");
            throw new ServiceException("Could not add the account: " + e.getMessage(), e);
        }
    }

    @Override public void delete(int id, int currentUserId) throws ServiceException {
        if (id == currentUserId) throw new ServiceException("You cannot delete the account you are signed in with.");
        guardLastAdmin(id);
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement("DELETE FROM users WHERE id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new ServiceException("Could not delete the account: " + e.getMessage(), e);
        }
    }

    @Override public void setRole(int id, String role, int currentUserId) throws ServiceException {
        if (!"ADMIN".equals(role)) {
            if (id == currentUserId) throw new ServiceException("You cannot remove your own Administrator role.");
            guardLastAdmin(id);
        }
        try (Connection c = Db.get(); PreparedStatement ps = c.prepareStatement("UPDATE users SET role = ? WHERE id = ?")) {
            ps.setString(1, role);
            ps.setInt(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new ServiceException("Could not change the role: " + e.getMessage(), e);
        }
    }

    @Override public void resetPassword(int id, String newPassword) throws ServiceException {
        checkPassword(newPassword);
        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement("UPDATE users SET password = ? WHERE id = ?")) {
            ps.setString(1, newPassword);
            ps.setInt(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new ServiceException("Could not reset the password: " + e.getMessage(), e);
        }
    }

    private void checkPassword(String p) throws ServiceException {
        if (p == null || p.length() < 8) throw new ServiceException("Password must be at least 8 characters.");
    }

    /** Refuses to remove / demote the only administrator. */
    private void guardLastAdmin(int id) throws ServiceException {
        try (Connection c = Db.get()) {
            String role = null;
            try (PreparedStatement ps = c.prepareStatement("SELECT role FROM users WHERE id = ?")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) role = rs.getString(1); }
            }
            if (!"ADMIN".equals(role)) return;
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM users WHERE role = 'ADMIN'")) {
                rs.next();
                if (rs.getInt(1) <= 1) throw new ServiceException("There must be at least one Administrator account.");
            }
        } catch (SQLException e) {
            throw new ServiceException("Database error: " + e.getMessage(), e);
        }
    }

    /** Creates admin/admin123 and staff/staff123 when the users table is empty. */
    void ensureDefaultUsers() throws SQLException {
        try (Connection c = Db.get(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users")) {
            rs.next();
            if (rs.getInt(1) > 0) return;
        }
        try {
            add("admin", "admin123", "ADMIN");
            add("staff", "staff123", "STAFF");
        } catch (ServiceException e) {
            throw new SQLException(e.getMessage(), e);
        }
    }
}
