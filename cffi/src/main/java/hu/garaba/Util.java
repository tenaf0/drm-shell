package hu.garaba;

import java.io.IOException;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;

public class Util {
    private static final MethodHandle open;
    private static final MethodHandle close;
    private static final MethodHandle mmap;
    private static final MethodHandle poll;

    static {
        Linker linker = Linker.nativeLinker();
        MemorySegment openSymbol = linker.defaultLookup().find("open").orElseThrow();
        MemorySegment closeSymbol = linker.defaultLookup().find("close").orElseThrow();
        MemorySegment mmapSymbol = linker.defaultLookup().find("mmap").orElseThrow();
        MemorySegment pollSymbol = linker.defaultLookup().find("poll").orElseThrow();
        open = linker.downcallHandle(openSymbol, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        close = linker.downcallHandle(closeSymbol, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
        mmap = linker.downcallHandle(mmapSymbol, FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
        poll = linker.downcallHandle(pollSymbol, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    }

    public static int open(String fileName, int flags) throws IOException {
        try (final var arena = Arena.openConfined()) {
            MemorySegment fileNameSegment = MemorySegment.allocateNative(fileName.getBytes(StandardCharsets.UTF_8).length + 1, arena.scope());
            fileNameSegment.setUtf8String(0, fileName);

            return open(fileNameSegment, flags);
        }
    }

    public static int open(MemorySegment fileNameSegment, int flags) throws IOException {
        int fd;
        try {
            fd = (int) open.invokeExact(fileNameSegment, flags);
        } catch (Throwable e) {
            throw new IOException("Could not open file", e);
        }
        if (fd < 0) {
            throw new IOException("Could not open file");
        }

        return fd;
    }

    public static MemorySegment mmap(long length, int protection, int flag, int fd, long offset, SegmentScope session) {
        try {
            MemorySegment retAddress = (MemorySegment) mmap.invokeExact(MemorySegment.NULL, length, protection, flag, fd, offset);
            return MemorySegment.ofAddress(retAddress.address(), length, session);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean poll(MemorySegment pollfd, int pollFdsNumber, int timeout) {
        try {
            int ret = (int) CWrapper.execute(() -> {
                try {
                    return (int) poll.invokeExact(pollfd, pollFdsNumber, timeout);
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            }, "Polling failed");
            return ret > 0;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}