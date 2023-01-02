package drm.shell.drm.event;

public sealed interface Event permits TickEvent, KeyboardEvent, PointerEvent {
}
