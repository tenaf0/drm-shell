package hu.garaba;

import hu.garaba.linux.pollfd;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static hu.garaba.linux.poll_h.POLLIN;

public class EventLoop {
    private final List<Pollable> pollableList = new ArrayList<>();
    private final List<Runnable> eventHandlers = new ArrayList<>();
    private final int tick;
    private long lastTick = System.currentTimeMillis();
    private Runnable tickHandler;
    private volatile boolean isRunning = false;

    public EventLoop(int tick) {
        this.tick = tick;
    }

    public void addHandler(Pollable pollable, Runnable handler) {
        pollableList.add(pollable);
        eventHandlers.add(handler);
    }

    public void addTickHandler(Runnable handler) {
        this.tickHandler = handler;
    }

    public void start() {
        if (pollableList.size() != eventHandlers.size()) {
            throw new IllegalStateException("Pollable objects and event handlers have different number of elements");
        }

        try (final var arena = Arena.ofConfined()) {
            MemorySegment pollfdArr = arena.allocate(MemoryLayout.sequenceLayout(pollableList.size(), pollfd.layout()));
            List<MemorySegment> pollFdList = pollfdArr.elements(pollfd.layout()).toList();

            IntStream.range(0, pollableList.size())
                    .forEach(i -> {
                        MemorySegment pollfdStruct = pollFdList.get(i);
                        pollfd.fd(pollfdStruct, pollableList.get(i).fd());
                        pollfd.events(pollfdStruct, (short) POLLIN());
                        pollfd.revents(pollfdStruct, (short) 0);
                    });

            isRunning = true;
            while (isRunning) {
                boolean poll = Util.poll(pollfdArr, pollableList.size(), tick);
                if (poll) {
                    IntStream.range(0, pollableList.size())
                            .forEach(i -> {
                                MemorySegment pollfdStruct = pollFdList.get(i);

                                if ((pollfd.revents(pollfdStruct) & POLLIN()) > 0) {
                                    eventHandlers.get(i).run();
                                }
                            });
                }
                long l = System.currentTimeMillis();
                if (l - lastTick > tick) {
                    lastTick = l;
                    tickHandler.run();
                }
            }
        }
    }

    public void stop() {
        isRunning = false;
    }
}
