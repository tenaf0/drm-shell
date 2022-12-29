package drm.shell.drm.event;

public record KeyboardEvent(boolean pressed, char key) implements Event {
}
