package chc;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;

/** Patients - Check In: the active queue on the left, the registration form with "Select Action" on the right. */
public class PatientsPanel extends ScreenPanel {
    private static final String[] ACTIONS = {"Enqueue", "Edit", "Update", "Done", "Dequeue", "Delete"};
    private static final int STATUS_COL = 6;

    private final QueueService service;
    private final User user;
    private final Consumer<String> status;

    private final JTextField search = new JTextField();
    private final JTextField queueField = new JTextField();
    private final JTextField nameField = new JTextField();
    private final JTextField contactField = new JTextField();
    private final DatePicker birthPicker = new DatePicker();
    private final JTextField addressField = new JTextField();
    private final JLabel modeLabel = Ui.label(" ", Font.BOLD, 12, Ui.TEAL);
    private final JComboBox<String> actionBox = new JComboBox<String>(ACTIONS);
    private final JLabel hint = new JLabel();
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"Queue No.", "Patient Name", "Contact", "Birthday", "Address", "Checked In", "Status"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);
    private List<Visit> rows = new ArrayList<Visit>();
    private boolean loading;

    public PatientsPanel(QueueService service, User user, Consumer<String> status) {
        super("Patients");
        this.service = service;
        this.user = user;
        this.status = status;
        setLayout(new BorderLayout(0, 8));
        setBackground(Ui.BG);
        setBorder(BorderFactory.createEmptyBorder(14, 14, 8, 14));

        JLabel note = Ui.label("Arrival time is recorded automatically", Font.BOLD, 12, Ui.TEXT);
        add(Ui.titleBar("Patients - Check In", note), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(10, 0));
        center.setOpaque(false);
        center.add(buildList(), BorderLayout.CENTER);
        center.add(buildForm(), BorderLayout.EAST);
        add(center, BorderLayout.CENTER);

        actionBox.addActionListener(e -> actionChanged());
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refresh(); }
            public void removeUpdate(DocumentEvent e) { refresh(); }
            public void changedUpdate(DocumentEvent e) { refresh(); }
        });
        actionChanged();
        refresh();
    }

    // ------------------------------------------------------------------ layout

    private JPanel buildList() {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createLineBorder(Ui.BORDER));

        JPanel s = new JPanel(new BorderLayout(6, 0));
        s.setOpaque(false);
        s.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        s.add(Ui.label("Search name, queue number or contact:", Font.BOLD, 12, Ui.TEXT), BorderLayout.WEST);
        s.add(search, BorderLayout.CENTER);
        p.add(s, BorderLayout.NORTH);

        Ui.styleTable(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(STATUS_COL).setCellRenderer(new Ui.StatusCellRenderer());
        int[] w = {80, 130, 110, 90, 130, 150, 100};
        for (int i = 0; i < w.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(w[i]);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !loading) selectionChanged();
        });
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(Color.WHITE);
        p.add(sp, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildForm() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Ui.BORDER),
                BorderFactory.createEmptyBorder(14, 12, 12, 12)));
        p.setPreferredSize(new Dimension(300, 100));

        p.add(left(Ui.label("Patient Registration", Font.BOLD, 18, Ui.NAVY)));
        p.add(Box.createVerticalStrut(6));
        p.add(left(modeLabel));
        p.add(Box.createVerticalStrut(12));

        queueField.setEditable(false);
        queueField.setBackground(new Color(0xEEEEEE));
        addField(p, "Queue Number (automatic)", queueField);
        addField(p, "Full Name *", nameField);
        addField(p, "Contact Number", contactField);
        addField(p, "Birthday (choose from calendar)", birthPicker);
        addField(p, "Address", addressField);
        p.add(Box.createVerticalGlue());

        // the "Select Action" box
        JPanel act = new JPanel();
        act.setLayout(new BoxLayout(act, BoxLayout.Y_AXIS));
        act.setBackground(new Color(0xE9F4F5));
        act.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0xC9DEE0)),
                BorderFactory.createEmptyBorder(10, 12, 12, 12)));
        act.setAlignmentX(0f);

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(Ui.label("Select Action", Font.BOLD, 15, Ui.NAVY), BorderLayout.WEST);
        JLabel clear = Ui.label("New / Clear", Font.BOLD, 11, Ui.TEAL);
        clear.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        clear.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { newEntry(); }
        });
        head.add(clear, BorderLayout.EAST);
        head.setAlignmentX(0f);
        head.setMaximumSize(new Dimension(1000, 24));
        act.add(head);
        act.add(Box.createVerticalStrut(6));

        actionBox.setAlignmentX(0f);
        actionBox.setMaximumSize(new Dimension(1000, 30));
        actionBox.setPreferredSize(new Dimension(100, 30));
        act.add(actionBox);
        act.add(Box.createVerticalStrut(6));
        hint.setFont(Ui.font(Font.PLAIN, 11));
        hint.setForeground(Ui.TEXT);
        act.add(left(hint));
        act.add(Box.createVerticalStrut(10));

        Ui.FlatButton apply = Ui.button("APPLY ACTION", Ui.TEAL);
        apply.setAlignmentX(0f);
        apply.setMaximumSize(new Dimension(1000, 38));
        apply.setPreferredSize(new Dimension(100, 38));
        apply.addActionListener(e -> apply());
        act.add(apply);
        p.add(act);

        for (JTextField f : new JTextField[]{nameField, contactField, addressField})
            f.addActionListener(e -> apply());
        return p;
    }

    private static <T extends JComponent> T left(T c) { c.setAlignmentX(0f); return c; }

    private static void addField(JPanel p, String caption, JComponent field) {
        p.add(left(Ui.label(caption, Font.BOLD, 12, Ui.TEXT)));
        p.add(Box.createVerticalStrut(4));
        field.setAlignmentX(0f);
        field.setPreferredSize(new Dimension(100, 30));
        field.setMaximumSize(new Dimension(1000, 30));
        p.add(field);
        p.add(Box.createVerticalStrut(12));
    }

    // ------------------------------------------------------------------ behaviour

    private String action() { return (String) actionBox.getSelectedItem(); }

    private Visit selected() {
        int r = table.getSelectedRow();
        return r < 0 || r >= rows.size() ? null : rows.get(r);
    }

    private static String html(String s) { return "<html>" + s + "</html>"; }

    private void actionChanged() {
        String a = action();
        String h;
        if ("Enqueue".equals(a)) h = "Register visit, assign Q-number.<br>Joins the END of the line.";
        else if ("Edit".equals(a)) h = "Select a patient to load<br>the details into the form.";
        else if ("Update".equals(a)) h = "Change details, then Apply to save.<br>The queue number stays.";
        else if ("Done".equals(a)) h = "Select the patient being served,<br>then Apply: next is called.";
        else if ("Dequeue".equals(a)) h = "Patient left before consultation.<br>Visit stays in Records (red).";
        else h = "Remove a mistaken registration.<br>Visit stays in Records (red).";
        hint.setText(html(h));
        if ("Enqueue".equals(a)) clearForm();
        else if ("Edit".equals(a) || "Update".equals(a)) selectionChanged();
    }

    private void newEntry() {
        table.clearSelection();
        if (!"Enqueue".equals(action())) actionBox.setSelectedItem("Enqueue");
        clearForm();
        nameField.requestFocusInWindow();
    }

    private void selectionChanged() {
        String a = action();
        if (!"Edit".equals(a) && !"Update".equals(a)) return;
        Visit v = selected();
        if (v == null) { clearForm(); return; }
        loadIntoForm(v);
    }

    private void clearForm() {
        queueField.setText("Auto-generated");
        nameField.setText(""); contactField.setText(""); addressField.setText("");
        birthPicker.setValue(null);
        modeLabel.setText("New registration - ready to check in");
    }

    private void loadIntoForm(Visit v) {
        queueField.setText(v.getQueueNo());
        nameField.setText(v.getFullName());
        contactField.setText(v.getContact() == null ? "" : v.getContact());
        birthPicker.setValue(v.getBirthday());
        addressField.setText(v.getAddress() == null ? "" : v.getAddress());
        modeLabel.setText("Editing " + v.getQueueNo() + " - " + Ui.displayStatus(v, service.peekNext()));
    }

    private void apply() {
        String a = action();
        try {
            if ("Enqueue".equals(a)) {
                Visit v = service.enqueue(nameField.getText(), contactField.getText(), birthPicker.getValue(),
                        addressField.getText(), user.getUsername());
                status.accept(v == null ? "Patient registered."
                        : v.getStatus() == Status.SERVING ? v.getQueueNo() + " registered and is now being served."
                        : v.getQueueNo() + " registered and added to the end of the line.");
                clearForm();
                nameField.requestFocusInWindow();
                return;
            }
            Visit v = selected();
            if (v == null) { Ui.error(this, "Select a patient in the table first."); return; }
            if ("Edit".equals(a)) {
                actionBox.setSelectedItem("Update");   // loads the selected patient into the form
                status.accept("Editing " + v.getQueueNo() + ". Change the details, then press Apply Action to save.");
            } else if ("Update".equals(a)) {
                service.update(v.getId(), nameField.getText(), contactField.getText(), birthPicker.getValue(),
                        addressField.getText());
                status.accept(v.getQueueNo() + " updated.");
            } else if ("Done".equals(a)) {
                if (v.getStatus() != Status.SERVING)
                    throw new ServiceException(v.getQueueNo() + " is not being served yet. Use Call Next in Queue Simulation first.");
                Visit next = service.complete(v.getId());
                status.accept(v.getQueueNo() + " completed." + (next != null ? " Now serving " + next.getQueueNo() + "." : " No patient waiting."));
            } else if ("Dequeue".equals(a)) {
                if (Ui.confirm(this, "Dequeue " + v.getQueueNo() + " - " + v.getFullName() + "?\nThe visit will stay in Records.")) {
                    service.dequeue(v.getId());
                    status.accept(v.getQueueNo() + " dequeued (kept in Records).");
                }
            } else if ("Delete".equals(a)) {
                if (Ui.confirm(this, "Delete the mistaken registration " + v.getQueueNo() + " - " + v.getFullName()
                        + "?\nThe visit will stay in Records.")) {
                    service.delete(v.getId());
                    status.accept(v.getQueueNo() + " deleted (kept in Records).");
                }
            }
        } catch (ServiceException ex) {
            Ui.error(this, ex.getMessage());
        }
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private boolean matches(Visit v, String q) {
        return v.getQueueNo().toLowerCase().contains(q) || v.getFullName().toLowerCase().contains(q)
                || nz(v.getContact()).toLowerCase().contains(q);
    }

    /** Reloads the table from the queue (and the search box), keeping the selected row. */
    @Override public void refresh() {
        Visit sel = selected();
        int selId = sel == null ? -1 : sel.getId();
        loading = true;
        Visit next = service.peekNext();
        String q = search.getText().trim().toLowerCase();
        rows = new ArrayList<Visit>();
        model.setRowCount(0);
        for (Visit v : service.getActive()) {
            if (!q.isEmpty() && !matches(v, q)) continue;
            rows.add(v);
            model.addRow(new Object[]{v.getQueueNo(), v.getFullName(), nz(v.getContact()),
                    v.getBirthday() == null ? "-" : v.getBirthday().toString(), nz(v.getAddress()),
                    v.getCreatedAt() == null ? "" : v.getCreatedAt().format(Ui.STAMP), Ui.displayStatus(v, next)});
        }
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).getId() == selId) { table.setRowSelectionInterval(i, i); break; }
        }
        loading = false;
    }
}
