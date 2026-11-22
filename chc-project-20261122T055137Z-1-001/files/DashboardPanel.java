package chc;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Dashboard: color-coded counters (waiting = yellow, serving = blue, completed = green, removed = red). */
public class DashboardPanel extends ScreenPanel {
    private final QueueService service;
    private final JLabel waiting = new JLabel("0"), serving = new JLabel("0"), completed = new JLabel("0"),
            removed = new JLabel("0"), visits = new JLabel("0");
    private final JLabel nowServing = new JLabel(), nextServe = new JLabel(), holdInfo = new JLabel(), totals = new JLabel();

    public DashboardPanel(QueueService service) {
        super("Dashboard");
        this.service = service;
        setLayout(new BorderLayout(0, 14));
        setBackground(Ui.BG);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 10, 16));

        add(Ui.label("Dashboard Overview", Font.BOLD, 22, Ui.NAVY), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 14));
        center.setOpaque(false);

        JPanel cards = new JPanel(new GridLayout(1, 5, 12, 0));
        cards.setOpaque(false);
        cards.setPreferredSize(new Dimension(100, 96));
        cards.add(statCard("WAITING (incl. NEXT)", waiting, Status.WAITING));
        cards.add(statCard("SERVING", serving, Status.SERVING));
        cards.add(statCard("COMPLETED TODAY", completed, Status.COMPLETED));
        cards.add(statCard("REMOVED TODAY", removed, Status.DEQUEUED));
        cards.add(statCard("VISITS TODAY", visits, null));
        center.add(cards, BorderLayout.NORTH);

        JPanel big = Ui.card();
        big.setLayout(new BoxLayout(big, BoxLayout.Y_AXIS));
        big.add(left(Ui.label("NOW SERVING", Font.BOLD, 13, Ui.NAVY)));
        big.add(Box.createVerticalStrut(18));
        nowServing.setFont(Ui.font(Font.BOLD, 30));
        big.add(left(nowServing));
        big.add(Box.createVerticalStrut(34));
        big.add(left(Ui.label("NEXT TO SERVE", Font.BOLD, 13, Ui.NAVY)));
        big.add(Box.createVerticalStrut(18));
        nextServe.setFont(Ui.font(Font.BOLD, 26));
        big.add(left(nextServe));
        big.add(Box.createVerticalStrut(24));
        holdInfo.setFont(Ui.font(Font.PLAIN, 14));
        holdInfo.setForeground(Status.ON_HOLD.dark);
        big.add(left(holdInfo));
        big.add(Box.createVerticalGlue());
        JPanel legend = Ui.flow(java.awt.FlowLayout.LEFT, 16, 0);
        legend.setAlignmentX(0f);
        legend.add(Ui.label("Color code:", Font.BOLD, 12, Ui.TEXT));
        legend.add(Ui.legendChip(Status.SERVING, "Serving"));
        legend.add(Ui.legendChip(Status.WAITING, "Next to Serve / Waiting"));
        legend.add(Ui.legendChip(Status.ON_HOLD, "On Hold"));
        legend.add(Ui.legendChip(Status.COMPLETED, "Completed"));
        legend.add(Ui.legendChip(Status.DEQUEUED, "Removed / Deleted"));
        big.add(legend);
        center.add(big, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        totals.setFont(Ui.font(Font.PLAIN, 12));
        totals.setForeground(Ui.MUTED);
        add(totals, BorderLayout.SOUTH);
        refresh();
    }

    private static JLabel left(JLabel l) { l.setAlignmentX(0f); return l; }

    private JPanel statCard(String title, JLabel number, Status s) {
        Color strong = s == null ? Ui.NAVY : s.color.darker();
        Color tint = s == null ? Color.WHITE : s.tint;
        Color bar = s == null ? Ui.NAVY : s.color;
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(tint);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(5, 0, 0, 0, bar),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        JLabel t = Ui.label(title, Font.BOLD, 12, s == null ? Ui.NAVY : s.dark);
        t.setHorizontalAlignment(JLabel.CENTER);
        number.setFont(Ui.font(Font.BOLD, 36));
        number.setForeground(strong);
        number.setHorizontalAlignment(JLabel.CENTER);
        p.add(t, BorderLayout.NORTH);
        p.add(number, BorderLayout.CENTER);
        return p;
    }

    @Override public void refresh() {
        QueueService.Stats s = service.getStats();
        waiting.setText(String.valueOf(s.getWaiting()));
        serving.setText(String.valueOf(s.getServing()));
        completed.setText(String.valueOf(s.getCompletedToday()));
        removed.setText(String.valueOf(s.getRemovedToday()));
        visits.setText(String.valueOf(s.getVisitsToday()));
        Visit cur = service.getServing();
        Visit next = service.peekNext();
        nowServing.setText(cur == null ? "No patient serving" : cur.getQueueNo() + "  -  " + cur.getFullName());
        nowServing.setForeground(Status.SERVING.color.darker());
        nextServe.setText(next == null ? "No patient waiting" : next.getQueueNo() + "  -  " + next.getFullName());
        nextServe.setForeground(new Color(0xA86F00));
        holdInfo.setText(s.getOnHold() == 0 ? " " : "On hold: " + s.getOnHold() + (s.getOnHold() == 1 ? " patient" : " patients"));
        totals.setText("All-time visits: " + s.getAllVisits() + "   |   All-time completed: " + s.getAllCompleted());
    }
}
