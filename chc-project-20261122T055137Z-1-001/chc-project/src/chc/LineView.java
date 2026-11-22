package chc;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;

/**
 * "People in Line" picture: a Desk card followed by one card per person
 * (serving first, then waiting from front to rear), joined by short connector lines.
 * Put it inside a scroll pane: a long line scrolls sideways.
 */
public class LineView extends JComponent {
    private static final int CW = 170, CH = 200, GAP = 28, PAD = 12;
    private static final Color INK = new Color(0x111111);
    private static final Color LINK = new Color(0x7F8FA0);
    private static final Color GOLD_TEXT = new Color(0xA86F00);
    private static final java.time.format.DateTimeFormatter TIME_ONLY =
            java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.ENGLISH);
    private static final java.time.format.DateTimeFormatter DATE_TIME =
            java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a", java.util.Locale.ENGLISH);

    private final boolean publicStyle;
    private List<Visit> visits = new ArrayList<Visit>();
    private boolean names;
    private String caption = "";

    /** @param publicStyle true = teal / amber colors of the public screen, false = blue / yellow of the staff screens */
    public LineView(boolean publicStyle) {
        this.publicStyle = publicStyle;
        setOpaque(true);
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(PAD * 2 + CW * 2 + GAP, CH + 50));
    }

    public void setVisits(List<Visit> line, boolean showNames, String caption) {
        this.visits = new ArrayList<Visit>(line);
        this.names = showNames;
        this.caption = caption == null ? "" : caption;
        int w = PAD * 2 + (visits.size() + 1) * CW + visits.size() * GAP;
        setPreferredSize(new Dimension(w, CH + 50));
        revalidate();
        repaint();
    }

    private Color colorOf(Visit v) {
        if (v.getStatus() == Status.SERVING) return publicStyle ? new Color(0x1B6F74) : new Color(0x2370D8);
        return publicStyle ? new Color(0xD1962E) : Status.WAITING.color;
    }

    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());

        int top = Math.max(8, (getHeight() - (CH + 28)) / 2);
        int x = PAD;
        drawDesk(g, x, top);
        boolean nextDone = false;
        int pos = 1;
        for (Visit v : visits) {
            int nx = x + CW + GAP;
            g.setColor(LINK);
            g.setStroke(new BasicStroke(3f));
            g.drawLine(x + CW, top + 50, nx, top + 50);
            boolean isNext = !nextDone && v.getStatus() == Status.WAITING;
            if (isNext) nextDone = true;
            drawCard(g, nx, top, pos++, v, isNext);
            x = nx;
        }
        if (!caption.isEmpty()) {
            g.setFont(Ui.font(Font.BOLD, 12));
            g.setColor(Ui.MUTED);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(caption, getWidth() - 12 - fm.stringWidth(caption), getHeight() - 8);
        }
        g.dispose();
    }

    private void drawDesk(Graphics2D g, int x, int top) {
        g.setColor(Color.WHITE);
        g.fillRect(x, top, CW, CH);
        g.setColor(INK);
        g.setStroke(new BasicStroke(2f));
        g.drawRect(x + 1, top + 1, CW - 2, CH - 2);
        int cx = x + CW / 2;
        g.setColor(Ui.NAVY);
        g.fillOval(cx - 10, top + 14, 20, 20);
        g.fillRoundRect(cx - 20, top + 38, 40, 34, 10, 10);
        g.setColor(new Color(0x5F6F7C));
        g.fillRect(cx - 40, top + 72, 80, 18);
        g.setColor(Ui.NAVY);
        g.setFont(Ui.font(Font.BOLD, 24));
        Ui.centerText(g, "Desk", cx, top + 126);
        g.setColor(Ui.TEXT);
        g.setFont(Ui.font(Font.PLAIN, 13));
        Ui.centerText(g, "GET YOUR QUEUE NO.", cx, top + 170);
    }

    private void drawCard(Graphics2D g, int x, int top, int pos, Visit v, boolean isNext) {
        Color c = colorOf(v);
        g.setColor(Color.WHITE);
        g.fillRect(x, top, CW, CH);
        g.setColor(c);
        g.setStroke(new BasicStroke(2f));
        g.drawRect(x + 1, top + 1, CW - 2, CH - 2);
        int cx = x + CW / 2;
        g.fillOval(cx - 10, top + 14, 20, 20);
        g.fillRoundRect(cx - 20, top + 38, 40, 58, 18, 18);

        g.setColor(Ui.NAVY);
        g.setFont(Ui.font(Font.BOLD, 18));
        Ui.centerText(g, pos + ".  " + v.getQueueNo(), cx, top + (names ? 120 : 128));
        if (names) {
            g.setColor(Ui.TEXT);
            g.setFont(Ui.font(Font.BOLD, 12));
            Ui.centerText(g, fit(g, v.getFullName(), CW - 16), cx, top + 150);
        }
        String label = v.getStatus() == Status.SERVING ? "SERVING NOW" : isNext ? "NEXT TO SERVE" : "WAITING";
        g.setColor(v.getStatus() == Status.SERVING ? c : GOLD_TEXT);
        g.setFont(Ui.font(Font.BOLD, 12));
        Ui.centerText(g, label, cx, top + (names ? 181 : 172));

        // check-in time under the card (date too when it was not today)
        if (v.getCreatedAt() != null) {
            boolean today = v.getCreatedAt().toLocalDate().equals(java.time.LocalDate.now());
            String when = v.getCreatedAt().format(today ? TIME_ONLY : DATE_TIME);
            g.setColor(Ui.MUTED);
            g.setFont(Ui.font(Font.BOLD, 12));
            Ui.centerText(g, "Checked in " + when, cx, top + CH + 20);
        }
    }

    private static String fit(Graphics2D g, String s, int width) {
        FontMetrics fm = g.getFontMetrics();
        if (fm.stringWidth(s) <= width) return s;
        while (s.length() > 1 && fm.stringWidth(s + "...") > width) s = s.substring(0, s.length() - 1);
        return s + "...";
    }
}
