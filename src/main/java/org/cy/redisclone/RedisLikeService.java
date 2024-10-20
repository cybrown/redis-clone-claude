package org.cy.redisclone;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.stream.Collectors;

public class RedisLikeService {
    private final ConcurrentHashMap<String, String> dataStore = new ConcurrentHashMap<>();

    public String get(String key) {
        return dataStore.get(key);
    }

    public void set(String key, String value) {
        dataStore.put(key, value);
    }

    public boolean del(String key) {
        return dataStore.remove(key) != null;
    }

    public Set<String> keys(String pattern) {
        if ("*".equals(pattern)) {
            return dataStore.keySet();
        }
        if (pattern.endsWith("*")) {
            String prefix = pattern.substring(0, pattern.length() - 1);
            return dataStore.keySet().stream()
                    .filter(key -> key.startsWith(prefix))
                    .collect(Collectors.toSet());
        }
        return dataStore.containsKey(pattern) ? Set.of(pattern) : Set.of();
    }
}