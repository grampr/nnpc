package net.nehaverse.nehanpcs.path;

import java.util.ArrayList;
import java.util.List;

public final class NpcPath {
    private final String name;
    private boolean loop = true;
    private double speed = 0.2D;
    private final List<PathPoint> points = new ArrayList<>();

    public NpcPath(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public boolean loop() {
        return loop;
    }

    public void loop(boolean loop) {
        this.loop = loop;
    }

    public double speed() {
        return speed;
    }

    public void speed(double speed) {
        this.speed = Math.max(0.05D, speed);
    }

    public List<PathPoint> points() {
        return points;
    }
}
