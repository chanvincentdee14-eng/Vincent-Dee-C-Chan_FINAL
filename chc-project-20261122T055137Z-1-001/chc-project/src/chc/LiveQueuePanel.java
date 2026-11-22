package chc;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.Timer;

/**
 * The live queue for patients and doctors: NOW SERVING, NEXT TO SERVE and the people in line.
 * Only queue numbers are shown - never names, contact details or addresses.
 *
 * display = true  : the full-screen Public Display window (centered title)
 * display = false : the "Patient / Doctor View" with a Back button
 */
public class LiveQueuePanel extends JPanel {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("hh:mm:ss a", Locale.ENGLISH);

    private final QueueService service;
    private final boolean display;
    private final JLabel nowServing = new JLabel("-");
    private final JLabel nextServe = new JLabel("-");
    private final JLabel updated = new JLabel(" ");
    private final LineView line;
    private final Timer poll;
    private final Runnable listener = () -> refresh();

    public LiveQueuePanel(QueueService service, boolean display, Runnable onBack) {
        this.service = service;
        this.display = display;
        this.line = new LineView(true);   // teal and amber, like the public screen
        setLayout(new BorderLayout(0, 10));
        setBackground(Ui.BG);
        setBorder(BorderFactory.createEmptyBorder(display ? 18 : 14, display ? 22 : 14, 10, display ? 22 : 14));

        if (display) {
            JLabel t = Ui.label("COMMUNITY HEALTH CENTER - LIVE QUEUE", Font.BOLD, 30, Ui.NAVY);
            t.setHorizontalAlignment(SwingConstants.CENTER);
            add(t, BorderLayout.NORTH);
        } else {
            JButton back = Ui.plainButton("Back");
            back.addActionListener(e -> { if (onBack != null) onBack.run(); });
            add(Ui.titleBar("Live Queue - Patient / Doctor View", back), BorderLayout.NORTH);
        }

        Color teal = new Color(0x1B6F74);
        JPanel mid = new JPanel(new GridBagLayout());
        mid.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.weightx = 1; c.fill = GridBagConstraints.BOTH; c.insets = new java.awt.Insets(0, 0, 12, 0);
        c.gridy = 0; mid.add(bigCard("NOW SERVING", nowServing, teal, display ? teal : Ui.NAVY), c);
        c.gridy = 1; mid.add(bigCard("NEXT TO SERVE", nextServe, Ui.NAVY, Ui.NAVY), c);

        JScrollPane sp = new JScrollPane(line, JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        sp.getViewport().setBackground(Color.WHITE);
        JLabel heading = Ui.label("People in Line - Queue Number Order", Font.BOLD, 18, Ui.NAVY);
        JPanel section;
        if (display) {
            section = Ui.card();
            section.setLayout(new BorderLayout(0, 8));
            section.add(heading, BorderLayout.NORTH);
            sp.setBorder(BorderFactory.createEmptyBorder());
            section.add(sp, BorderLayout.CENTER);
        } else {
            section = new JPanel(new BorderLayout());
            section.setOpaque(false);
            JPanel head = new JPanel(new BorderLayout());
            head.setBackground(Color.WHITE);
            head.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));
            head.add(heading, BorderLayout.WEST);
            section.add(head, BorderLayout.NORTH);
            sp.setBorder(BorderFactory.createLineBorder(Ui.BORDER));
            section.add(sp, BorderLayout.CENTER);
        }
        c.gridy = 2; c.weighty = 1; c.insets = new java.awt.Insets(0, 0, 0, 0);
        mid.add(section, c);
        add(mid, BorderLayout.CENTER);

        JPanel foot = new JPanel(new GridLayout(2, 1));
        foot.setOpaque(false);
        foot.add(Ui.label(display ? "Queue numbers only. Private patient information is never shown."
                : "Queue numbers only - names, contact details, and addresses are hidden.", Font.BOLD, 12, Ui.TEXT));
        updated.setFont(Ui.font(Font.BOLD, 12));
        updated.setForeground(display ? Ui.TEXT : Ui.TEAL);
        foot.add(updated);
        add(foot, BorderLayout.SOUTH);

        poll = new Timer(2500, e -> {
            try { service.reload(); } catch (ServiceException ignored) { /* the main window shows the offline state */ }
            refresh();
        });
        refresh();
    }

    private JPanel bigCard(String caption, JLabel number, Color capColor, Color numColor) {
        int al = display ? SwingConstants.CENTER : SwingConstants.LEFT;
        JPanel p = Ui.card();
        p.setLayout(new BorderLayout(0, 4));
        JLabel cap = Ui.label(caption, Font.BOLD, 16, capColor);
        cap.setHorizontalAlignment(al);
        number.setFont(Ui.font(Font.BOLD, display ? 52 : 34));
        number.setForeground(numColor);
        number.setHorizontalAlignment(al);
        p.add(cap, BorderLayout.NORTH);
        p.add(number, BorderLayout.CENTER);
        return p;
    }

    @Override public void addNotify() {
        super.addNotify();
        service.addListener(listener);
        poll.start();
        refresh();
    }

    @Override public void removeNotify() {
        poll.stop();
        service.removeListener(listener);
        super.removeNotify();
    }

    private void refresh() {
        Visit cur = service.getServing();
        Visit next = service.peekNext();
        nowServing.setText(cur == null ? "-" : cur.getQueueNo());
        nextServe.setText(next == null ? "-" : next.getQueueNo());
        List<Visit> inLine = new ArrayList<Visit>(service.getServingList());
        inLine.addAll(service.getWaiting());
        int held = service.getOnHold().size();
        line.setVisits(inLine, false, held == 0 ? "" : "On hold: " + held);
        updated.setText((display ? "Updated automatically at " : "Automatically updated at ")
                + LocalTime.now().format(TIME));
    }
}
