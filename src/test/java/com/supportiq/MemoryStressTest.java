package com.supportiq;

import com.supportiq.data.*;

public class MemoryStressTest {
    public static void main(String[] args) {
        int ROWS = 2_800_000;
        System.out.println("Starting memory test for " + ROWS + " rows...");
        
        System.gc();
        try { Thread.sleep(500); } catch (Exception e) {}
        System.gc();
        long memBefore = getUsedMemoryMB();
        System.out.println("Heap used before allocation: " + memBefore + " MB");
        
        // Populate primitive graph
        long[] children = new long[ROWS];
        long[] parents = new long[ROWS];
        for (int i = 0; i < ROWS; i++) {
            children[i] = 1000000L + i;
            parents[i] = 1000000L + (i - 1);
        }
        long memAfterGraph = getUsedMemoryMB();
        System.out.println("Heap used after graph allocation: " + memAfterGraph + " MB (Delta: " + (memAfterGraph - memBefore) + " MB)");
        
        // Populate validIds (LongHashSet)
        long[] validIdsTable = new long[2_000_000]; // 1M elements, 0.5 load factor
        for (int i = 0; i < validIdsTable.length; i++) {
            validIdsTable[i] = -1L;
        }
        long memAfterSet = getUsedMemoryMB();
        System.out.println("Heap used after validIds Set allocation: " + memAfterSet + " MB (Delta: " + (memAfterSet - memAfterGraph) + " MB)");

        // To prevent JVM from optimizing out variables
        long sum = 0;
        for (int i = 0; i < ROWS; i++) sum += children[i] + parents[i];
        for (int i = 0; i < validIdsTable.length; i++) sum += validIdsTable[i];
        System.out.println("Checksum: " + sum);
        
        System.out.println("Total delta: " + (memAfterSet - memBefore) + " MB");
    }
    
    private static long getUsedMemoryMB() {
        Runtime rt = Runtime.getRuntime();
        return (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
    }
}
