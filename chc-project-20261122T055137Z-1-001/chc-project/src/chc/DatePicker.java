package chc;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.Month;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicArrowButton;

/** A date field with a calendar: click it, pick the year and month, then the day. */
public class DatePicker extends JPanel {
    private static final String PROMPT = "Select a date...";
    private static final String[] DOW = {"Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"};

    private LocalDate value;
    private final JLabel text = new JLabel(PROMPT);

    public DatePicker() {
        super(new BorderLayout());
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createLineBorder(new Color(0x7A8A99)));
        text.setFont(Ui.font(Font.PLAIN, 13));
        text.setForeground(Ui.MUTED);
        text.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
        add(text, BorderLayout.CENTER);
        BasicArrowButton arrow = new BasicArrowButton(SwingConstants.SOUTH);
        arrow.setPreferredSize(new Dimension(28, 26));
        add(arrow, BorderLayout.EAST);
        arrow.addActionListener(e -> open());
        text.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { open(); }
        });
    }

    public LocalDate getValue() { return value; }

    public void setValue(LocalDate d) {
        value = d;
        text.setText(d == null ? PROMPT : d.toString());
        text.setForeground(d == null ? Ui.MUTED : Ui.TEXT);
    }

    private void open() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        Picker p = new Picker(owner);
        try {
            Point at = getLocationOnScreen();
            p.setLocation(at.x, at.y + getHeight());
        } catch (java.awt.IllegalComponentStateException ignored) {
            p.setLocationRelativeTo(this);
        }
        p.setVisible(true);
    }

    /** The calendar window. */
    private class Picker extends JDialog {
        private YearMonth shown;
        private final JPanel grid = new JPanel(new GridLayout(0, 7, 3, 3));
        private final JComboBox<String> monthBox = new JComboBox<String>();
        private final JComboBox<Integer> yearBox = new JComboBox<Integer>();
        private boolean updating;

        Picker(Window owner) {
            super(owner, "Select a date", Dialog.ModalityType.APPLICATION_MODAL);
            shown = value != null ? YearMonth.from(value) : YearMonth.now();
            for (Month m : Month.values()) monthBox.addItem(m.getDisplayName(TextStyle.FULL, Locale.ENGLISH));
            for (int y = Year.now().getValue(); y >= 1900; y--) yearBox.addItem(y);

            JButton prev = new JButton("<");
            JButton next = new JButton(">");
            prev.setMargin(new Insets(2, 8, 2, 8));
            next.setMargin(new Insets(2, 8, 2, 8));
            prev.addActionListener(e -> move(-1));
            next.addActionListener(e -> move(1));
            monthBox.addActionListener(e -> {
                if (!updating) { shown = YearMonth.of(shown.getYear(), monthBox.getSelectedIndex() + 1); rebuild(); }
            });
            yearBox.addActionListener(e -> {
                if (!updating && yearBox.getSelectedItem() != null) {
                    shown = YearMonth.of((Integer) yearBox.getSelectedItem(), shown.getMonthValue());
                    rebuild();
                }
            });

            JPanel top = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 2));
            top.add(prev); top.add(monthBox); top.add(yearBox); top.add(next);
            JButton clear = new JButton("Clear");
            JButton cancel = new JButton("Cancel");
            clear.addActionListener(e -> { setValue(null); dispose(); });
            cancel.addActionListener(e -> dispose());
            JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            bottom.add(clear); bottom.add(cancel);

            JPanel box = new JPanel(new BorderLayout(0, 6));
            box.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            box.add(top, BorderLayout.NORTH);
            box.add(grid, BorderLayout.CENTER);
            box.add(bottom, BorderLayout.SOUTH);
            setContentPane(box);
            setResizable(false);
            rebuild();
        }

        private void move(int months) {
            YearMonth t = shown.plusMonths(months);
            if (t.isAfter(YearMonth.now()) || t.getYear() < 1900) return;
            shown = t;
            rebuild();
        }

        private void rebuild() {
            updating = true;
            monthBox.setSelectedIndex(shown.getMonthValue() - 1);
            yearBox.setSelectedItem(shown.getYear());
            updating = false;
            grid.removeAll();
            for (String d : DOW) {
                JLabel l = new JLabel(d, SwingConstants.CENTER);
                l.setFont(Ui.font(Font.BOLD, 11));
                grid.add(l);
            }
            int lead = shown.atDay(1).getDayOfWeek().getValue() % 7; // Sunday = 0
            for (int i = 0; i < lead; i++) grid.add(new JLabel(""));
            LocalDate today = LocalDate.now();
            for (int d = 1; d <= shown.lengthOfMonth(); d++) {
                final LocalDate date = shown.atDay(d);
                JButton b = new JButton(String.valueOf(d));
                b.setMargin(new Insets(2, 0, 2, 0));
                b.setFocusPainted(false);
                b.setPreferredSize(new Dimension(36, 28));
                b.setEnabled(!date.isAfter(today));
                if (date.equals(value)) { b.setBackground(Ui.NAVY); b.setForeground(Color.WHITE); }
                b.addActionListener(e -> { setValue(date); dispose(); });
                grid.add(b);
            }
            grid.revalidate();
            grid.repaint();
            pack();
        }
    }
}
