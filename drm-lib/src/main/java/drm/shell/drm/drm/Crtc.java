package drm.shell.drm.drm;

import hu.garaba.drmMode._drmModeCrtc;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static hu.garaba.drmMode.xf86drmMode_h.drmModeFreeCrtc;
import static hu.garaba.drmMode.xf86drmMode_h.drmModeGetCrtc;

public class Crtc {
    private final Drm drm;
    public final int crtcId;

    public record Info(int bufferId, int x, int y, int width, int height, int modeValid, Mode mode, int gammaSize) {}

    private Info info;

    // TODO: package-private
    public Crtc(Drm drm, int crtcId) {
        this.drm = drm;
        this.crtcId = crtcId;
    }

    public void sync() {
        try (final var arena = Arena.openConfined()) {
            MemorySegment crtcAddr = drmModeGetCrtc(drm.fd, crtcId);
            MemorySegment crtc = MemorySegment.ofAddress(crtcAddr.address(), _drmModeCrtc.$LAYOUT().byteSize(), arena.scope(), () -> drmModeFreeCrtc(crtcAddr));

            int bufferId = _drmModeCrtc.buffer_id$get(crtc);
            int x = _drmModeCrtc.x$get(crtc);
            int y = _drmModeCrtc.y$get(crtc);
            int width = _drmModeCrtc.width$get(crtc);
            int height = _drmModeCrtc.height$get(crtc);
            int modeValid = _drmModeCrtc.mode_valid$get(crtc);
            Mode mode = Mode.fromStruct(_drmModeCrtc.mode$slice(crtc));
            int gammaSize = _drmModeCrtc.gamma_size$get(crtc);

            this.info = new Info(bufferId, x, y, width, height, modeValid, mode, gammaSize);
        }
    }

    public Info info() {
        return info;
    }

    @Override
    public String toString() {
        return String.format("Crtc[%d, %s]", crtcId, info);
    }
}
