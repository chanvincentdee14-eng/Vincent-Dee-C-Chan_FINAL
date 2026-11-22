package chc;

import javax.swing.JPanel;

/**
 * Base class of every screen in the main window (OOP: abstraction + polymorphism).
 * The main window only knows "a ScreenPanel": it opens and refreshes any screen
 * the same way, and each screen decides for itself what that means.
 */
public abstract class ScreenPanel extends JPanel {
    private final String title;

    protected ScreenPanel(String title) { this.title = title; }

    /** The name shown in the side menu. */
    public String getTitle() { return title; }

    /** Reads the latest data and redraws the screen. */
    public abstract void refresh();

    /** Called every time the screen is opened. Screens may override it. */
    public void onShow() { refresh(); }
}
