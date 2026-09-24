package com.example.ngac.support;

/**
 * A single redundancy finding (from Algorithm 3 or Algorithm 4), classified into one of the
 * eight visual patterns of the paper's Fig. 3 ("Full or Partial Redundancies") and Fig. 4
 * ("Hierarchical redundancies"), with the abstract ua1/at1/ua2/at2/"u or ua" labels replaced by
 * the real node names of this specific finding.
 *
 * <pre>
 * Pattern 1 (Fig. 3a): two associations, same (ua, at) - Algorithm 3, permission side.
 * Pattern 2 (Fig. 3b): two flattened prohibitions, same (at, s) - Algorithm 3, prohibition side.
 * Pattern 3 (Fig. 4a): two associations, ua and at both differ (both sides hierarchically related).
 * Pattern 4 (Fig. 4a): two associations, same ua, at differs (hierarchy only on the object side).
 * Pattern 5 (Fig. 4a): two associations, ua differs, same at (hierarchy only on the subject side).
 * Pattern 6 (Fig. 4b): two flattened prohibitions, s and at both differ.
 * Pattern 7 (Fig. 4b): two flattened prohibitions, same s, at differs.
 * Pattern 8 (Fig. 4b): two flattened prohibitions, s differs, same at.
 * </pre>
 *
 * "Top" (p1) is always the dominated/redundant rule, "bottom" (p2) the dominant rule that already
 * covers it - matching the paper's figures, where p1 sits above p2 and the solid arrow (an
 * assignment/containment path) points from the contained (p1's side) down to the container
 * (p2's side). When a side is shared (patterns 1, 2, 4, 5, 7, 8) there is only one node on that
 * side and no containment arrow is drawn for it.
 *
 * {@code pcNote}, when non-null/non-blank, is Definition (Rule)'s "active via PC" text (only
 * meaningful for permission patterns, 1/3/4/5) - shown in the diagram itself, not just in the
 * results-dialog text, so it isn't missed there.
 */
public record RedundancyPattern(
        int number,
        String title,
        boolean isProhibition,
        boolean leftShared,
        boolean rightShared,
        String leftTop,
        String leftBottom,
        String rightTop,
        String rightBottom,
        String rightsTop,
        String rightsBottom,
        String pcNote) implements FindingPattern {

    /** "ua" for a permission pattern, "u or ua" for a prohibition pattern - matches Fig. 3/4. */
    public String leftAxisLabel() {
        return isProhibition ? "u or ua" : "ua";
    }

    public String rightAxisLabel() {
        return "at";
    }

    @Override
    public String caption() {
        return "Pattern " + number + " - " + title;
    }

    @Override
    public String describeEndpoints() {
        String left = leftShared ? leftTop() : (leftTop() + "/" + leftBottom());
        String right = rightShared ? rightTop() : (rightTop() + "/" + rightBottom());
        return left + " -> " + right;
    }
}
