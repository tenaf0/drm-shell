package drm.shell.drm.seat;

import drm.shell.drm.Session;
import hu.garaba.CWrapper;
import hu.garaba.Pollable;
import hu.garaba.libseat.libseat_seat_listener;
import org.tinylog.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static hu.garaba.libseat.libseat_h.*;

public class Seat implements AutoCloseable, Pollable {
    private final int sessionId;
    private final String loginCtlSessionId;
    private final Arena arena;
    private final MemorySegment libseat;
    private final int fd;

    private Libinput libinput;

    private final Map<Integer, Integer> deviceIds = new HashMap<>(); // Stores fd, deviceId pairs

    private Seat(int sessionId, Arena arena, MemorySegment libseat, int fd) {
        this.sessionId = sessionId;
        this.arena = arena;
        this.libseat = libseat;
        this.fd = fd;

        this.loginCtlSessionId = getActiveSession();
    }

    public int id() {
        return sessionId;
    }

    public String loginCtlSessionId() {
        return loginCtlSessionId;
    }

    private String lazyName;

    public String name() {
        if (lazyName == null) {
            MemorySegment seatNameC = libseat_seat_name(libseat);
            try (final var arena = Arena.ofConfined()) {
                lazyName = MemorySegment.ofAddress(seatNameC.address()).reinterpret(Long.MAX_VALUE, arena, null).getString(0);
            }
        }

        return lazyName;
    }

    public Arena arena() {
        return arena;
    }

    public void initLibinput() {
        if (this.libinput != null)
            throw new RuntimeException("Libinput for this seat is already initialized");

        this.libinput = Libinput.createContextForSeat(this);
    }

    public Libinput libinput() {
        return libinput;
    }

    public void resume() {
        libinput.resume();
    }

    public void suspend() {
        libinput.suspend();
    }

    public void dispatch() {
        Logger.debug("Dispatching libseat event");
        CWrapper.execute(() -> libseat_dispatch(libseat, 0));
    }

    public int openDevice(MemorySegment path) {
        try (final var arena = Arena.ofConfined()) {
            MemorySegment fd = arena.allocate(ValueLayout.JAVA_INT);
            int deviceId = (int) CWrapper.execute(() -> libseat_open_device(libseat, path, fd),
                    "Could not open seat device");
            int fdVal = fd.get(ValueLayout.JAVA_INT, 0);

            deviceIds.put(fdVal, deviceId);
            return fdVal;
        }
    }

    public void closeDevice(int fd) {
        CWrapper.execute(() -> libseat_close_device(libseat, deviceIds.get(fd)), "Could not close device");
    }

    public void switchSession(int sessionNumber) {
        CWrapper.execute(() -> libseat_switch_session(libseat, sessionNumber), "Switching session to " + sessionNumber + " failed");
    }

    @Override
    public int fd() {
        return fd;
    }

    @Override
    public void close() {
        arena.close();
    }

    public static Seat openSeat(int sessionId) {
        final var arena = Arena.ofConfined();

        MemorySegment sessionIdAddress = Arena.global().allocate(ValueLayout.JAVA_INT);
        sessionIdAddress.set(ValueLayout.JAVA_INT, 0, sessionId);

        MemorySegment libseat = libseat_open_seat(createSeatListener(), sessionIdAddress);
        int fd = libseat_get_fd(libseat);
        if (fd < 0) {
            throw new RuntimeException("Could not get libseat fd");
        }
        return new Seat(sessionId, arena, MemorySegment.ofAddress(libseat.address()).reinterpret(arena,
                ms -> CWrapper.execute(() -> libseat_close_seat(libseat), "Failure during the closing of seat")), fd);
    }

    public String getActiveSession() {
        try {
            Process exec = Runtime.getRuntime().exec(new String[]{"loginctl", "-p", "ActiveSession", "show-seat", name()});

            try (BufferedReader reader = exec.inputReader()) {
                String result = reader.lines().collect(Collectors.joining());
                return result.substring("ActiveSession=".length());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static MemorySegment createSeatListener() {
        Linker linker = Linker.nativeLinker();

        try {
            MemorySegment seatListener = Arena.global().allocate(libseat_seat_listener.layout());
            MemorySegment enableSeat = linker.upcallStub(MethodHandles.lookup().findStatic(SeatListener.class, "enableSeat",
                            MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class)),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS), Arena.global());
            MemorySegment disableSeat = linker.upcallStub(MethodHandles.lookup().findStatic(SeatListener.class, "disableSeat",
                            MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class)),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS), Arena.global());

            libseat_seat_listener.enable_seat(seatListener, enableSeat);
            libseat_seat_listener.disable_seat(seatListener, disableSeat);

            return seatListener;
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static class SeatListener {
        public static void enableSeat(MemorySegment seat, MemorySegment userData) {
            MemorySegment data = userData.reinterpret(ValueLayout.JAVA_INT.byteSize());
            Logger.debug("Activating from seatListener");
            Session.sessions.get(data.get(ValueLayout.JAVA_INT, 0)).activate();
        }

        public static void disableSeat(MemorySegment seat, MemorySegment userData) {
            Logger.debug("Seat disabled");
            CWrapper.execute(() -> libseat_disable_seat(seat), "Failure during libseat disabling");
        }
    }
}
