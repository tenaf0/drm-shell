package drm.shell.drm.drm;

import hu.garaba.drmMode._drmModeConnector;
import hu.garaba.drmMode._drmModeModeInfo;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.List;

import static hu.garaba.drmMode.xf86drmMode_h.*;

public class Connector {
    private final Drm drm;
    final int connectorId;

    public record Info(
            int encoderId,
            ConnectorType connectorType,
            int connectorTypeId,
            boolean connection,
            int mmWidth,
            int mmHeight,
            int subpixel,
            List<Mode> modes
    ) {}

    private Info info;

    Connector(Drm drm, int connectorId) {
        this.drm = drm;
        this.connectorId = connectorId;
    }

    public void sync() {
        try (final var arena = Arena.openConfined()) {
            MemorySegment connectorAddr = drmModeGetConnector(drm.fd, connectorId);
            MemorySegment connector = MemorySegment.ofAddress(connectorAddr.address(), _drmModeConnector.$LAYOUT().byteSize(), arena.scope(), () -> drmModeFreeConnector(connectorAddr));

            int modeCount = Math.max(_drmModeConnector.count_modes$get(connector)-1, 0);
            MemorySegment modeArr = MemorySegment.ofAddress(_drmModeConnector.modes$get(connector).address(), _drmModeModeInfo.$LAYOUT().byteSize() * modeCount, arena.scope());
            List<Mode> modes = modeArr.elements(_drmModeModeInfo.$LAYOUT())
                    .map(Mode::fromStruct)
                    .toList();

            this.info = new Info(
                    _drmModeConnector.encoder_id$get(connector),
                    ConnectorType.fromCode(_drmModeConnector.connector_type$get(connector)),
                    _drmModeConnector.connector_type_id$get(connector),
                    _drmModeConnector.connection$get(connector) == DRM_MODE_CONNECTED(),
                    _drmModeConnector.mmWidth$get(connector),
                    _drmModeConnector.mmHeight$get(connector),
                    _drmModeConnector.subpixel$get(connector),
                    modes
            );
        }
    }

    public Info info() {
        return info;
    }

    @Override
    public String toString() {
        return String.format("Connector[%d, info=%s]", connectorId, info);
    }
}
