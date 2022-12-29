package hu.garaba;

import hu.garaba.linux.pollfd;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static hu.garaba.linux.poll_h.POLLIN;

public class EventLoop {
    private final List<Pollable> pollableList = new ArrayList<>();
    private final List<Runnable> eventHandlers = new ArrayList<>();
    private volatile boolean isRunning = false;

    public void addHandler(Pollable pollable, Runnable handler) {
        pollableList.add(pollable);
        eventHandlers.add(handler);
    }

    public void start() {
        if (pollableList.size() != eventHandlers.size()) {
            throw new IllegalStateException("Pollable objects and event handlers have different number of elements");
        }

        System.out.println(pollableList);

        try (final var arena = Arena.openConfined()) {
            MemorySegment pollfdArr = arena.allocateArray(pollfd.$LAYOUT(), pollableList.size());
            List<MemorySegment> pollFdList = pollfdArr.elements(pollfd.$LAYOUT()).toList();

            IntStream.range(0, pollableList.size())
                    .forEach(i -> {
                        MemorySegment pollfdStruct = pollFdList.get(i);
                        pollfd.fd$set(pollfdStruct, pollableList.get(i).fd());
                        pollfd.events$set(pollfdStruct, (short) POLLIN());
                        pollfd.revents$set(pollfdStruct, (short) 0);
                    });

            isRunning = true;
            while (isRunning) {
                if (Util.poll(pollfdArr, pollableList.size(), 100)) {
                    IntStream.range(0, pollableList.size())
                            .forEach(i -> {
                                MemorySegment pollfdStruct = pollFdList.get(i);

                                if ((pollfd.revents$get(pollfdStruct) & POLLIN()) > 0) {
                                    eventHandlers.get(i).run();
                                }
                            });
                }
            }
        }
    }

    public void stop() {
        isRunning = false;
    }
}
