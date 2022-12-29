package drm.shell.drm.seat;

import hu.garaba.CWrapper;
import hu.garaba.Pollable;
import hu.garaba.libseat.libseat_seat_listener;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;

import static hu.garaba.libseat.libseat_h.*;

public class Seat implements AutoCloseable, Pollable {
    private final int sessionId;
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
    }

    public int id() {
        return sessionId;
    }

    private String lazyName;

    public String name() {
        if (lazyName == null) {
            MemorySegment seatNameC = libseat_seat_name(libseat);
            try (final var arena = Arena.openConfined()) {
                lazyName = MemorySegment.ofAddress(seatNameC.address(), Long.MAX_VALUE, arena.scope()).getUtf8String(0);
            }
        }

        return lazyName;
    }

    public SegmentScope scope() {
        return arena.scope();
    }

    public void initLibinput() {
        if (this.libinput != null)
            throw new RuntimeException("Libinput for this seat is already initialized");

        this.libinput = Libinput.createContextForSeat(this);
    }

    public Libinput libinput() {
        return libinput;
    }

    public void dispatch() {
        System.out.println("Dispatching libseat event");
        CWrapper.execute(() -> libseat_dispatch(libseat, 0));
    }

    public int openDevice(MemorySegment path) {
        try (final var arena = Arena.openConfined()) {
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
    public void close() throws Exception {
        arena.close();
    }

    public static Seat openSeat(int sessionId) {
        final var arena = Arena.openConfined();

        MemorySegment sessionIdAddress = MemorySegment.allocateNative(ValueLayout.JAVA_INT, SegmentScope.global());
        sessionIdAddress.set(ValueLayout.JAVA_INT, 0, sessionId);

        MemorySegment libseat = libseat_open_seat(createSeatListener(), sessionIdAddress);
        int fd = libseat_get_fd(libseat);
        if (fd < 0) {
            throw new RuntimeException("Could not get libseat fd");
        }
        return new Seat(sessionId, arena, MemorySegment.ofAddress(libseat.address(), 0, arena.scope(),
                () -> CWrapper.execute(() -> libseat_close_seat(libseat), "Failure during the closing of seat")), fd);
    }

    private static MemorySegment createSeatListener() {
        Linker linker = Linker.nativeLinker();

        try {
            MemorySegment seatListener = MemorySegment.allocateNative(libseat_seat_listener.$LAYOUT(), SegmentScope.global());
            MemorySegment enableSeat = linker.upcallStub(MethodHandles.lookup().findStatic(SeatListener.class, "enableSeat",
                            MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class)),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS), SegmentScope.global());
            MemorySegment disableSeat = linker.upcallStub(MethodHandles.lookup().findStatic(SeatListener.class, "disableSeat",
                            MethodType.methodType(void.class, MemorySegment.class, MemorySegment.class)),
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS), SegmentScope.global());

            libseat_seat_listener.enable_seat$set(seatListener, enableSeat);
            libseat_seat_listener.disable_seat$set(seatListener, disableSeat);

            return seatListener;
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static class SeatListener {
        public static void enableSeat(MemorySegment seat, MemorySegment userData) {
        }

        public static void disableSeat(MemorySegment seat, MemorySegment userData) {
            System.out.println("Seat disabled");
            CWrapper.execute(() -> libseat_disable_seat(seat), "Failure during libseat disabling");
        }
    }
}
