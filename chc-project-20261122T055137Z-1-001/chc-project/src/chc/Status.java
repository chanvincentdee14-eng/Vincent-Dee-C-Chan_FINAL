package chc;

import java.awt.Color;

/** Visit status with its color code (serving = blue, waiting = yellow, removed = red, completed = green). */
public enum Status {
    WAITING  ("Waiting",   new Color(0xF2B705), new Color(0xFFF3BF), new Color(0x7A5A00)),
    SERVING  ("Serving",   new Color(0x1F6FD6), new Color(0xD6E7FF), new Color(0x14498F)),
    ON_HOLD  ("On Hold",   new Color(0x7C8794), new Color(0xE9ECF0), new Color(0x4A5561)),
    COMPLETED("Completed", new Color(0x1E9E57), new Color(0xD5F2E1), new Color(0x146B3A)),
    DEQUEUED ("Dequeued",  new Color(0xD93A3A), new Color(0xFFDADA), new Color(0x9B1C1C)),
    DELETED  ("Deleted",   new Color(0xD93A3A), new Color(0xFFDADA), new Color(0x9B1C1C));

    public final String label;
    /** Strong color (tags, badges, numbers). */
    public final Color color;
    /** Light tint (row / card background). */
    public final Color tint;
    /** Dark readable text color on the tint. */
    public final Color dark;

    Status(String label, Color color, Color tint, Color dark) {
        this.label = label; this.color = color; this.tint = tint; this.dark = dark;
    }

    /** Still part of today's line (waiting, serving or on hold). */
    public boolean isActive() { return this == WAITING || this == SERVING || this == ON_HOLD; }

    /** Removed from the line without a consultation (red). */
    public boolean isRemoved() { return this == DEQUEUED || this == DELETED; }

    @Override public String toString() { return label; }
}
