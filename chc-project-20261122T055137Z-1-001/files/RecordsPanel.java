package chc;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;

/** Records - All Patient Visits: filters (period, sort, status, search), color-coded status, CSV export and printing. */
public class RecordsPanel extends ScreenPanel {
    /** Columns of the CSV file (more detail than the screen shows). */
    private static final String[] CSV_COLS = {"Queue No.", "Patient", "Contact", "Birthday", "Address", "Status",
            "Registered", "Called", "Finished", "Wait (min)", "Old Nos."};
    /** Filter entry for the first waiting patient (same wording as the Patients and Queue Simulation screens). */
    private static final String NEXT_TO_SERVE = "Next to Serve";
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final QueueService service;
    private final Consumer<String> status;

    private final JComboBox<String> periodBox = new JComboBox<String>(
            new String[]{"All Time", "Today", "Last 7 Days", "Last 30 Days", "This Month"});
    private final JComboBox<String> sortBox = new JComboBox<String>(
            new String[]{"Newest First", "Oldest First", "Queue No.", "Patient Name"});
    private final JComboBox<Object> statusBox = new JComboBox<Object>();
    private final JTextField search = new JTextField();
    private final JLabel count = Ui.label(" ", Font.BOLD, 12, Ui.TEXT);
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"Queue No.", "Patient Name", "Checked In", "Finished / Removed", "Visit Status"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);
    private final Timer debounce;
    private List<Visit> rows = new ArrayList<Visit>();
    private Visit shownNext;

    public RecordsPanel(QueueService service, Consumer<String> status) {
        super("Records");
        this.service = service;
        this.status = status;
        setLayout(new BorderLayout(0, 8));
        setBackground(Ui.BG);
        setBorder(BorderFactory.createEmptyBorder(14, 14, 8, 14));

        // ---- title, filters, search
        JPanel north = new JPanel(new BorderLayout(0, 8));
        north.setOpaque(false);
        north.add(Ui.titleBar("Records - All Patient Visits", count), BorderLayout.NORTH);

        statusBox.addItem("All Statuses");
        for (Status s : Status.values()) {
            statusBox.addItem(s);
            if (s == Status.WAITING) statusBox.addItem(NEXT_TO_SERVE);
        }
        JPanel filters = Ui.flow(FlowLayout.LEFT, 8, 0);
        filters.add(Ui.label("Period:", Font.BOLD, 12, Ui.TEXT)); filters.add(periodBox);
        filters.add(Ui.label("   Sort:", Font.BOLD, 12, Ui.TEXT)); filters.add(sortBox);
        filters.add(Ui.label("   Status:", Font.BOLD, 12, Ui.TEXT)); filters.add(statusBox);

        JPanel searchRow = new JPanel(new BorderLayout(8, 0));
        searchRow.setOpaque(false);
        searchRow.add(Ui.label("Search visit history:", Font.BOLD, 12, Ui.TEXT), BorderLayout.WEST);
        searchRow.add(search, BorderLayout.CENTER);
        JPanel buttons = Ui.flow(FlowLayout.RIGHT, 8, 0);
        JButton print = Ui.plainButton("Print Report");
        Ui.FlatButton csv = Ui.button("EXPORT CSV", Ui.TEAL);
        print.setPreferredSize(new Dimension(110, 30));
        csv.setPreferredSize(new Dimension(122, 36));
        buttons.add(print); buttons.add(csv);
        searchRow.add(buttons, BorderLayout.EAST);

        JPanel stack = new JPanel(new BorderLayout(0, 8));
        stack.setOpaque(false);
        stack.add(filters, BorderLayout.NORTH);
        stack.add(searchRow, BorderLayout.CENTER);
        north.add(stack, BorderLayout.CENTER);
        add(north, BorderLayout.NORTH);

        // ---- table
        Ui.styleTable(table);
        table.getColumnModel().getColumn(4).setCellRenderer(new Ui.StatusCellRenderer());
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createLineBorder(Ui.BORDER));
        sp.getViewport().setBackground(Color.WHITE);
        add(sp, BorderLayout.CENTER);

        add(Ui.label("Records include checked-in, completed, dequeued and removed visits. Dates use original check-in time.",
                Font.BOLD, 12, Ui.TEXT), BorderLayout.SOUTH);

        // ---- behaviour
        debounce = new Timer(300, e -> refresh());
        debounce.setRepeats(false);
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { debounce.restart(); }
            public void removeUpdate(DocumentEvent e) { debounce.restart(); }
            public void changedUpdate(DocumentEvent e) { debounce.restart(); }
        });
        periodBox.addActionListener(e -> refresh());
        sortBox.addActionListener(e -> refresh());
        statusBox.addActionListener(e -> refresh());
        csv.addActionListener(e -> exportCsv());
        print.addActionListener(e -> printTable());
        refresh();
    }

    private LocalDate periodStart() {
        LocalDate today = LocalDate.now();
        switch (periodBox.getSelectedIndex()) {
            case 1: return today;
            case 2: return today.minusDays(6);
            case 3: return today.minusDays(29);
            case 4: return today.withDayOfMonth(1);
            default: return null;
        }
    }

    /** Loads the records using the current filters. */
    @Override public void refresh() {
        LocalDate from = periodStart();
        LocalDate to = from == null ? null : LocalDate.now();
        Object sel = statusBox.getSelectedItem();
        boolean nextOnly = NEXT_TO_SERVE.equals(sel);
        Status st = nextOnly ? Status.WAITING : (sel instanceof Status ? (Status) sel : null);
        int total;
        Visit next;
        try {
            service.reload(); // make sure "Next to Serve" is based on the latest queue
            next = service.peekNext();
            rows = service.records(from, to, st, search.getText());
            total = service.records(null, null, null, "").size();
        } catch (ServiceException ex) {
            Ui.error(this, ex.getMessage());
            return;
        }
        // keep the filter in line with the status text shown in the table
        for (java.util.Iterator<Visit> it = rows.iterator(); it.hasNext();) {
            Visit v = it.next();
            boolean isNext = next != null && v.getStatus() == Status.WAITING && v.getId() == next.getId();
            if (nextOnly && !isNext) it.remove();
            else if (sel == Status.WAITING && isNext) it.remove();
        }
        shownNext = next;
        switch (sortBox.getSelectedIndex()) {
            case 1: Collections.reverse(rows); break;
            case 2: Collections.sort(rows, new Comparator<Visit>() {
                public int compare(Visit a, Visit b) { return a.getQueueNo().compareTo(b.getQueueNo()); }
            }); break;
            case 3: Collections.sort(rows, new Comparator<Visit>() {
                public int compare(Visit a, Visit b) { return a.getFullName().compareToIgnoreCase(b.getFullName()); }
            }); break;
            default: break; // newest first, as loaded
        }
        model.setRowCount(0);
        for (Visit v : rows) {
            model.addRow(new Object[]{v.getQueueNo(), v.getFullName(),
                    v.getCreatedAt() == null ? "" : v.getCreatedAt().format(Ui.STAMP),
                    v.getFinishedAt() == null ? "-" : v.getFinishedAt().format(Ui.STAMP), Ui.displayStatus(v, next)});
        }
        count.setText(rows.size() + " of " + total + " visits");
    }

    private static String nz(String s) { return s == null ? "" : s; }

    private void exportCsv() {
        if (rows.isEmpty()) { Ui.info(this, "There are no records to export."); return; }
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("records_" + LocalDate.now() + ".csv"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        if (!f.getName().toLowerCase().endsWith(".csv")) f = new File(f.getPath() + ".csv");
        if (f.exists() && !Ui.confirm(this, f.getName() + " already exists. Replace it?")) return;
        try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(f), "UTF-8"))) {
            out.print('\uFEFF'); // lets Excel read the file as UTF-8
            out.print(csvLine(CSV_COLS));
            for (Visit v : rows) {
                long wait = v.waitMinutes();
                out.print(csvLine(new String[]{v.getQueueNo(), v.getFullName(), nz(v.getContact()),
                        v.getBirthday() == null ? "" : v.getBirthday().toString(), nz(v.getAddress()), Ui.displayStatus(v, shownNext),
                        v.getCreatedAt() == null ? "" : v.getCreatedAt().format(ISO),
                        v.getCalledAt() == null ? "" : v.getCalledAt().format(ISO),
                        v.getFinishedAt() == null ? "" : v.getFinishedAt().format(ISO),
                        wait < 0 ? "" : String.valueOf(wait), nz(v.getPreviousNumbers())}));
            }
            status.accept("Exported " + rows.size() + " records to " + f.getName());
            Ui.info(this, "Saved " + rows.size() + " records to:\n" + f.getAbsolutePath());
        } catch (Exception ex) {
            Ui.error(this, "Could not save the file: " + ex.getMessage());
        }
    }

    private static String csvLine(String[] cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) sb.append(',');
            String c = cells[i] == null ? "" : cells[i];
            // stop spreadsheet formula injection, but keep phone numbers such as +63 912 345 6789 untouched
            if (c.matches("^[=@].*") || c.matches("^[+\\-][^0-9\\s()+\\-].*")) c = "'" + c;
            sb.append('"').append(c.replace("\"", "\"\"")).append('"');
        }
        return sb.append("\r\n").toString();
    }

    private void printTable() {
        if (rows.isEmpty()) { Ui.info(this, "There are no records to print."); return; }
        try {
            table.print(JTable.PrintMode.FIT_WIDTH,
                    new MessageFormat("Community Health Center - Patient Visit Records"),
                    new MessageFormat("Page {0}"));
        } catch (java.awt.print.PrinterException ex) {
            Ui.error(this, "Printing failed: " + ex.getMessage());
        }
    }
}
