package drm.shell.ui;

import org.jetbrains.skija.Canvas;
import org.jetbrains.skija.Surface;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RedrawHelperTest {
    private static Canvas canvas1;
    private static Canvas canvas2;

    @BeforeAll
    public static void init() {
        canvas1 = Surface.makeNull(10, 10).getCanvas();
        canvas2 = Surface.makeNull(10, 10).getCanvas();

        assert canvas1 != canvas2;
    }

    @Test
    public void test() {
        RedrawHelper redrawHelper = new RedrawHelper();
        assertTrue(redrawHelper.needsRedraw(canvas1));
        assertTrue(redrawHelper.needsRedraw(canvas2));

        redrawHelper.markCanvasUpToDate(canvas1);
        assertFalse(redrawHelper.needsRedraw(canvas1));
        assertTrue(redrawHelper.needsRedraw(canvas2));

        redrawHelper.markCanvasUpToDate(canvas2);
        assertFalse(redrawHelper.needsRedraw(canvas1));
        assertFalse(redrawHelper.needsRedraw(canvas2));

        redrawHelper.invalidate();
        assertTrue(redrawHelper.needsRedraw(canvas1));
        assertTrue(redrawHelper.needsRedraw(canvas2));
    }
}