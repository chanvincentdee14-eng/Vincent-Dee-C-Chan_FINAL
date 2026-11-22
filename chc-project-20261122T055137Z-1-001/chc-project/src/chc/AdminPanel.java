package chc;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;

/** Administrator - Accounts: add, delete, change role and reset password of staff accounts. */
public class AdminPanel extends ScreenPanel {
    private final UserRepository repo;
    private final User me;
    private final Consumer<String> status;
    private final JTextField userField = new JTextField();
    private final JPasswordField passField = new JPasswordField();
    private final JComboBox<String> roleBox = new JComboBox<String>(new String[]{"Staff", "Administrator"});
    private final DefaultTableModel model = new DefaultTableModel(new Object[]{"Username", "Role"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);
    private List<User> users = new ArrayList<User>();

    public AdminPanel(UserRepository repo, User me, Consumer<String> status) {
        super("Administrator");
        this.repo = repo;
        this.me = me;
        this.status = status;
        setLayout(new BorderLayout(0, 8));
        setBackground(Ui.BG);
        setBorder(BorderFactory.createEmptyBorder(14, 14, 8, 14));

        JPanel north = new JPanel(new BorderLayout(0, 8));
        north.setOpaque(false);
        north.add(Ui.titleBar("Administrator - Accounts", null), BorderLayout.NORTH);

        // ---- add-account form
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBackground(Color.WHITE);
        form.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Ui.BORDER),
                BorderFactory.createEmptyBorder(14, 14, 12, 14)));
        JPanel row = Ui.flow(FlowLayout.CENTER, 12, 0);
        userField.setPreferredSize(new Dimension(170, 28));
        passField.setPreferredSize(new Dimension(170, 28));
        Ui.FlatButton add = Ui.button("ADD ACCOUNT", Ui.NAVY);
        add.setPreferredSize(new Dimension(108, 36));
        row.add(Ui.label("Username", Font.BOLD, 12, Ui.TEXT)); row.add(userField);
        row.add(Ui.label("Password", Font.BOLD, 12, Ui.TEXT)); row.add(passField);
        row.add(Ui.label("Role", Font.BOLD, 12, Ui.TEXT)); row.add(roleBox);
        row.add(add);
        row.setAlignmentX(0.5f);
        form.add(row);
        JLabel help = Ui.label("Passwords must be at least 8 characters. Select a row below to delete, change its role, "
                + "or reset its password (using the Password box).", Font.BOLD, 12, Ui.MUTED);
        help.setHorizontalAlignment(SwingConstants.CENTER);
        help.setAlignmentX(0.5f);
        form.add(javax.swing.Box.createVerticalStrut(8));
        form.add(help);

        // ---- row buttons
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        bar.setBackground(Color.WHITE);
        Ui.FlatButton role = Ui.button("CHANGE ROLE", Ui.TEAL);
        Ui.FlatButton reset = Ui.button("RESET PASSWORD", Ui.TEAL);
        Ui.FlatButton del = Ui.button("DELETE ACCOUNT", new Color(0xA52A2A));
        JButton refresh = Ui.plainButton("Refresh");
        refresh.setPreferredSize(new Dimension(80, 28));
        bar.add(role); bar.add(reset); bar.add(del); bar.add(refresh);

        JPanel stack = new JPanel(new BorderLayout());
        stack.setOpaque(false);
        stack.add(form, BorderLayout.NORTH);
        stack.add(bar, BorderLayout.CENTER);
        north.add(stack, BorderLayout.CENTER);
        add(north, BorderLayout.NORTH);

        // ---- table
        Ui.styleTable(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createLineBorder(Ui.BORDER));
        sp.getViewport().setBackground(Color.WHITE);
        add(sp, BorderLayout.CENTER);

        add.addActionListener(e -> addAccount());
        role.addActionListener(e -> changeRole());
        reset.addActionListener(e -> resetPassword());
        del.addActionListener(e -> deleteAccount());
        refresh.addActionListener(e -> refresh());
        passField.addActionListener(e -> addAccount());
        refresh();
    }

    @Override public void refresh() {
        try {
            users = repo.list();
        } catch (ServiceException ex) {
            Ui.error(this, ex.getMessage());
            return;
        }
        model.setRowCount(0);
        for (User u : users) model.addRow(new Object[]{u.getUsername(), u.getRoleLabel()});
    }

    private User selected() {
        int r = table.getSelectedRow();
        if (r < 0 || r >= users.size()) { Ui.error(this, "Select an account in the table first."); return null; }
        return users.get(r);
    }

    private void addAccount() {
        try {
            repo.add(userField.getText(), new String(passField.getPassword()),
                    roleBox.getSelectedIndex() == 1 ? "ADMIN" : "STAFF");
            status.accept("Account \"" + userField.getText().trim() + "\" added.");
            userField.setText("");
            passField.setText("");
            roleBox.setSelectedIndex(0);
            refresh();
        } catch (ServiceException ex) {
            Ui.error(this, ex.getMessage());
        }
    }

    private void changeRole() {
        User u = selected();
        if (u == null) return;
        String newRole = u.isAdmin() ? "STAFF" : "ADMIN";
        if (!Ui.confirm(this, "Change " + u.getUsername() + " from " + u.getRoleLabel() + " to "
                + (u.isAdmin() ? "Staff" : "Administrator") + "?")) return;
        try {
            repo.setRole(u.getId(), newRole, me.getId());
            status.accept("Role of " + u.getUsername() + " changed.");
            refresh();
        } catch (ServiceException ex) {
            Ui.error(this, ex.getMessage());
        }
    }

    private void resetPassword() {
        User u = selected();
        if (u == null) return;
        String pw = new String(passField.getPassword());
        if (pw.isEmpty()) { Ui.error(this, "Type the new password in the Password box first, then press Reset Password."); return; }
        try {
            repo.resetPassword(u.getId(), pw);
            passField.setText("");
            status.accept("Password of " + u.getUsername() + " was reset.");
        } catch (ServiceException ex) {
            Ui.error(this, ex.getMessage());
        }
    }

    private void deleteAccount() {
        User u = selected();
        if (u == null) return;
        if (!Ui.confirm(this, "Delete the account \"" + u.getUsername() + "\"?")) return;
        try {
            repo.delete(u.getId(), me.getId());
            status.accept("Account \"" + u.getUsername() + "\" deleted.");
            refresh();
        } catch (ServiceException ex) {
            Ui.error(this, ex.getMessage());
        }
    }
}
