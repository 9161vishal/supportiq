package com.supportiq.data;

import java.util.Arrays;

/**
 * Memory-efficient dynamic array for primitive longs.
 */
class LongArray {
    private long[] data;
    private int size;

    public LongArray(int capacity) {
        data = new long[capacity];
        size = 0;
    }

    public void add(long val) {
        if (size == data.length) {
            data = Arrays.copyOf(data, data.length * 2);
        }
        data[size++] = val;
    }

    public void sort() {
        Arrays.sort(data, 0, size);
    }

    public void clear() {
        size = 0;
    }

    public boolean contains(long val) {
        return Arrays.binarySearch(data, 0, size, val) >= 0;
    }

    public int size() {
        return size;
    }

    public long get(int index) {
        return data[index];
    }
}
