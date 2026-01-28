package com.util;

import java.util.HashMap;
import java.util.Map;

public class TicketCache {
    
    private static final Map<String, CacheEntry> cache = new HashMap<>();
    
    private static class CacheEntry {
        String ticket;
        long expireTime;
        
        CacheEntry(String ticket, long expireTime) {
            this.ticket = ticket;
            this.expireTime = expireTime;
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }
    }
    
    public static String get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry != null && !entry.isExpired()) {
            return entry.ticket;
        }
        return null;
    }
    
    public static void put(String key, String ticket, int expiresIn) {
        long expireTime = System.currentTimeMillis() + (expiresIn * 1000L) - 60000L;
        cache.put(key, new CacheEntry(ticket, expireTime));
    }
    
    public static void remove(String key) {
        cache.remove(key);
    }
    
    public static void clear() {
        cache.clear();
    }
}
