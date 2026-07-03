package net.nehaverse.nehanpcs.action;

public final class NpcAction {
    private ActionType type;
    private String content;
    private int cooldownSeconds;

    public NpcAction(ActionType type, String content, int cooldownSeconds) {
        this.type = type;
        this.content = content;
        this.cooldownSeconds = cooldownSeconds;
    }

    public ActionType type() {
        return type;
    }

    public String content() {
        return content;
    }

    public int cooldownSeconds() {
        return cooldownSeconds;
    }

    public void cooldownSeconds(int cooldownSeconds) {
        this.cooldownSeconds = Math.max(0, cooldownSeconds);
    }
}
