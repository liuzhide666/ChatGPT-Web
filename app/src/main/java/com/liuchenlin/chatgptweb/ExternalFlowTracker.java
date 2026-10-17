package com.liuchenlin.chatgptweb;

import java.util.EnumSet;
import java.util.Objects;

public final class ExternalFlowTracker {
    public enum Flow {
        ANDROID_PERMISSION,
        FILE_PICKER,
        DOWNLOAD_SAVE
    }

    private final EnumSet<Flow> active = EnumSet.noneOf(Flow.class);
    private Runnable changeListener = () -> {};

    public synchronized void setChangeListener(Runnable listener) {
        changeListener = listener == null ? () -> {} : listener;
    }

    public void begin(Flow flow) {
        Runnable listener;
        synchronized (this) {
            Objects.requireNonNull(flow, "flow");
            if (!active.add(flow)) {
                throw new IllegalStateException("external flow already active: " + flow);
            }
            listener = changeListener;
        }
        listener.run();
    }

    public void end(Flow flow) {
        Runnable listener = null;
        synchronized (this) {
            Objects.requireNonNull(flow, "flow");
            if (active.remove(flow)) listener = changeListener;
        }
        if (listener != null) listener.run();
    }

    public synchronized boolean isActive(Flow flow) {
        return active.contains(Objects.requireNonNull(flow, "flow"));
    }

    public synchronized boolean isAnyActive() {
        return !active.isEmpty();
    }

    public void clearAll() {
        Runnable listener = null;
        synchronized (this) {
            if (!active.isEmpty()) {
                active.clear();
                listener = changeListener;
            }
        }
        if (listener != null) listener.run();
    }
}
