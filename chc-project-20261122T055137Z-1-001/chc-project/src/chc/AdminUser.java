package chc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The administrator: can do everything staff can, and also manage the staff accounts. */
public class AdminUser extends User {
    public AdminUser(int id, String username) { super(id, username); }

    @Override public String getRole() { return "ADMIN"; }

    @Override public String getRoleLabel() { return "Administrator"; }

    @Override public List<String> getScreens() {
        List<String> all = new ArrayList<String>(BASIC_SCREENS);
        all.add("Administrator");
        return Collections.unmodifiableList(all);
    }
}
