package net.nehaverse.nehanpcs.path;

import net.nehaverse.nehanpcs.storage.YamlStorageManager;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class PathManager {
    private final YamlStorageManager storageManager;
    private final Map<String, NpcPath> paths = new LinkedHashMap<>();

    public PathManager(YamlStorageManager storageManager) {
        this.storageManager = storageManager;
    }

    public void load() {
        paths.clear();
        paths.putAll(storageManager.loadPaths());
    }

    public void save() {
        storageManager.savePaths(paths.values());
    }

    public Collection<NpcPath> all() {
        return Collections.unmodifiableCollection(paths.values());
    }

    public Optional<NpcPath> get(String name) {
        return Optional.ofNullable(paths.get(name.toLowerCase(java.util.Locale.ROOT)));
    }

    public boolean create(String name) {
        String key = name.toLowerCase(java.util.Locale.ROOT);
        if (paths.containsKey(key)) {
            return false;
        }
        paths.put(key, new NpcPath(name));
        save();
        return true;
    }

    public boolean delete(String name) {
        boolean removed = paths.remove(name.toLowerCase(java.util.Locale.ROOT)) != null;
        if (removed) {
            save();
        }
        return removed;
    }
}
