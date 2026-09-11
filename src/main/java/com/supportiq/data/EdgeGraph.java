package com.supportiq.data;

import java.util.Arrays;

/**
 * Memory-efficient DAG using primitive parallel arrays.
 * Models directed edges: child -> parent.
 */
class EdgeGraph {
    private long[] children;
    private long[] parents;
    private int size;
    private boolean isSortedByChild = false;
    private boolean isSortedByParent = false;

    public EdgeGraph(int capacity) {
        children = new long[capacity];
        parents = new long[capacity];
        size = 0;
    }

    public void addEdge(long child, long parent) {
        if (size == children.length) {
            children = Arrays.copyOf(children, children.length * 2);
            parents = Arrays.copyOf(parents, parents.length * 2);
        }
        children[size] = child;
        parents[size] = parent;
        size++;
        isSortedByChild = false;
        isSortedByParent = false;
    }
    
    public int size() {
        return size;
    }

    /**
     * Sorts the parallel arrays by child ID for O(log N) lookup of a parent.
     */
    public void sortByChild() {
        if (isSortedByChild) return;
        quickSort(children, parents, 0, size - 1);
        isSortedByChild = true;
        isSortedByParent = false;
    }

    /**
     * Sorts the parallel arrays by parent ID for O(log N) lookup of children.
     */
    public void sortByParent() {
        if (isSortedByParent) return;
        quickSort(parents, children, 0, size - 1);
        isSortedByParent = true;
        isSortedByChild = false;
    }

    /**
     * Assumes graph is sorted by child. Finds the exact parent of this child.
     * Returns -1 if not found.
     */
    public long getParent(long childId) {
        if (!isSortedByChild) throw new IllegalStateException("Must sortByChild() first");
        int idx = Arrays.binarySearch(children, 0, size, childId);
        if (idx >= 0) {
            return parents[idx];
        }
        return -1L;
    }

    /**
     * Assumes graph is sorted by parent. Finds all children for this parent.
     * Fills the provided LongArray with child IDs.
     */
    public void getChildren(long parentId, LongArray result) {
        if (!isSortedByParent) throw new IllegalStateException("Must sortByParent() first");
        
        int low = 0;
        int high = size - 1;
        int firstIdx = -1;
        
        // Find the FIRST occurrence of parentId
        while (low <= high) {
            int mid = low + (high - low) / 2;
            if (parents[mid] == parentId) {
                firstIdx = mid;
                high = mid - 1; 
            } else if (parents[mid] > parentId) {
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }
        
        if (firstIdx == -1) return; // No children found
        
        // Add all children for this parent
        for (int i = firstIdx; i < size && parents[i] == parentId; i++) {
            result.add(children[i]);
        }
    }

    private void quickSort(long[] keys, long[] values, int left, int right) {
        if (left >= right) return;
        long pivotKey = keys[left + (right - left) / 2];
        long pivotVal = values[left + (right - left) / 2];
        int i = left;
        int j = right;
        
        while (i <= j) {
            while (keys[i] < pivotKey || (keys[i] == pivotKey && values[i] < pivotVal)) i++;
            while (keys[j] > pivotKey || (keys[j] == pivotKey && values[j] > pivotVal)) j--;
            if (i <= j) {
                long tempKey = keys[i];
                keys[i] = keys[j];
                keys[j] = tempKey;
                
                long tempVal = values[i];
                values[i] = values[j];
                values[j] = tempVal;
                
                i++;
                j--;
            }
        }
        quickSort(keys, values, left, j);
        quickSort(keys, values, i, right);
    }
}
