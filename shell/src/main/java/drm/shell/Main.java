package drm.shell;

import drm.shell.drm.Session;
import org.tinylog.Logger;

import java.nio.file.Path;

public class Main {
    public static void main(String[] args) {
        String cardPath = "/dev/dri/card0";
        if (args.length > 0) {
            cardPath = args[0];
        }

        try (Session session = Session.createSession(new App(), Path.of(cardPath))) {
            Logger.info("App started");

            Thread watchdog = new Thread(() -> {
                try {
                    Thread.sleep(30*60*1000);
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
