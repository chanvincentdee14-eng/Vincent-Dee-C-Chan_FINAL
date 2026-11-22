package chc;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A person who can sign in (OOP: abstraction + inheritance).
 * The common data lives here; {@link AdminUser} and {@link StaffUser} decide what each role may do,
 * so the rest of the program never has to ask "if role equals ADMIN".
 */
public abstract class User {
    /** Screens every signed-in user can open. */
    protected static final List<String> BASIC_SCREENS = Collections.unmodifiableList(
            Arrays.asList("Dashboard", "Patients", "Queue Simulation", "Records"));

    private final int id;
    private final String username;

    protected User(int id, String username) {
        this.id = id;
        this.username = username;
    }

    /** Creates the right kind of user for a role name stored in the database. */
    public static User create(int id, String username, String role) {
        return "ADMIN".equals(role) ? new AdminUser(id, username) : new StaffUser(id, username);
    }

    public int getId() { return id; }

    public String getUsername() { return username; }

    /** The role as stored in the database ("ADMIN" or "STAFF"). */
    public abstract String getRole();

    /** The role as shown to people ("Administrator" or "Staff"). */
    public abstract String getRoleLabel();

    /** The names of the screens this user may open, in menu order. */
    public abstract List<String> getScreens();

    /** True when this user may add, delete and change staff accounts. */
    public boolean canManageAccounts() { return getScreens().contains("Administrator"); }

    public boolean isAdmin() { return canManageAccounts(); }

    @Override public String toString() { return username + " (" + getRoleLabel() + ")"; }
}
