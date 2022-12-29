package hu.garaba;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.function.LongSupplier;

import static hu.garaba.glibc.errno.errno_h.__errno_location;
import static hu.garaba.glibc.string.string_h.strerror;

public class CWrapper {
    private static MemorySegment errnoLocation;

    static {
        errnoLocation = __errno_location();
    }

    public static int errno() {
        return errnoLocation.get(ValueLayout.JAVA_INT, 0);
    }

    public static long execute(LongSupplier lambda) {
        return execute(lambda, "");
    }

    public static long execute(LongSupplier lambda, String userErrorMessage) {
        long res = lambda.getAsLong();
        if (res < 0) {
            int errno = errno();
            String errorMessage;
            try (Arena arena = Arena.openConfined()) {
                MemorySegment strerrorC = strerror(errno);
                MemorySegment strErrorSafe = MemorySegment.ofAddress(strerrorC.address(), Long.MAX_VALUE, arena.scope());

                errorMessage = strErrorSafe.getUtf8String(0);
            }

            throw new RuntimeException(String.format("%s\nResult: %d\nError code: %d\nError msg: %s",
                    userErrorMessage, res, errno, errorMessage));
        }

        return res;
    }
}
