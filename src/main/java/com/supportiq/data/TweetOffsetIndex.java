package com.supportiq.data;

import java.util.Arrays;

/**
 * Primitive linear-probing hash map to store (tweetId -> fileOffset).
 * Memory footprint: ~45MB for 2.8 million entries.
 */
public class TweetOffsetIndex {
    private long[] keys;
    private long[] offsets;
    private int size;
    private static final long EMPTY = -1L;

    public TweetOffsetIndex(int capacity) {
        int c = Integer.highestOneBit(capacity - 1) << 2;
        if (c < 16) c = 16;
        keys = new long[c];
        offsets = new long[c];
        Arrays.fill(keys, EMPTY);
    }

    public void put(long key, long offset) {
        int mask = keys.length - 1;
        int idx = (int) (hash(key) & mask);
        while (keys[idx] != EMPTY) {
            if (keys[idx] == key) {
                offsets[idx] = offset; // Update if exists
                return;
            }
            idx = (idx + 1) & mask;
        }
        keys[idx] = key;
        offsets[idx] = offset;
        size++;
        if (size * 2 >= keys.length) {
            resize();
        }
    }

    public long getOffset(long key) {
        int mask = keys.length - 1;
        int idx = (int) (hash(key) & mask);
        while (keys[idx] != EMPTY) {
            if (keys[idx] == key) return offsets[idx];
            idx = (idx + 1) & mask;
        }
        return -1L;
    }

    public int size() {
        return size;
    }

    private void resize() {
        long[] oldKeys = keys;
        long[] oldOffsets = offsets;
        
        keys = new long[oldKeys.length * 2];
        offsets = new long[keys.length];
        
        Arrays.fill(keys, EMPTY);
        size = 0;
        
        for (int i = 0; i < oldKeys.length; i++) {
            if (oldKeys[i] != EMPTY) {
                put(oldKeys[i], oldOffsets[i]);
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
