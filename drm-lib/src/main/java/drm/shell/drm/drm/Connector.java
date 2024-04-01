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
        try (final var arena = Arena.ofConfined()) {
            MemorySegment connectorAddr = drmModeGetConnector(drm.fd, connectorId);
            MemorySegment connector = MemorySegment.ofAddress(connectorAddr.address()).reinterpret(_drmModeConnector.layout().byteSize(), arena, ms -> drmModeFreeConnector(connectorAddr));

            int modeCount = Math.max(_drmModeConnector.count_modes(connector)-1, 0);
            MemorySegment modeArr = MemorySegment.ofAddress(_drmModeConnector.modes(connector).address()).reinterpret( _drmModeModeInfo.layout().byteSize() * modeCount, arena, null);
            List<Mode> modes = modeArr.elements(_drmModeModeInfo.layout())
                    .map(Mode::fromStruct)
                    .toList();

            this.info = new Info(
                    _drmModeConnector.encoder_id(connector),
                    ConnectorType.fromCode(_drmModeConnector.connector_type(connector)),
                    _drmModeConnector.connector_type_id(connector),
                    _drmModeConnector.connection(connector) == DRM_MODE_CONNECTED(),
                    _drmModeConnector.mmWidth(connector),
                    _drmModeConnector.mmHeight(connector),
                    _drmModeConnector.subpixel(connector),
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
