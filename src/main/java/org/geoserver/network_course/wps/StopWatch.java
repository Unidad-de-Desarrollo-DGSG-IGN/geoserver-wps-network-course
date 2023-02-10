package org.geoserver.network_course.wps;

import java.time.Duration;
import java.time.Instant;

public class StopWatch {
    private Instant starts;
    private Instant ends;

    public void start() {
        this.starts = Instant.now();
    }

    public void stop() {
        this.ends = Instant.now();
    }

    public long duration() {
        return Duration.between(this.starts, this.ends).toMillis();
    }
}
