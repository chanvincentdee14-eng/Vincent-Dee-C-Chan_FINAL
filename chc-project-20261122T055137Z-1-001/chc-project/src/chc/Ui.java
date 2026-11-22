package chc;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

/** Colors, fonts and small helpers shared by all screens. */
public final class Ui {
    private Ui() { }

    public static final Color NAVY = new Color(0x1F3A52);
    public static final Color BG = new Color(0xF1F5F8);
    public static final Color SIDEBAR = new Color(0xE8EFF4);
    public static final Color BORDER = new Color(0xD3DDE5);
    public static final Color TEXT = new Color(0x1F2D3A);
    public static final Color MUTED = new Color(0x5F6F7C);
    public static final Color TEAL = new Color(0x1E6F78);
    public static final Color RED = new Color(0xD93A3A);
    public static final Color AMBER = new Color(0xA86F1E);

    public static final Font BASE = new Font("SansSerif", Font.PLAIN, 13);
    public static final Font BOLD = new Font("SansSerif", Font.BOLD, 13);

    /** Date and time as shown in the tables, e.g. "Nov 21, 2026  08:18 PM". */
    public static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("MMM dd, yyyy  hh:mm a", Locale.ENGLISH);

    public static Font font(int style, float size) { return BASE.deriveFont(style, size); }

    public static JLabel label(String text, int style, float size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font(style, size));
        l.setForeground(color);
        return l;
    }

    public static JPanel card() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), BorderFactory.createEmptyBorder(12, 16, 12, 16)));
        return p;
    }

    public static JPanel flow(int align, int hgap, int vgap) {
        JPanel p = new JPanel(new FlowLayout(align, hgap, vgap));
        p.setOpaque(false);
        return p;
    }

    /** White strip with a big title on the left and an optional component on the right. */
    public static JPanel titleBar(String title, JComponent east) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        p.add(label(title, Font.BOLD, 24, NAVY), BorderLayout.WEST);
        if (east != null) p.add(east, BorderLayout.EAST);
        return p;
    }

    /** A plain grey button (the standard Java look), used for the small secondary actions. */
    public static JButton plainButton(String text) {
        JButton b = new JButton(text);
        b.setFont(font(Font.BOLD, 12));
        b.setFocusPainted(false);
        return b;
    }

    public static void error(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Please check", JOptionPane.WARNING_MESSAGE);
    }

    public static void info(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Community Health Center", JOptionPane.INFORMATION_MESSAGE);
    }

    public static boolean confirm(Component parent, String message) {
        return JOptionPane.showConfirmDialog(parent, message, "Please confirm",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    /** A flat button that looks the same on every platform. */
    public static class FlatButton extends JButton {
        private Color base;
        private Color textColor;
        private boolean hover;
        private int arc = 4;

        public FlatButton(String text, Color base, Color textColor) {
            super(text);
            this.base = base; this.textColor = textColor;
            setFont(BOLD);
            setForeground(textColor);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        public void setBase(Color c) { base = c; repaint(); }

        public void setArc(int a) { arc = a; repaint(); }

        public void setColors(Color bg, Color fg) { base = bg; textColor = fg; setForeground(fg); repaint(); }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color c = base;
            if (isEnabled() && getModel().isPressed()) c = c.darker().darker();
            else if (isEnabled() && hover) c = c.darker();
            g.setColor(c);
            g.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
            g.dispose();
            // a disabled button keeps its color but its text fades
            setForeground(isEnabled() ? textColor : blend(textColor, base, 0.6f));
            super.paintComponent(g0);
        }
    }

    public static FlatButton button(String text, Color bg) { return new FlatButton(text, bg, Color.WHITE); }

    public static FlatButton button(String text, Color bg, Color fg) { return new FlatButton(text, bg, fg); }

    /** A small colored square with a caption, used for the color legend. */
    public static JComponent legendChip(final Status s, String text) {
        final JLabel l = new JLabel("  " + text);
        l.setFont(font(Font.PLAIN, 12));
        l.setForeground(TEXT);
        l.setIcon(new javax.swing.Icon() {
            public int getIconWidth() { return 14; }
            public int getIconHeight() { return 14; }
            public void paintIcon(Component c, Graphics g0, int x, int y) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(s.color);
                g.fillRoundRect(x, y, 14, 14, 5, 5);
                g.dispose();
            }
        });
        return l;
    }

    // ---------------------------------------------------------------- tables

    /** Standard grid table: grey header, thin grid lines, light-blue selection. */
    public static void styleTable(JTable t) {
        t.setRowHeight(30);
        t.setFont(font(Font.PLAIN, 13));
        t.setForeground(TEXT);
        t.setGridColor(new Color(0x7F8A94));
        t.setShowGrid(true);
        t.setIntercellSpacing(new Dimension(1, 1));
        t.setSelectionBackground(new Color(0xBBD6F5));
        t.setSelectionForeground(TEXT);
        t.setFillsViewportHeight(true);
        t.setAutoCreateRowSorter(false);
        t.getTableHeader().setReorderingAllowed(false);
        t.getTableHeader().setFont(font(Font.BOLD, 12));
    }

    /** The status text a person sees: the first waiting patient is "Next to Serve". */
    public static String displayStatus(Visit v, Visit next) {
        if (v.getStatus() == Status.WAITING && next != null && next.getId() == v.getId()) return "Next to Serve";
        return v.getStatus().label;
    }

    /** Maps a status text from a table cell back to its Status. */
    public static Status statusFor(String text) {
        if ("Next to Serve".equals(text)) return Status.WAITING;
        for (Status s : Status.values()) if (s.label.equalsIgnoreCase(text)) return s;
        return null;
    }

    /** Fills a status cell with the status color (blue, yellow, grey, green or red) and bold text. */
    public static class StatusCellRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                                  boolean focus, int row, int col) {
            JLabel l = (JLabel) super.getTableCellRendererComponent(table, value, false, false, row, col);
            Status s = statusFor(String.valueOf(value));
            l.setFont(BOLD.deriveFont(12f));
            if (s != null) {
                l.setOpaque(true);
                l.setBackground(s.color);
                l.setForeground(s == Status.WAITING ? Color.BLACK : Color.WHITE);
            }
            return l;
        }
    }

    public static Color blend(Color a, Color b, float t) {
        float u = 1f - t;
        return new Color((int) (a.getRed() * u + b.getRed() * t), (int) (a.getGreen() * u + b.getGreen() * t),
                (int) (a.getBlue() * u + b.getBlue() * t));
    }

    /** Draws text centered at (cx, baseline y). */
    public static void centerText(Graphics2D g, String s, double cx, double y) {
        int w = g.getFontMetrics().stringWidth(s);
        g.drawString(s, (float) (cx - w / 2.0), (float) y);
    }

    public static BasicStroke stroke(float w) {
        return new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    }
}
