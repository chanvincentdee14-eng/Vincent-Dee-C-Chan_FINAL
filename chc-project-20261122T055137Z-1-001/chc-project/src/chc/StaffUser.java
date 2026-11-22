package chc;

import java.util.List;

/** A staff member: registers patients, runs the queue and reads the records. */
public class StaffUser extends User {
    public StaffUser(int id, String username) { super(id, username); }

    @Override public String getRole() { return "STAFF"; }

    @Override public String getRoleLabel() { return "Staff"; }

    @Override public List<String> getScreens() { return BASIC_SCREENS; }
}
