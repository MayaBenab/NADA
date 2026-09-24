package com.example.ngac.support;

/**
 * A single conflict finding (Algorithm 5), classified into one of the paper's Fig. 5 patterns
 * (9-17), with the abstract ua1/at1/ua2/at2/"u or ua1" labels replaced by this finding's real
 * node names.
 *
 * <p>Definition (Conflict) relates a permission {@code perm = (a1, ars(perm), a2)} and a
 * flattened prohibition {@code proh = (b2, ars(proh), b1)} on each side independently - the
 * subject side ({@code a1} vs {@code b1}) and the object side ({@code a2} vs {@code b2}) - and
 * each side is, independently, one of exactly three relations: equal, "a contains b", or "b
 * contains a". The paper gives the mapping directly as its own "Table: Conflict pattern
 * assignment" (3x3 = 9 patterns, 9 through 17):
 *
 * <pre>
 *                    a2=b2       contains(a2,b2)   contains(b2,a2)
 * a1=b1              9           17                15
 * contains(a1,b1)    16          12                13
 * contains(b1,a1)    14          11                10
 * </pre>
 *
 * This is the mapping of the paper's Algorithm 5 (a1 = ua(perm), b1 = s(proh), a2 = at(perm),
 * b2 = at(proh)); the pattern number itself is computed by Algorithm5_ConflictDetector.
 */
public record ConflictPattern(
        int number,
        boolean subjectShared,
        boolean objectShared,
        boolean subjectAContainsB,
        boolean objectAContainsB,
        String a1Name,
        String b1Name,
        String a2Name,
        String b2Name,
        String permRights,
        String prohRights) implements FindingPattern {

    @Override
    public String caption() {
        return "Pattern " + number + " - Potential conflict";
    }

    @Override
    public String describeEndpoints() {
        return a1Name + " -> " + a2Name + "  (permission)   vs   " + b1Name + " -> " + b2Name + "  (prohibition)";
    }
}
