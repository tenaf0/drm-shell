package drm.shell.drm.drm;

import hu.garaba.drmMode._drmModeModeInfo;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

public record Mode(
        int clock,
        short hdisplay,
        short vdisplay,
        String name,
        MemorySegment modeStruct
        ) {

    static Mode fromStruct(MemorySegment modeInfo) {
        int clock = _drmModeModeInfo.clock(modeInfo);
        short hdisplay = _drmModeModeInfo.hdisplay(modeInfo);
        short vdisplay = _drmModeModeInfo.vdisplay(modeInfo);
        String name = _drmModeModeInfo.name(modeInfo).getString(0);

        Arena arena = Arena.ofAuto();
        MemorySegment struct = arena.allocate(_drmModeModeInfo.layout());
        struct.copyFrom(modeInfo);
        return new Mode(clock, hdisplay, vdisplay, name, struct);
    }

    @Override
    public String toString() {
        return "Mode{" +
                "clock=" + clock +
                ", hdisplay=" + hdisplay +
                ", vdisplay=" + vdisplay +
                ", name='" + name + '\'' +
                '}';
    }
}
