package drm.shell.drm.drm;

import hu.garaba.drmMode._drmModeEncoder;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static hu.garaba.drmMode.xf86drmMode_h.drmModeFreeEncoder;
import static hu.garaba.drmMode.xf86drmMode_h.drmModeGetEncoder;

public record Encoder(
        int encoderId,
        int encoderType,
        int crtcId
        ) {

    static Encoder create(Drm drm, int encoderId) {
        try (final var arena = Arena.ofConfined()) {
            MemorySegment encoderAddr = drmModeGetEncoder(drm.fd, encoderId);
            MemorySegment encoder = MemorySegment.ofAddress(encoderAddr.address())
                    .reinterpret(_drmModeEncoder.layout().byteSize(), arena, ms -> drmModeFreeEncoder(encoderAddr));

            return new Encoder(encoderId,
                    _drmModeEncoder.encoder_type(encoder),
                    _drmModeEncoder.crtc_id(encoder));
        }
    }
}
