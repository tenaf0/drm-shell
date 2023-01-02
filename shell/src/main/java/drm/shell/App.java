package drm.shell;

import drm.shell.drm.Renderer;
import drm.shell.drm.Session;
import drm.shell.drm.event.Event;
import drm.shell.drm.event.KeyboardEvent;
import drm.shell.drm.event.PointerEvent;
import drm.shell.drm.event.TickEvent;
import drm.shell.ui.Position;
import drm.shell.ui.Scene;
import drm.shell.ui.Text;
import org.jetbrains.skija.Canvas;
import org.jetbrains.skija.Color;
import org.jetbrains.skija.Paint;
import org.jetbrains.skija.Rect;
import org.tinylog.Logger;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class App implements Renderer {

    private Queue<Consumer<App>> actionQueue = new ArrayBlockingQueue<>(10);

    private static class AppState {
        static float x = 0.0f;
        static float y = 0.0f;
    }

    private Scene scene;
    private Text keyText;

    public App() {
        scene = new Scene(Rect.makeXYWH(0f, 0f, 1920f, 1080f), new Paint().setColor(Color.makeRGB(210,210,210)));
        final var topBar = new Scene(Rect.makeXYWH(0f, 0f, 1920f, 30f), new Paint().setColor(Color.makeRGB(20, 20, 20)));
        topBar.setParent(scene);

        final var clock = new Text(topBar, "Clock", new Paint().setColor(Color.makeRGB(255, 255, 255)),
                new Position(1830f, 5f), 90f);
        keyText = new Text(scene, "Key press", new Paint().setColor(Color.makeRGB(169, 30, 30)),
                new Position(60f, 100f), 200f);
        Text freeText = new Text(scene, "", new Paint().setColor(Color.makeRGB(169, 30, 30)),
                new Position(60f, 400f), 900f);

        Thread thread = new Thread(() -> {
            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
            while (true) {
                actionQueue.offer(a -> {
                    clock.setText(LocalTime.now().format(dateTimeFormatter));
                });
                actionQueue.offer(a -> {
                    Process exec = null;
                    try {
                        exec = Runtime.getRuntime().exec(new String[]{"free", "-h"});
                        String collect = exec.inputReader().lines().collect(Collectors.joining("\n"));
                        freeText.setText(collect);
                    } catch (IOException e) {
                        Logger.info(e);
                        throw new RuntimeException(e);
                    }
                });
                try {
                    Thread.sleep(1000);
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
                scene.damage(Rect.makeXYWH(AppState.x, AppState.y, 10f, 10f));

                AppState.x = Math.min(Math.max(0, AppState.x + dx), 1920f);
                AppState.y = Math.min(Math.max(0.0f, AppState.y + dy), 1080f);
                Logger.debug("{} {}\n", AppState.x, AppState.y);
            }
            case KeyboardEvent(boolean b,char key) -> {
                Logger.debug("Got key: {}", (int) key);
                keyText.setText("Got key " + (int) key);
                if (key >= 2 && key <= 10) {
                    session.pause();
                    session.seat().switchSession(key - 1);
                } else if (key == 16) {
                    session.stop();
                }
            }
            case TickEvent() -> {
                while (!actionQueue.isEmpty()) {
                    Consumer<App> action = actionQueue.remove();
                    action.accept(this);
                }
            }
        }
    }

    private final Paint red = new Paint();
    {
        red.setColor(Color.makeRGB(255, 0, 0));
    }
    private long renderCount = 0;
    private long renderTime = 0;

    private final Paint green = new Paint();
    {
        green.setColor(Color.makeRGB(0, 255, 0));
    }

    @Override
    public void render(Canvas canvas) {
        long l = System.nanoTime();

//        canvas.save();
//        canvas.clipRect(scene.boundingBox());
//        canvas.clear(Color.makeRGB(0, 0, 0));
        scene.draw(canvas);
//        canvas.restore();

        canvas.drawRect(Rect.makeXYWH(AppState.x, AppState.y, 10, 10), green);
        renderCount++;
        renderTime += System.nanoTime()-l;
        if (renderCount % 500 == 0) {
            Logger.info("Render time: {} ns/frame", renderTime / renderCount);
        }
    }
}
