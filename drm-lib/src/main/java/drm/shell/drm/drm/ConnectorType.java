package drm.shell.drm.drm;

import static hu.garaba.drmMode.xf86drmMode_h.*;


public enum ConnectorType {
    UNKNOWN(DRM_MODE_CONNECTOR_Unknown()),
    VGA(DRM_MODE_CONNECTOR_VGA()),
    DVI_I(DRM_MODE_CONNECTOR_DVII()),
    DVI_D(DRM_MODE_CONNECTOR_DVID()),
    DVI_A(DRM_MODE_CONNECTOR_DVIA()),
    Composite(DRM_MODE_CONNECTOR_Composite()),
    SVIDEO(DRM_MODE_CONNECTOR_SVIDEO()),
    LVDS(DRM_MODE_CONNECTOR_LVDS()),
    Component(DRM_MODE_CONNECTOR_Component()),
    _9PinDIN(DRM_MODE_CONNECTOR_9PinDIN()),
    DisplayPort(DRM_MODE_CONNECTOR_DisplayPort()),
    HDMI_A(DRM_MODE_CONNECTOR_HDMIA()),
    HDMI_B(DRM_MODE_CONNECTOR_HDMIB()),
    TV(DRM_MODE_CONNECTOR_TV()),
    EDP(DRM_MODE_CONNECTOR_eDP()),
    Virtual(DRM_MODE_CONNECTOR_VIRTUAL()),
    DSI(DRM_MODE_CONNECTOR_DSI()),
    DPI(DRM_MODE_CONNECTOR_DPI())
    ;
    private final int code;

    ConnectorType(int code) {
        this.code = code;
    }

    public static ConnectorType fromCode(int code) {
        for (var v : values()) {
            if (v.code == code) {
                return v;
            }
        }
        throw new IllegalArgumentException("No type is known with code " + code);
    }
}
