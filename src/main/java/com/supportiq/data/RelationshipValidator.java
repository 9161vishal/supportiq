package com.supportiq.data;

/**
 * Validates relationships compactly without consuming excessive memory.
 */
public class RelationshipValidator {
    
    private int missingParentErrors = 0;
    private int malformedCount = 0;
    private int cycleCount = 0;

    public void incrementMissingParent() {
        missingParentErrors++;
    }

    public void incrementMalformed() {
        malformedCount++;
    }
    
    public void incrementCycle() {
        cycleCount++;
    }

    public int getMissingParentErrors() {
        return missingParentErrors;
    }

    public int getMalformedCount() {
        return malformedCount;
    }
    
    public int getCycleCount() {
        return cycleCount;
    }
    
    public void report() {
        System.out.println("Validation Report:");
        System.out.println("- Missing Parents: " + missingParentErrors);
        System.out.println("- Malformed Tweets: " + malformedCount);
        System.out.println("- Cycles Terminated: " + cycleCount);
    }
}
