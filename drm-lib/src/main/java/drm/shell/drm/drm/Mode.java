package drm.shell.drm.drm;

import hu.garaba.drmMode._drmModeModeInfo;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentScope;

public record Mode(
        int clock,
        short hdisplay,
        short vdisplay,
        String name,
        MemorySegment modeStruct
        ) {

    static Mode fromStruct(MemorySegment modeInfo) {
        int clock = _drmModeModeInfo.clock$get(modeInfo);
        short hdisplay = _drmModeModeInfo.hdisplay$get(modeInfo);
        short vdisplay = _drmModeModeInfo.vdisplay$get(modeInfo);
        String name = _drmModeModeInfo.name$slice(modeInfo).getUtf8String(0);

        MemorySegment struct = MemorySegment.allocateNative(_drmModeModeInfo.$LAYOUT(), SegmentScope.auto());
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
