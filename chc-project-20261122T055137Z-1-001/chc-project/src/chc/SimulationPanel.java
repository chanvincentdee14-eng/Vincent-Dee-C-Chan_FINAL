package chc;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

/**
 * Queue Simulation: the real queue drawn as people in line (Desk, serving, waiting)
 * with the staff buttons (complete and call next, hold, call next, return) and a details table.
 */
public class SimulationPanel extends ScreenPanel {
    private final QueueService service;
    private final Consumer<String> status;
    private final LineView line = new LineView(false);

    private final Ui.FlatButton doneBtn = Ui.button("COMPLETE & CALL NEXT", Ui.NAVY);
    private final Ui.FlatButton holdBtn = Ui.button("PLACE ON HOLD", Ui.AMBER);
    private final Ui.FlatButton callBtn = Ui.button("CALL NEXT", Ui.TEAL);
    private final Ui.FlatButton returnBtn = Ui.button("RETURN SELECTED TO LINE", Ui.TEAL);
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"Queue No.", "Patient Name", "Position", "Status", "Checked In"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(model);
    private List<Visit> rows = new ArrayList<Visit>();

    public SimulationPanel(QueueService service, Consumer<String> status) {
        super("Queue Simulation");
        this.service = service;
        this.status = status;
        setLayout(new BorderLayout(0, 10));
        setBackground(Ui.BG);
        setBorder(BorderFactory.createEmptyBorder(14, 14, 8, 14));

        // ---- people in line + buttons
        JPanel top = Ui.card();
        top.setLayout(new BorderLayout(0, 8));
        top.add(Ui.label("People in Line - Queue Number Order", Font.BOLD, 16, Ui.NAVY), BorderLayout.NORTH);
        JScrollPane lineScroll = new JScrollPane(line, JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        lineScroll.setBorder(BorderFactory.createEmptyBorder());
        lineScroll.getViewport().setBackground(Color.WHITE);
        top.add(lineScroll, BorderLayout.CENTER);
        JPanel btns = new JPanel(new GridLayout(2, 2, 12, 6));
        btns.setOpaque(false);
        for (Ui.FlatButton b : new Ui.FlatButton[]{doneBtn, holdBtn, callBtn, returnBtn}) {
            b.setPreferredSize(new Dimension(100, 36));
            btns.add(b);
        }
        top.add(btns, BorderLayout.SOUTH);
        add(top, BorderLayout.CENTER);

        // ---- details table
        JPanel bottom = Ui.card();
        bottom.setLayout(new BorderLayout(0, 6));
        bottom.add(Ui.label("Queue Details", Font.BOLD, 16, Ui.NAVY), BorderLayout.NORTH);
        Ui.styleTable(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(3).setCellRenderer(new Ui.StatusCellRenderer());
        table.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) updateButtons(); });
        JScrollPane sp = new JScrollPane(table);
        sp.getViewport().setBackground(Color.WHITE);
        bottom.add(sp, BorderLayout.CENTER);
        bottom.add(Ui.label("Active line follows queue order. On Hold patients may rejoin at the end.",
                Font.BOLD, 12, Ui.TEXT), BorderLayout.SOUTH);
        bottom.setPreferredSize(new Dimension(100, 215));
        add(bottom, BorderLayout.SOUTH);

        callBtn.addActionListener(e -> run(() -> {
            Visit v = service.callNext();
            return "Now serving " + v.getQueueNo() + " - " + v.getFullName();
        }));
        doneBtn.addActionListener(e -> run(() -> {
            Visit cur = service.getServing();
            if (cur == null) throw new ServiceException("Nobody is being served right now.");
            Visit next = service.complete(cur.getId());
            return cur.getQueueNo() + " completed." + (next != null ? " Now serving " + next.getQueueNo() + "." : " No patient waiting.");
        }));
        holdBtn.addActionListener(e -> run(() -> {
            Visit v = selected();
            if (v == null) v = service.getServing();
            if (v == null) throw new ServiceException("Select a waiting or serving patient first.");
            Visit next = service.hold(v.getId());
            return v.getQueueNo() + " placed on hold." + (next != null ? " Now serving " + next.getQueueNo() + "." : "");
        }));
        returnBtn.addActionListener(e -> run(() -> {
            Visit v = selected();
            if (v == null || v.getStatus() != Status.ON_HOLD) {
                List<Visit> held = service.getOnHold();
                if (held.isEmpty()) throw new ServiceException("Nobody is on hold.");
                if (held.size() > 1) throw new ServiceException("Select the patient who is On Hold first.");
                v = held.get(0);
            }
            String old = v.getQueueNo();
            Visit back = service.returnToLine(v.getId());
            return old + " returned to the end of the line as " + (back != null ? back.getQueueNo() : "a new number") + ".";
        }));
        refresh();
    }

    private interface Action { String run() throws ServiceException; }

    private void run(Action a) {
        try {
            status.accept(a.run());
        } catch (ServiceException ex) {
            Ui.error(this, ex.getMessage());
        }
    }

    private Visit selected() {
        int r = table.getSelectedRow();
        return r < 0 || r >= rows.size() ? null : rows.get(r);
    }

    /** Called when the queue changes (and every few seconds). */
    @Override public void refresh() {
        Visit sel = selected();
        int selId = sel != null ? sel.getId() : -1;
        rows = service.getActive();
        Visit next = service.peekNext();
        model.setRowCount(0);
        int pos = 0;
        for (Visit v : rows) {
            boolean inLine = v.getStatus() == Status.SERVING || v.getStatus() == Status.WAITING;
            model.addRow(new Object[]{v.getQueueNo(), v.getFullName(), inLine ? String.valueOf(++pos) : "-",
                    Ui.displayStatus(v, next), v.getCreatedAt() == null ? "" : v.getCreatedAt().format(Ui.STAMP)});
        }
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).getId() == selId) { table.setRowSelectionInterval(i, i); break; }
        }
        List<Visit> inLine = new ArrayList<Visit>(service.getServingList());
        inLine.addAll(service.getWaiting());
        int held = service.getOnHold().size();
        line.setVisits(inLine, true, held == 0 ? "" : "On hold: " + held);
        updateButtons();
    }

    private void updateButtons() {
        Visit sel = selected();
        boolean serving = service.getServing() != null;
        callBtn.setEnabled(!serving && service.peekNext() != null);
        doneBtn.setEnabled(serving);
        holdBtn.setEnabled(sel != null ? (sel.getStatus() == Status.WAITING || sel.getStatus() == Status.SERVING) : serving);
        returnBtn.setEnabled(sel != null ? sel.getStatus() == Status.ON_HOLD : !service.getOnHold().isEmpty());
    }
}
