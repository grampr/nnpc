package net.nehaverse.nehanpcs.conversation;

import java.util.ArrayList;
import java.util.List;

public final class Conversation {
    private final String name;
    private int cooldownSeconds = 10;
    private double radius = 5.0D;
    private final List<ConversationLine> lines = new ArrayList<>();

    public Conversation(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public int cooldownSeconds() {
        return cooldownSeconds;
    }

    public void cooldownSeconds(int cooldownSeconds) {
        this.cooldownSeconds = Math.max(0, cooldownSeconds);
    }

    public double radius() {
        return radius;
    }

    public void radius(double radius) {
        this.radius = Math.max(0.1D, radius);
    }

    public List<ConversationLine> lines() {
        return lines;
    }
}
