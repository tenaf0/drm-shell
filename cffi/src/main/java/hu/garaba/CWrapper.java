package hu.garaba;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.function.LongSupplier;

import static hu.garaba.glibc.errno.errno_h.__errno_location;
import static hu.garaba.glibc.string.string_h.strerror;

public class CWrapper {
    public static class CException extends RuntimeException {
        public final long errorCode;
        public final long errno;
        public final String errorCodeMessage;

        public CException(long errorCode, long errno, String errorCodeMessage, String message) {
            super(message);
            this.errorCode = errorCode;
            this.errno = errno;
            this.errorCodeMessage = errorCodeMessage;
        }

        @Override
        public String toString() {
            return String.format("CException(code=%d, errno=%d, msg=%s)", errorCode, errno, getMessage());
        }
    }
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
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment strerrorC = strerror(errno);
                MemorySegment strErrorSafe = strerrorC.reinterpret(Long.MAX_VALUE, arena, null);

                errorMessage = strErrorSafe.getString(0);
            }

            throw new CException(res, errno, errorMessage, userErrorMessage);
        }

        return res;
    }
}
