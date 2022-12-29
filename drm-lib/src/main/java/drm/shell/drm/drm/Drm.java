package drm.shell.drm.drm;

import hu.garaba.CWrapper;
import hu.garaba.Pollable;
import hu.garaba.Util;
import hu.garaba.drmMode._drmModeRes;
import hu.garaba.linux.fcntl_h;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.List;

import static hu.garaba.drmMode.xf86drmMode_h.*;

public class Drm implements AutoCloseable, Pollable {
    public final int fd;
    final Arena arena;
    private final MemorySegment resource;

    private Drm(int fd) {
        this.fd = fd;
        this.arena = Arena.openConfined();

        MemorySegment resourceAddr = drmModeGetResources(fd);
        this.resource = MemorySegment.ofAddress(resourceAddr.address(), _drmModeRes.$LAYOUT().byteSize(), arena.scope(), () -> drmModeFreeResources(resourceAddr));
    }

    public static Drm open(Path driDevice) throws IOException {
        int fd = Util.open(driDevice.toString(), fcntl_h.O_RDWR());

        return new Drm(fd);
    }

    public List<Crtc> fetchCrtcs() {
        int count = _drmModeRes.count_crtcs$get(resource);
        MemorySegment crtcsArr = MemorySegment.ofAddress(_drmModeRes.crtcs$get(resource).address(),
                ValueLayout.JAVA_INT.byteSize() * count, arena.scope());

        return crtcsArr.elements(ValueLayout.JAVA_INT)
                .map(s -> s.get(ValueLayout.JAVA_INT, 0))
                .map(crtcId -> new Crtc(this, crtcId))
                .toList();
    }

    public List<Connector> fetchConnectors() {
        int count = _drmModeRes.count_connectors$get(resource);
        MemorySegment connectorsArr = MemorySegment.ofAddress(_drmModeRes.connectors$get(resource).address(),
                ValueLayout.JAVA_INT.byteSize() * count, arena.scope());

        return connectorsArr.elements(ValueLayout.JAVA_INT)
                .map(s -> s.get(ValueLayout.JAVA_INT, 0))
                .map(connId -> new Connector(this, connId))
                .toList();
    }

    public List<Encoder> fetchEncoders() {
        int count = _drmModeRes.count_encoders$get(resource);
        MemorySegment encodersArr = MemorySegment.ofAddress(_drmModeRes.encoders$get(resource).address(),
                ValueLayout.JAVA_INT.byteSize() * count, arena.scope());

        return encodersArr.elements(ValueLayout.JAVA_INT)
                .map(s -> s.get(ValueLayout.JAVA_INT, 0))
                .map(connId -> Encoder.create(this, connId))
                .toList();
    }

    public void setMode(Crtc crtc, Connector connector, Mode mode, FrameBuffer frameBuffer) {
        setMode(crtc, connector, mode, frameBuffer.fbId);
    }

    public void setMode(Crtc crtc, Connector connector, Mode mode, int fbId) {
        try (final var arena = Arena.openConfined()) {
            MemorySegment connectorArr = arena.allocate(ValueLayout.JAVA_INT);
            connectorArr.set(ValueLayout.JAVA_INT, 0, connector.connectorId);
            CWrapper.execute(() -> drmModeSetCrtc(fd, crtc.crtcId, fbId, 0, 0, connectorArr, 1, mode.modeStruct()));
        }
    }

    @Override
    public int fd() {
        return fd;
    }

    @Override
    public void close() throws Exception {
        arena.close();
    }
}
