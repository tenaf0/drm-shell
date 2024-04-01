package drm.shell.drm.seat;

import drm.shell.drm.Session;
import hu.garaba.CWrapper;
import hu.garaba.Pollable;
import hu.garaba.libinput.libinput_interface;
import org.tinylog.Logger;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Consumer;

import static hu.garaba.libinput.libinput_h.*;
import static hu.garaba.libinput.libinput_h.libinput_udev_assign_seat;

public class Libinput implements Pollable {
    private final int fd;
    private final MemorySegment libinputContext;

    public Libinput(int fd, MemorySegment libinputContext) {
        this.fd = fd;
        this.libinputContext = libinputContext;
    }

    public static Libinput createContextForSeat(Seat seat) {
        MemorySegment libinputInterface = createLibinputInterface();
        MemorySegment udev = udev_new();

        MemorySegment userDataSegment = seat.arena().allocate(ValueLayout.JAVA_INT);
        userDataSegment.set(ValueLayout.JAVA_INT, 0, seat.id());

        MemorySegment libinput = libinput_udev_create_context(libinputInterface, userDataSegment, udev);
        if (libinput.get(ValueLayout.JAVA_LONG, 0) == 0) {
            throw new RuntimeException("Could not create libinput context");
        }
        int fd = (int) CWrapper.execute(() -> libinput_get_fd(libinput), "Could not create libinput context on seat " + seat);

        try (final var arena = Arena.ofConfined()) {
            String seatName = seat.name();
            MemorySegment stringSegment = arena.allocate(seatName.length() + 1);
            stringSegment.setString(0, seatName);
            CWrapper.execute(() -> (long) libinput_udev_assign_seat(libinput, stringSegment), "Could not assign seat " + seatName + " to libinput context");
        }

        return new Libinput(fd, libinput);
    }

    @Override
    public int fd() {
        return fd;
    }

    public void dispatch(Consumer<MemorySegment> eventHandler) {
        CWrapper.execute(() -> (long) libinput_dispatch(libinputContext), "Libinput dispatch failed");

        MemorySegment unsafeEventPtr;
        while ((unsafeEventPtr = libinput_get_event(libinputContext)).address() > 0) {
            try (final var arena = Arena.ofConfined()) {
                final var finalUnsafeEventPtr = unsafeEventPtr;
                MemorySegment eventPtr = MemorySegment.ofAddress(unsafeEventPtr.address()).reinterpret(0, arena, ms -> libinput_event_destroy(finalUnsafeEventPtr));
                eventHandler.accept(eventPtr);
            }
        }
    }

    public void resume() {
        Logger.info("libinput_resume");
        CWrapper.execute(() -> libinput_resume(libinputContext));
    }

    public void suspend() {
        Logger.info("libinput_suspend");
        libinput_suspend(libinputContext);
    }

    private static MemorySegment createLibinputInterface() {
        try {
            Linker linker = Linker.nativeLinker();
            MethodHandle openRestricted = MethodHandles.lookup().findStatic(LibinputInterfaceImplementation.class, "openRestricted",
                    MethodType.methodType(int.class, MemorySegment.class, int.class, MemorySegment.class));
            MethodHandle closeRestricted = MethodHandles.lookup().findStatic(LibinputInterfaceImplementation.class, "closeRestricted",
                    MethodType.methodType(void.class, int.class, MemorySegment.class));
            MemorySegment openRestrictedStub = linker.upcallStub(openRestricted, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS), Arena.global());
            MemorySegment closeRestrictedStub = linker.upcallStub(closeRestricted, FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT, ValueLayout.ADDRESS), Arena.global());

            MemorySegment libinputInterface = Arena.global().allocate(libinput_interface.layout());
            libinput_interface.open_restricted(libinputInterface, openRestrictedStub);
            libinput_interface.close_restricted(libinputInterface, closeRestrictedStub);
            return libinputInterface;
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static class LibinputInterfaceImplementation {
        private static Seat getSeat(MemorySegment userData) {
            MemorySegment userDataSafe = MemorySegment.ofAddress(userData.address()).reinterpret( ValueLayout.JAVA_INT.byteSize(), Arena.ofAuto(), null);
            int sessionId = userDataSafe.get(ValueLayout.JAVA_INT, 0);
            Session session = Session.sessions.get(sessionId);
            return session.seat();
        }
        static int openRestricted(MemorySegment path, int flags, MemorySegment userData) {
            MemorySegment safePath = MemorySegment.ofAddress(path.address()).reinterpret(Long.MAX_VALUE, Arena.ofAuto(), null);
            Logger.debug("Opening " + safePath.getString(0));
            return (int) CWrapper.execute(() -> getSeat(userData).openDevice(safePath), "Could not open file " + safePath.getString(0));
        }

        static void closeRestricted(int fd, MemorySegment userData) {
            Logger.debug("Closing " + fd);
            getSeat(userData).closeDevice(fd);
        }
    }
}
