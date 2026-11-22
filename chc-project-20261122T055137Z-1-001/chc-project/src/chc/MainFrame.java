package chc;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;

/** Main window for Admin and Staff: header, sidebar and the screens. */
public class MainFrame extends JFrame {
    private final QueueService service;
    private final User user;
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final Map<String, Ui.FlatButton> nav = new LinkedHashMap<String, Ui.FlatButton>();
    private final JLabel statusLabel = new JLabel();
    private final JLabel dbLabel = new JLabel();

    private final UserRepository users;
    /** Every screen the signed-in user may open, in menu order. */
    private final Map<String, ScreenPanel> screens = new LinkedHashMap<String, ScreenPanel>();
    private final Timer poll;
    private final Runnable queueListener = this::refreshVisible;
    private String current = "Dashboard";
    private boolean online = true;

    public MainFrame(QueueService service, UserRepository users, User user) {
        super("Community Health Center - Queue Management System");
        this.service = service;
        this.user = user;
        this.users = users;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        getContentPane().setBackground(Ui.BG);
        setLayout(new BorderLayout());

        // ---------- header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Color.WHITE);
        header.setBorder(BorderFactory.createEmptyBorder(14, 18, 12, 18));
        header.add(Ui.label("Community Health Center", Font.BOLD, 24, Ui.NAVY), BorderLayout.WEST);
        JPanel right = Ui.flow(FlowLayout.RIGHT, 12, 0);
        right.add(Ui.label(user.getRoleLabel(), Font.BOLD, 12, Ui.TEXT));
        Ui.FlatButton pub = Ui.button("OPEN PUBLIC DISPLAY", Ui.TEAL);
        JButton out = Ui.plainButton("Log Out");
        out.setPreferredSize(new Dimension(78, 28));
        right.add(pub); right.add(out);
        header.add(right, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // ---------- screens
        content.setBackground(Ui.BG);
        for (String name : user.getScreens()) screens.put(name, createScreen(name));
        for (Map.Entry<String, ScreenPanel> e : screens.entrySet()) content.add(e.getValue(), e.getKey());
        add(content, BorderLayout.CENTER);

        // ---------- sidebar
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBackground(Ui.SIDEBAR);
        side.setBorder(BorderFactory.createEmptyBorder(14, 12, 10, 12));
        side.setPreferredSize(new Dimension(206, 100));
        for (final String n : screens.keySet()) {
            Ui.FlatButton b = Ui.button(n, Ui.SIDEBAR, Ui.TEXT);
            b.setArc(0);
            b.setFont(Ui.font(Font.BOLD, 14));
            b.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
            b.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
            b.setMaximumSize(new Dimension(1000, 45));
            b.setAlignmentX(0f);
            b.addActionListener(e -> show(n));
            nav.put(n, b);
            side.add(b);
            side.add(Box.createVerticalStrut(10));
        }
        add(side, BorderLayout.WEST);

        // ---------- status bar
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Color.WHITE);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Ui.BORDER), BorderFactory.createEmptyBorder(6, 14, 6, 14)));
        statusLabel.setFont(Ui.font(Font.BOLD, 12));
        statusLabel.setForeground(Ui.TEAL);
        dbLabel.setFont(Ui.font(Font.PLAIN, 12));
        bar.add(statusLabel, BorderLayout.WEST);
        bar.add(dbLabel, BorderLayout.EAST);
        add(bar, BorderLayout.SOUTH);
        setStatus("Welcome. Your changes are saved automatically.");
        updateDbLabel();

        // ---------- behaviour
        pub.addActionListener(e -> new PublicDisplayFrame(service).setVisible(true));
        out.addActionListener(e -> logout());
        service.addListener(queueListener);
        poll = new Timer(3000, e -> pollDatabase());
        poll.start();
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) { poll.stop(); }
        });

        show("Dashboard");
        setMinimumSize(new Dimension(1000, 640));
        setSize(1280, 800);
        setLocationRelativeTo(null);
        if (!GraphicsEnvironment.isHeadless()) setExtendedState(JFrame.MAXIMIZED_BOTH);
    }

    private void show(String name) {
        current = name;
        cards.show(content, name);
        for (Map.Entry<String, Ui.FlatButton> e : nav.entrySet()) {
            boolean on = e.getKey().equals(name);
            e.getValue().setColors(on ? Ui.NAVY : Ui.SIDEBAR, on ? Color.WHITE : Ui.TEXT);
        }
        screens.get(name).onShow();
    }

    /** Builds one screen by its menu name. */
    private ScreenPanel createScreen(String name) {
        switch (name) {
            case "Dashboard": return new DashboardPanel(service);
            case "Patients": return new PatientsPanel(service, user, this::setStatus);
            case "Queue Simulation": return new SimulationPanel(service, this::setStatus);
            case "Records": return new RecordsPanel(service, this::setStatus);
            case "Administrator": return new AdminPanel(users, user, this::setStatus);
            default: throw new IllegalArgumentException("Unknown screen: " + name);
        }
    }

    /** Re-draws the open screen: the same call works for every kind of screen (polymorphism). */
    private void refreshVisible() {
        ScreenPanel open = screens.get(current);
        if (open != null) open.refresh();
    }

    public void setStatus(String message) { statusLabel.setText(message); }

    private void updateDbLabel() {
        dbLabel.setText(online ? "● Database connected  (" + Db.describe() + ")"
                : "● Database OFFLINE - start MySQL in XAMPP");
        dbLabel.setForeground(online ? Status.COMPLETED.color.darker() : Ui.RED);
    }

    /** Every few seconds: pick up changes made from another computer and notice a lost connection. */
    private void pollDatabase() {
        try {
            boolean changed = service.reload();
            if (!online) { online = true; updateDbLabel(); }
            if (changed) refreshVisible();
        } catch (ServiceException ex) {
            if (online) { online = false; updateDbLabel(); }
        }
    }

    private void logout() {
        poll.stop();
        service.removeListener(queueListener);
        dispose();
        new LoginFrame(service, users).setVisible(true);
    }
}
