package com.supportiq.data;

import java.util.Arrays;

/**
 * Memory-efficient hash set for primitive longs using linear probing.
 * Used to avoid the huge object overhead of java.util.HashSet<Long>.
 */
class LongHashSet {
    private long[] table;
    private int size;
    private static final long EMPTY = -1L;

    public LongHashSet(int capacity) {
        // power of 2 capacity for fast modulo
        int c = Integer.highestOneBit(capacity - 1) << 2; 
        if (c < 16) c = 16;
        table = new long[c];
        Arrays.fill(table, EMPTY);
    }

    public boolean add(long key) {
        int mask = table.length - 1;
        int idx = (int) (hash(key) & mask);
        while (table[idx] != EMPTY) {
            if (table[idx] == key) return false;
            idx = (idx + 1) & mask;
        }
        table[idx] = key;
        size++;
        if (size * 2 >= table.length) {
            resize();
        }
        return true;
    }

    public boolean contains(long key) {
        int mask = table.length - 1;
        int idx = (int) (hash(key) & mask);
        while (table[idx] != EMPTY) {
            if (table[idx] == key) return true;
            idx = (idx + 1) & mask;
        }
        return false;
    }

    private void resize() {
        long[] old = table;
        table = new long[old.length * 2];
        Arrays.fill(table, EMPTY);
        size = 0;
        for (long k : old) {
            if (k != EMPTY) {
                add(k);
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

    public long[] toArray() {
        long[] arr = new long[size];
        int j = 0;
        for (long k : table) {
            if (k != EMPTY) {
                arr[j++] = k;
            }
        }
        return arr;
    }

    public int size() {
        return size;
    }
}
