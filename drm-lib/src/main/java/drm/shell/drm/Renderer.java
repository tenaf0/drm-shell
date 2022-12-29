package drm.shell.drm;

import drm.shell.drm.event.Event;
import org.jetbrains.skija.Canvas;

public interface Renderer {
    void handleEvent(Session session, Event event);
    void render(Canvas canvas);
}
