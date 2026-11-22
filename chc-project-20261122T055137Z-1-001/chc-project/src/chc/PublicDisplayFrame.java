package chc;

import java.awt.Dimension;
import javax.swing.JFrame;

/** The public live-queue window. No login needed and no personal details are shown: only queue numbers. */
public class PublicDisplayFrame extends JFrame {
    public PublicDisplayFrame(QueueService service) {
        super("Health Center - Public Queue Display");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setContentPane(new LiveQueuePanel(service, true, null));
        setSize(1180, 700);
        setMinimumSize(new Dimension(900, 560));
        setLocationRelativeTo(null);
    }
}
