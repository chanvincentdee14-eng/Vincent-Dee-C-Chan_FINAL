package chc;

import java.sql.SQLException;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/** Community Health Center Queue Management System - start here. */
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            startWithDatabase();
        });
    }

    private static void startWithDatabase() {
        while (true) {
            try {
                Db.init();
                break;
            } catch (SQLException e) {
                Object[] options = {"Try again", "Exit"};
                int choice = JOptionPane.showOptionDialog(null,
                        "The program could not connect to the database.\n\n"
                                + "1. Open the XAMPP Control Panel\n"
                                + "2. Click Start next to MySQL (it must turn green)\n"
                                + "3. Press \"Try again\"\n\n"
                                + "Database: " + Db.describe() + "\nDetails: " + e.getMessage(),
                        "Database not available", JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE,
                        null, options, options[0]);
                if (choice != 0) System.exit(1);
            }
        }
        QueueService service = new QueueService();
        try {
            service.reload();
        } catch (ServiceException e) {
            JOptionPane.showMessageDialog(null, e.getMessage());
        }
        new LoginFrame(service, new UserDao()).setVisible(true);
    }
}
