package drm.shell.drm;

import drm.shell.drm.drm.*;
import drm.shell.drm.event.KeyboardEvent;
import drm.shell.drm.event.PointerEvent;
import drm.shell.drm.event.TickEvent;
import drm.shell.drm.seat.Seat;
import hu.garaba.CWrapper;
import hu.garaba.EventLoop;
import hu.garaba.drm._drmEventContext;
import org.jetbrains.skija.Canvas;
import org.jetbrains.skija.ImageInfo;
import org.jetbrains.skija.Surface;
import org.tinylog.Logger;

import java.io.IOException;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static hu.garaba.drm.xf86drm_h.*;
import static hu.garaba.drmMode.xf86drmMode_h.DRM_MODE_PAGE_FLIP_EVENT;
import static hu.garaba.drmMode.xf86drmMode_h.drmModePageFlip;
import static hu.garaba.libinput.libinput_h.*;

/**
 * Represents a seat session. Upon creation, it will
 * - open a new libseat session
 * - register necessary event handlers (session suspend/resume, device open/close, page flip)
 * - set up DRM
 * - manage event loop (start, stop)
 */
public class Session implements AutoCloseable {
    static {
        System.loadLibrary("udev");
        System.loadLibrary("input");
        System.loadLibrary("drm");
        System.loadLibrary("seat");
    }

    public static List<Session> sessions = List.of();

    private final Renderer renderer;
    public final Drm drm;
    private final Seat seat;

    private final EventLoop eventLoop;
    private Map<Connector, ModeInfo> savedModeInfo;

    private Session(Renderer renderer, Drm drm, Seat seat) {
        this.renderer = renderer;
        this.drm = drm;
        this.seat = seat;

        this.eventLoop = new EventLoop(500);
    }

    public static Session createSession(Renderer renderer, Path videoCard) throws IOException {
        Drm drm = Drm.open(videoCard);
        Seat seat = Seat.openSeat(sessions.size());

        Session session = new Session(renderer, drm, seat);

        final var list = new ArrayList<>(sessions);
        list.add(session);
        sessions = Collections.unmodifiableList(list);

        seat.initLibinput();
        session.initEventHandler();
        session.initDrm();

        return session;
    }

    record ModeInfo(int fd, int crtcId, int fb) {}

    private static class VSync {
        static FrameBuffer[] fbs;
        static Canvas[] canvases;

        static ModeInfo modeInfo;

        static int frame = 0;

        static Renderer renderer;
        static boolean active = true;

        static void vsyncHandle() {
            if (!active) {
                return;
            }
            frame++;
            renderer.render(canvases[frame % 2]);
            modeInfo = new ModeInfo(modeInfo.fd, modeInfo.crtcId, fbs[frame % 2].fbId);
            drmModePageFlip(modeInfo.fd, modeInfo.crtcId, modeInfo.fb, DRM_MODE_PAGE_FLIP_EVENT(), MemorySegment.NULL);
        }

        static void vsyncResume() {
            modeInfo = new ModeInfo(modeInfo.fd, modeInfo.crtcId, fbs[frame % 2].fbId);
            drmModePageFlip(modeInfo.fd, modeInfo.crtcId, modeInfo.fb, DRM_MODE_PAGE_FLIP_EVENT(), MemorySegment.NULL);
        }
    }

