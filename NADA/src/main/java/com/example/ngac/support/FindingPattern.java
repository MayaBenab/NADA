package com.example.ngac.support;

/**
 * Common surface for a single anomaly finding that has been classified into one of the paper's
 * named visual patterns - either a {@link RedundancyPattern} (Fig. 3/4, Patterns 1-8, Algorithm 3
 * or 4) or a {@link ConflictPattern} (Fig. 5, Patterns 9-17, Algorithm 5). Lets NADA show
 * every finding - redundancies and conflicts alike - in a single list, switching to the matching
 * diagram panel ({@link PatternDiagramPanel} or {@link ConflictPatternDiagramPanel}) whichever is
 * selected.
 */
public interface FindingPattern {

    /** e.g. "Pattern 4 - Hierarchical permission redundancy" or "Pattern 14 - Potential conflict". */
    String caption();

    /** One-line summary of the real nodes involved, for the findings list (e.g. "Pilot -> Emergency vs M_Cell -> M_Zone"). */
    String describeEndpoints();
}
