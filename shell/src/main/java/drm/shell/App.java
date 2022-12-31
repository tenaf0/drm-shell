package drm.shell;

import drm.shell.drm.Renderer;
import drm.shell.drm.Session;
import drm.shell.drm.event.Event;
import drm.shell.drm.event.KeyboardEvent;
import drm.shell.drm.event.PointerEvent;
import org.jetbrains.skija.*;
import org.tinylog.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalTime;

public class App {
    public static void main(String[] args) {
        String cardPath = "/dev/dri/card0";
        if (args.length > 0) {
            cardPath = args[0];
        }
        Renderer app = new Renderer() {
            private float x = 0.0f;
            private float y = 0.0f;

            {
                Thread thread = new Thread(() -> {
                    while (true) {
                        time = LocalTime.now();
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
                thread.setDaemon(true);
                thread.start();
            }

            @Override
            public void handleEvent(Session session, Event event) {
                switch (event) {
                    case PointerEvent(float dx,float dy) -> {
                        x = Math.min(Math.max(0, x + dx), 1920f);
                        y = Math.min(Math.max(0, y + dy), 1080f);
                        Logger.debug("{} {}\n", x, y);
                    }
                    case KeyboardEvent(boolean b,char key) -> {
                        Logger.debug("Got key: {}", (int) key);
                        if (key >= 2 && key <= 10) {
                            session.pause();
                            session.seat().switchSession(key - 1);
                        } else if (key == 16) {
                            session.stop();
                        }
                    }
                }
            }

            final Paint red = new Paint();
            {
                red.setColor(Color.makeRGB(255, 0, 0));
            }

            volatile LocalTime time = LocalTime.now();

            @Override
            public void render(Canvas canvas) {
                canvas.clear(Color.makeRGB(0, 0, 0));
                canvas.drawTextLine(TextLine.make(time.toString(), new Font(Typeface.makeDefault(), 20.0f)),
                        0, 20.0f, red);
                canvas.drawRect(Rect.makeXYWH(x, y, 10, 10), red);
            }
        };

        try (Session session = Session.createSession(app, Path.of(cardPath))) {
            Logger.info("App started");

            Thread watchdog = new Thread(() -> {
                try {
                    Thread.sleep(22000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                Logger.info("Watchdog expired");
                session.stop();
            });
            watchdog.setDaemon(true);
            watchdog.start();
            session.start();
        } catch (Exception e) {
            Logger.info(e);
            throw new RuntimeException("Could not create session", e);
        }
    }
}