    private void initDrm() {
        Connector connector = drm.fetchConnectors().stream()
                .peek(Connector::sync)
                .filter(c -> c.info().connection())
                .findFirst().orElseThrow();

        Encoder encoder = drm.fetchEncoders().stream()
                .filter(e -> e.encoderId() == connector.info().encoderId())
                .findFirst().orElseThrow();

        Crtc crtc = drm.fetchCrtcs().stream()
                .filter(c -> c.crtcId == encoder.crtcId())
                .findFirst().orElseThrow();
        crtc.sync();

        Logger.info("Modes: {}", connector.info().modes());
        Mode mode = connector.info().modes().get(0);
        this.savedModeInfo = Map.of(connector, new ModeInfo(drm.fd, crtc.crtcId, crtc.info().bufferId()));

        FrameBuffer frameBuffer1 = FrameBuffer.create(drm, mode.hdisplay(), mode.vdisplay());
        FrameBuffer frameBuffer2 = FrameBuffer.create(drm, mode.hdisplay(), mode.vdisplay());
        final var surface1 = Surface.makeRasterDirect(ImageInfo.makeN32Premul(mode.hdisplay(), mode.vdisplay()),
                frameBuffer1.bufferAddress(), mode.hdisplay()*4);
        final var surface2 = Surface.makeRasterDirect(ImageInfo.makeN32Premul(mode.hdisplay(), mode.vdisplay()),
                frameBuffer2.bufferAddress(), mode.hdisplay()*4);

        final var canvas1 = surface1.getCanvas();
        final var canvas2 = surface2.getCanvas();

        VSync.fbs = new FrameBuffer[]{frameBuffer1, frameBuffer2};
        VSync.canvases = new Canvas[]{canvas1, canvas2};

        VSync.modeInfo = new ModeInfo(drm.fd(), crtc.crtcId, frameBuffer1.fbId);
        drmModePageFlip(VSync.modeInfo.fd, VSync.modeInfo.crtcId, VSync.modeInfo.fb, DRM_MODE_PAGE_FLIP_EVENT(), MemorySegment.NULL);

        VSync.renderer = this.renderer;

        MemorySegment eventContext = _drmEventContext.allocate(Arena.openConfined());
        _drmEventContext.version$set(eventContext, 4);

        MemorySegment vsyncHandle;
        try {
            vsyncHandle = Linker.nativeLinker().upcallStub(MethodHandles.lookup().findStatic(VSync.class, "vsyncHandle", MethodType.methodType(void.class)),
                    FunctionDescriptor.ofVoid(), SegmentScope.global());
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        _drmEventContext.page_flip_handler$set(eventContext, vsyncHandle);

        eventLoop.addHandler(drm, () -> drmHandleEvent(drm.fd, eventContext));
    }

    private void initEventHandler() {
        eventLoop.addHandler(seat, () -> {
            seat.dispatch();
            activate();
        });
        eventLoop.addHandler(seat.libinput(), () -> seat.libinput().dispatch(e -> {
            if (libinput_event_get_type(e) == LIBINPUT_EVENT_POINTER_MOTION()) {
                MemorySegment pointerEvent = libinput_event_get_pointer_event(e);
                double dx = libinput_event_pointer_get_dx(pointerEvent);
                double dy = libinput_event_pointer_get_dy(pointerEvent);

                renderer.handleEvent(this, new PointerEvent((float) dx, (float) dy));
            } else if (libinput_event_get_type(e) == LIBINPUT_EVENT_KEYBOARD_KEY()) {
                MemorySegment keyboardEvent = libinput_event_get_keyboard_event(e);
                boolean pressed = libinput_event_keyboard_get_key_state(keyboardEvent) == LIBINPUT_KEY_STATE_PRESSED();
                int key = libinput_event_keyboard_get_key(keyboardEvent);

                renderer.handleEvent(this, new KeyboardEvent(pressed, (char) key));
            }
        }));
        eventLoop.addTickHandler(() -> renderer.handleEvent(this, new TickEvent()));
    }


    public void start() {
        eventLoop.start();
    }

    public void activate() {
        String activeSession = seat.getActiveSession();
        Logger.info("Active session now is: " + activeSession + " loginctl id: " + seat.loginCtlSessionId());

        if (activeSession.equals(seat.loginCtlSessionId())) {
            seat.resume();
            resume();
            VSync.vsyncResume();
        } else {
            seat.suspend();
            pause();
            VSync.active = false;
        }
    }

    public void resume() {
        VSync.active = true;
        Logger.info("Setting master");
        CWrapper.execute(() -> drmSetMaster(drm.fd), "Could not set to DRM master");
    }
    public void pause() {
        VSync.active = false;
        if (drmIsMaster(drm.fd) > 0) {
            CWrapper.execute(() -> drmDropMaster(drm.fd), "Could not drop DRM master");
        } else {
            Logger.info("Trying to drop DRM master, but it is not set");
        }
    }

    public void stop() {
        eventLoop.stop();
    }

    public Seat seat() {
        return seat;
    }

    @Override
    public void close() throws Exception {
        try {
            CWrapper.execute(() -> drmSetMaster(drm.fd), "Could not set to DRM master");
            Map.Entry<Connector, ModeInfo> modeInfoEntry = this.savedModeInfo.entrySet().stream().findAny().orElseThrow();
            drm.setMode(new Crtc(drm, modeInfoEntry.getValue().crtcId), modeInfoEntry.getKey(),
                    modeInfoEntry.getKey().info().modes().get(0), modeInfoEntry.getValue().fb());
        } catch (Exception e) {
            Logger.info("Could not restore mode settings to original. Maybe this is not the active session?");
            Logger.info(e);
        }

        pause();

        seat.close();
        drm.close();
    }
}
