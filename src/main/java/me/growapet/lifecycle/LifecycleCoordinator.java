package me.growapet.lifecycle;

import java.util.concurrent.atomic.AtomicReference;

/** Small, Bukkit-free lifecycle state machine for startup/readiness/shutdown gates. */
public final class LifecycleCoordinator {
    private final AtomicReference<State> state = new AtomicReference<>(State.NEW);

    public boolean beginStartup() { return state.compareAndSet(State.NEW, State.STARTING); }

    public boolean markReady() { return state.compareAndSet(State.STARTING, State.READY); }

    public boolean fail(Throwable ignored) {
        return state.compareAndSet(State.STARTING, State.FAILED);
    }

    public boolean beginShutdown() {
        while (true) {
            State current = state.get();
            if (current == State.STOPPING || current == State.STOPPED) return false;
            if (state.compareAndSet(current, State.STOPPING)) return true;
        }
    }

    public boolean markStopped() { return state.compareAndSet(State.STOPPING, State.STOPPED); }

    public State state() { return state.get(); }

    public boolean isReady() { return state() == State.READY; }

    public enum State { NEW, STARTING, READY, FAILED, STOPPING, STOPPED }
}
