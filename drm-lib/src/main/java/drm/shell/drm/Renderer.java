package drm.shell.drm;

import drm.shell.drm.event.Event;
import org.jetbrains.skija.Canvas;

public interface Renderer {
    void init(int width, int height);
    void handleEvent(Session session, Event event);
    void render(Canvas canvas);
}
