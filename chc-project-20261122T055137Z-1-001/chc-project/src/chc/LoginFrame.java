package chc;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

/** Sign-in window for Admin and Staff. The public queue view needs no login. */
public class LoginFrame extends JFrame {
    private final QueueService service;
    private final UserRepository users;
    private final JTextField userField = new JTextField();
    private final JPasswordField passField = new JPasswordField();
    private final JLabel error = new JLabel(" ", SwingConstants.CENTER);
    private final JPanel loginRoot = new JPanel(new GridBagLayout());

    public LoginFrame(QueueService service, UserRepository users) {
        super("Community Health Center - Queue Management System");
        this.service = service;
        this.users = users;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        loginRoot.setBackground(Ui.BG);
        setContentPane(loginRoot);

        JPanel card = Ui.card();
        card.setLayout(new GridBagLayout());
        card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Ui.BORDER),
                BorderFactory.createEmptyBorder(26, 34, 24, 34)));
        card.setPreferredSize(new Dimension(420, 430));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1; c.insets = new Insets(3, 0, 3, 0);

        JLabel title = Ui.label("Community Health Center", Font.BOLD, 24, Ui.NAVY);
        title.setHorizontalAlignment(SwingConstants.CENTER);
        c.gridy = 0; card.add(title, c);
        JLabel sub = Ui.label("Queue Management System", Font.PLAIN, 15, Ui.MUTED);
        sub.setHorizontalAlignment(SwingConstants.CENTER);
        c.gridy = 1; c.insets = new Insets(0, 0, 20, 0); card.add(sub, c);

        c.insets = new Insets(3, 0, 3, 0);
        c.gridy = 2; card.add(Ui.label("Username", Font.BOLD, 12, Ui.TEXT), c);
        userField.setPreferredSize(new Dimension(100, 34));
        c.gridy = 3; card.add(userField, c);
        c.gridy = 4; c.insets = new Insets(10, 0, 3, 0); card.add(Ui.label("Password", Font.BOLD, 12, Ui.TEXT), c);
        passField.setPreferredSize(new Dimension(100, 34));
        c.gridy = 5; c.insets = new Insets(3, 0, 3, 0); card.add(passField, c);

        error.setForeground(Ui.RED);
        error.setFont(Ui.font(Font.BOLD, 12));
        c.gridy = 6; c.insets = new Insets(8, 0, 4, 0); card.add(error, c);

        Ui.FlatButton login = Ui.button("Log In", Ui.NAVY);
        login.setPreferredSize(new Dimension(100, 38));
        c.gridy = 7; c.insets = new Insets(4, 0, 4, 0); card.add(login, c);

        Ui.FlatButton pub = Ui.button("View Live Queue (no login)", Ui.TEAL);
        pub.setPreferredSize(new Dimension(100, 38));
        c.gridy = 8; card.add(pub, c);

        JLabel db = Ui.label("Database: " + Db.describe(), Font.PLAIN, 11, Ui.MUTED);
        db.setHorizontalAlignment(SwingConstants.CENTER);
        c.gridy = 9; c.insets = new Insets(14, 0, 0, 0); card.add(db, c);

        loginRoot.add(card);

        login.addActionListener(e -> doLogin());
        passField.addActionListener(e -> doLogin());
        userField.addActionListener(e -> passField.requestFocusInWindow());
        pub.addActionListener(e -> {
            try { service.reload(); } catch (ServiceException ex) { Ui.error(this, ex.getMessage()); return; }
            // show the live queue (Patient / Doctor View) in this window, with a Back button
            setContentPane(new LiveQueuePanel(service, false, () -> {
                setContentPane(loginRoot);
                revalidate();
                repaint();
            }));
            revalidate();
            repaint();
        });

        setSize(560, 560);
        setMinimumSize(new Dimension(480, 520));
        setLocationRelativeTo(null);
    }

    private void doLogin() {
        String u = userField.getText().trim();
        String p = new String(passField.getPassword());
        if (u.isEmpty() || p.isEmpty()) { error.setText("Enter your username and password."); return; }
        try {
            User user = users.authenticate(u, p);
            if (user == null) {
                error.setText("Wrong username or password.");
                passField.setText("");
                passField.requestFocusInWindow();
                return;
            }
            service.reload();
            dispose();
            new MainFrame(service, users, user).setVisible(true);
        } catch (ServiceException ex) {
            error.setText(" ");
            Ui.error(this, ex.getMessage());
        }
    }
}
