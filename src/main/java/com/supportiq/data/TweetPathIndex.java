package com.supportiq.data;

import java.util.Arrays;

/**
 * Primitive linear-probing hash map to store (tweetId -> {rootId, depth}).
 * Minimal memory footprint, easily fits 500k entries in ~12MB.
 */
public class TweetPathIndex {
    private long[] keys;
    private long[] rootIds;
    private int[] depths;
    private int size;
    private static final long EMPTY = -1L;

    public TweetPathIndex(int capacity) {
        int c = Integer.highestOneBit(capacity - 1) << 2; 
        if (c < 16) c = 16;
        keys = new long[c];
        rootIds = new long[c];
        depths = new int[c];
        Arrays.fill(keys, EMPTY);
    }

    public void put(long key, long rootId, int depth) {
        int mask = keys.length - 1;
        int idx = (int) (hash(key) & mask);
        while (keys[idx] != EMPTY) {
            if (keys[idx] == key) {
                // If it already exists, update only if the new depth is shallower
                if (depth < depths[idx]) {
                    rootIds[idx] = rootId;
                    depths[idx] = depth;
                }
                return;
            }
            idx = (idx + 1) & mask;
        }
        keys[idx] = key;
        rootIds[idx] = rootId;
        depths[idx] = depth;
        size++;
        if (size * 2 >= keys.length) {
            resize();
        }
    }

    public boolean contains(long key) {
        int mask = keys.length - 1;
        int idx = (int) (hash(key) & mask);
        while (keys[idx] != EMPTY) {
            if (keys[idx] == key) return true;
            idx = (idx + 1) & mask;
        }
        return false;
    }

    public long getRootId(long key) {
        int mask = keys.length - 1;
        int idx = (int) (hash(key) & mask);
        while (keys[idx] != EMPTY) {
            if (keys[idx] == key) return rootIds[idx];
            idx = (idx + 1) & mask;
        }
        return -1L;
    }

    public int getDepth(long key) {
        int mask = keys.length - 1;
        int idx = (int) (hash(key) & mask);
        while (keys[idx] != EMPTY) {
            if (keys[idx] == key) return depths[idx];
            idx = (idx + 1) & mask;
        }
        return -1;
    }

    private void resize() {
        long[] oldKeys = keys;
        long[] oldRootIds = rootIds;
        int[] oldDepths = depths;
        
        keys = new long[oldKeys.length * 2];
        rootIds = new long[keys.length];
        depths = new int[keys.length];
        
        Arrays.fill(keys, EMPTY);
        size = 0;
        
        for (int i = 0; i < oldKeys.length; i++) {
            if (oldKeys[i] != EMPTY) {
                put(oldKeys[i], oldRootIds[i], oldDepths[i]);
            }
        }
    }

    private long hash(long key) {
        key ^= (key >>> 33);
        key *= 0xff51afd7ed558ccdL;
        key ^= (key >>> 33);
        key *= 0xc4ceb9fe1a85ec53L;
        key ^= (key >>> 33);
        return key;
    }
}
