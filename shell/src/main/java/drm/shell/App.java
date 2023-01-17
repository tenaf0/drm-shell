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
import org.jetbrains.skija.*;
import org.tinylog.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class App implements Renderer {

    private static final float POINTER_SIZE = 10.0f;

    private Queue<Consumer<App>> actionQueue = new ArrayBlockingQueue<>(10);

    private static class AppState {
        static float x = 0.0f;
        static float y = 0.0f;
    }

    private Scene scene;
    private Text keyText;

    private int width;
    private int height;
    private boolean takeScreenshot = false;

    @Override
    public void init(int width, int height) {
        this.width = width;
        this.height = height;
        scene = new Scene(Rect.makeXYWH(0f, 0f, width, height), new Paint().setColor(Color.makeRGB(210,210,210)));
        final var topBar = new Scene(Rect.makeXYWH(0f, 0f, width, 30f), new Paint().setColor(Color.makeRGB(20, 20, 20)));
        topBar.setParent(scene);

        final var clock = new Text(topBar, "Clock", new Paint().setColor(Color.makeRGB(255, 255, 255)),
                new Position(width-90, 5f), 90f);
        final var manual = new Text(scene, "Press 1-9 to switch to VTTY,\n'R' to take screenshot,\n'Q' to exit.", new Paint().setColor(Color.makeRGB(169, 30, 30)),
                new Position(60f, 50f), 400f);
        keyText = new Text(scene, "Key press", new Paint().setColor(Color.makeRGB(169, 30, 30)),
                new Position(60f, 160f), 200f);
        Text freeText = new Text(scene, "", new Paint().setColor(Color.makeRGB(169, 30, 30)),
                new Position(60f, 400f), 900f);

        Thread thread = new Thread(() -> {
            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
            while (true) {
                actionQueue.offer(a -> {
                    clock.setText(LocalTime.now().format(dateTimeFormatter));
                });
                actionQueue.offer(a -> {
                    Process exec;
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
                scene.damage(Rect.makeXYWH(AppState.x, AppState.y, POINTER_SIZE, POINTER_SIZE));

                AppState.x = Math.min(Math.max(0, AppState.x + dx), scene.boundingBox().getRight() - POINTER_SIZE);
                AppState.y = Math.min(Math.max(0.0f, AppState.y + dy), scene.boundingBox().getBottom() - POINTER_SIZE);
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
                } else if (!b && key == 19) {
                    takeScreenshot = true;
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

    private static final Paint red = new Paint();
    static {
        red.setColor(Color.makeRGB(255, 0, 0));
    }
    private static final Paint green = new Paint();
    {
        green.setColor(Color.makeRGB(0, 255, 0));
    }

    private long renderCount = 0;
    private long renderTime = 0;

    @Override
    public void render(Canvas canvas) {
        long l = System.nanoTime();

        scene.draw(canvas);

        canvas.drawRect(Rect.makeXYWH(AppState.x, AppState.y, POINTER_SIZE, POINTER_SIZE), red);
        if (takeScreenshot) {
            new Thread(() -> {
                Bitmap bitmap = new Bitmap();
                bitmap.allocN32Pixels(width, height);
                canvas.readPixels(bitmap, 0, 0);

                Image image = Image.makeFromBitmap(bitmap);
                Data data = image.encodeToData(EncodedImageFormat.PNG);

                try {
                    Path screenshot = Files.createTempFile("screenshot", ".png");
                    BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(screenshot.toFile()));
                    stream.write(data.getBytes());
                    stream.close();
                    Logger.info("Screenshot written to: " + screenshot);
                } catch (Exception e) {
                    Logger.info("Failed to write screenshot");
                }

            }).start();

            takeScreenshot = false;
        }

        renderCount++;
        renderTime += System.nanoTime()-l;
        if (renderCount % 500 == 0) {
            Logger.info("Render time: {} microsec/frame", renderTime / 1e3 / renderCount);
            renderCount = 0;
            renderTime = 0;
        }
    }
}
