package drm.shell;

import drm.shell.drm.Renderer;
import drm.shell.drm.Session;
import drm.shell.drm.event.Event;
import drm.shell.drm.event.KeyboardEvent;
import drm.shell.drm.event.PointerEvent;
import org.jetbrains.skija.*;

import java.io.IOException;
import java.nio.file.Path;

public class App {
    public static void main(String[] args) throws IOException {
        String cardPath = "/dev/dri/card0";
        if (args.length > 0) {
            cardPath = args[0];
        }
        try (Session session = Session.createSession(new Renderer() {
            private float x = 0.0f;
            private float y = 0.0f;

            @Override
            public void handleEvent(Session session, Event event) {
                switch (event) {
                    case PointerEvent(float dx, float dy) -> {
                        x += dx;
                        y += dy;
                        System.out.printf("%f %f\n", x, y);
                    }
                    case KeyboardEvent(boolean b, char key) -> {
                        System.out.println("Got key: " + (int) key);
                        session.stop();
                    }
                };
            }

            @Override
            public void render(Canvas canvas) {
                canvas.clear(Color.makeRGB(255, 255, 255));
                canvas.drawTextLine(TextLine.make("Hello", new Font(Typeface.makeDefault(), 20.0f)),
                        x, y, new Paint());
            }
        }, Path.of(cardPath))) {
            Thread watchdog = new Thread(() -> {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                session.stop();
            });
            watchdog.setDaemon(true);
            watchdog.start();
            session.start();
        } catch (Exception e) {
            throw new RuntimeException("Could not create session", e);
        }
    }
}
