package net.nehaverse.nehanpcs.action;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CooldownManager {
    private final Map<String, Long> cooldownUntilMillis = new HashMap<>();

    public long remainingSeconds(UUID playerUuid, int npcId, int actionIndex) {
        long until = cooldownUntilMillis.getOrDefault(key(playerUuid, npcId, actionIndex), 0L);
        long remainingMillis = until - System.currentTimeMillis();
        if (remainingMillis <= 0) {
            return 0;
        }
        return Math.max(1L, (remainingMillis + 999L) / 1000L);
    }

    public void mark(UUID playerUuid, int npcId, int actionIndex, int seconds) {
        if (seconds <= 0) {
            return;
        }
        cooldownUntilMillis.put(key(playerUuid, npcId, actionIndex), System.currentTimeMillis() + seconds * 1000L);
    }

    public void clear(UUID playerUuid) {
        String prefix = playerUuid + ":";
        cooldownUntilMillis.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private String key(UUID playerUuid, int npcId, int actionIndex) {
        return playerUuid + ":" + npcId + ":" + actionIndex;
    }
}
