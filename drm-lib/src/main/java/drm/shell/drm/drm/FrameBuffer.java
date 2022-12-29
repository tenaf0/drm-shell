package drm.shell.drm.drm;

import hu.garaba.CWrapper;
import hu.garaba.Util;
import hu.garaba.drm.drm_mode_create_dumb;
import hu.garaba.drm.drm_mode_map_dumb;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import static hu.garaba.drm.xf86drm_h.DRM_IOCTL_MODE_CREATE_DUMB;
import static hu.garaba.drm.xf86drm_h.drmIoctl;
import static hu.garaba.drmMode.xf86drmMode_h.DRM_IOCTL_MODE_MAP_DUMB;
import static hu.garaba.drmMode.xf86drmMode_h.drmModeAddFB;
import static hu.garaba.linux.mman_h.*;

public class FrameBuffer {
    private final Drm drm;

    public final int fbId;
    private final int handle;
    private final MemorySegment buffer;

    private FrameBuffer(Drm drm, int fbId, int handle, MemorySegment buffer) {
        this.drm = drm;
        this.fbId = fbId;
        this.handle = handle;
        this.buffer = buffer;
    }

    public long bufferAddress() {
        return buffer.address();
    }

    public static FrameBuffer create(Drm drm, short width, short height) {
        final byte bpp = 32;
        try (final var arena = Arena.openConfined()) {
            MemorySegment fbSegment = arena.allocate(ValueLayout.JAVA_INT);

            MemorySegment createRequest = drm_mode_create_dumb.allocate(arena);
            drm_mode_create_dumb.width$set(createRequest, width);
            drm_mode_create_dumb.height$set(createRequest, height);
            drm_mode_create_dumb.bpp$set(createRequest, bpp);
            CWrapper.execute(() -> drmIoctl(drm.fd, DRM_IOCTL_MODE_CREATE_DUMB(), createRequest), "Can't create dumb buffer");
            int pitch = drm_mode_create_dumb.pitch$get(createRequest);
            long size = drm_mode_create_dumb.size$get(createRequest);
            int handle = drm_mode_create_dumb.handle$get(createRequest);

            CWrapper.execute(() -> (long) drmModeAddFB(drm.fd, width, height, (byte)24, bpp,
                    pitch, handle, fbSegment), "Can't create dumb buffer");

            MemorySegment mapRequest = drm_mode_map_dumb.allocate(arena);
            drm_mode_map_dumb.handle$set(mapRequest, handle);
            CWrapper.execute(() -> drmIoctl(drm.fd, DRM_IOCTL_MODE_MAP_DUMB(), mapRequest), "Can't map dumb buffer");

            MemorySegment dumbBuffer = Util.mmap(size, PROT_READ() | PROT_WRITE(), MAP_SHARED(), drm.fd, drm_mode_map_dumb.offset$get(mapRequest), drm.arena.scope());

            return new FrameBuffer(drm, fbSegment.get(ValueLayout.JAVA_INT, 0), handle, dumbBuffer);
        }
    }
}
