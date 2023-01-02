package drm.shell.ui;

import org.jetbrains.skija.Canvas;

public class RedrawHelper {
    private Canvas canvas;
    private boolean bothUpToDate;

    public boolean anyNeedsRedraw() {
        return !bothUpToDate;
    }

    public boolean needsRedraw(Canvas canvas) {
        return !bothUpToDate && canvas != this.canvas;
    }

    public void markCanvasUpToDate(Canvas canvas) {
        if (this.canvas != null && this.canvas != canvas) {
            bothUpToDate = true;
        }
        this.canvas = canvas;
    }

    public void invalidate() {
        canvas = null;
        bothUpToDate = false;
    }
}
