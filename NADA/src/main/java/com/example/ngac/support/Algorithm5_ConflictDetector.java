package com.example.ngac.support;

import gov.nist.ngac.pm.core.common.exception.PMException;
import gov.nist.ngac.pm.core.pap.PAP;
import gov.nist.ngac.pm.core.pap.operation.accessright.AccessRightSet;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Algorithm 5 - Conflict Detection (Definition "Conflict").
 *
 * <pre>
 * perm = (ua, ars, at) in ASSOCIATION and proh = (at, ars, s) in FP conflict iff
 *   (contains(ua(perm), s(proh)) or contains(s(proh), ua(perm)))
 *   and (contains(at(perm), at(proh)) or contains(at(proh), at(perm)))
 *   and ars(perm) n ars(proh) != empty
 *
 * Input : G_Com = &lt;&lt;U, AT, PC&gt;, &lt;ASSIGNMENT, ASSOCIATION, FP&gt;&gt;
 * Output: C_set, a set of triples (V, E, pattern)
 *
 * for each proh = (at(proh), ars(proh), s(proh)) in FP
 *   PC_proh &lt;- {pc in PC | contains(pc, s(proh)) and contains(pc, at(proh))}
 *   if PC_proh != empty
 *     for each perm = (ua(perm), ars(perm), at(perm)) in ASSOCIATION
 *       PC_c  &lt;- {pc in PC_proh | contains(pc, ua(perm)) and contains(pc, at(perm))}
 *       relS  &lt;- contains(ua(perm), s(proh))  or contains(s(proh), ua(perm))
 *       relAT &lt;- contains(at(perm), at(proh)) or contains(at(proh), at(perm))
 *       if PC_c != empty and relS and relAT and ars(perm) n ars(proh) != empty
 *         if ua(perm) = s(proh):               at equal -&gt; 9,  contains(at(perm), at(proh)) -&gt; 17, else -&gt; 15
 *         else if contains(ua(perm), s(proh)): at equal -&gt; 16, contains(at(perm), at(proh)) -&gt; 12, else -&gt; 13
 *         else:                                at equal -&gt; 14, contains(at(perm), at(proh)) -&gt; 11, else -&gt; 10
 *         for each pc in PC_c
 *           V &lt;- {ua(perm), at(perm), s(proh), at(proh), pc};  E &lt;- {perm, proh}
 *           C_set &lt;- C_set u {(V, E, pattern)}
 * </pre>
 *
 * Only active rules are considered (Definition "Rule"): a conflict is meaningful only where both
 * rules are active, i.e. in PC_c = PC(perm) n PC(proh). A conflict is reported for each policy
 * class of PC_c; that pc belongs to V and is also exposed as {@link Conflict#pc()}. Unlike a
 * redundancy, a conflict cannot be resolved by removing one of the two rules alone, so the policy
 * class indicates where the policy has to be revised. contains is reflexive (Definition
 * "Containment"), so relS / relAT also hold when the two endpoints are equal. An ASSOCIATION edge
 * is (source = ua, target = at); an FP edge is stored in its drawing direction (source = at,
 * target = s).
 */
public final class Algorithm5_ConflictDetector {

    /** One finding (V, E, pattern): E = {perm, proh}; pc is the policy class of the finding (also in V). */
    public record Conflict(Algorithm1_2_ComplementedGraphBuilder.Edge permission,
                            Algorithm1_2_ComplementedGraphBuilder.Edge prohibition,
                            Set<Long> v,
                            int pattern,
                            long pc) {

        /** ars(perm) n ars(proh): the rights both granted and denied. */
        public Set<String> overlappingRights() {
            Set<String> out = new LinkedHashSet<>();
            AccessRightSet permRights = permission.rights();
            AccessRightSet prohRights = prohibition.rights();
            if (permRights == null || prohRights == null) {
                return out;
            }
            for (String r : permRights) {
                if (prohRights.contains(r)) {
                    out.add(r);
                }
            }
            return out;
        }
    }

    public record Result(List<Conflict> conflicts) {
        public boolean isEmpty() {
            return conflicts.isEmpty();
        }
    }

    private final PAP pap;

    public Algorithm5_ConflictDetector(PAP pap) {
        this.pap = pap;
    }

    public Result detect(List<Algorithm1_2_ComplementedGraphBuilder.Edge> association,
                          List<Algorithm1_2_ComplementedGraphBuilder.Edge> fp) throws PMException {
        ContainmentIndex idx = new ContainmentIndex(pap);   // closure of contains, computed once
        Set<Conflict> cSet = new LinkedHashSet<>();                                  // C_set <- empty

        for (var proh : fp) {                                                        // for each proh in FP
            long s = proh.target();                                                  // s(proh)
            long atProh = proh.source();                                             // at(proh)
            Set<Long> pcProh = idx.activePcs(s, atProh);                             // PC_proh
            if (pcProh.isEmpty()) {                                                  // if PC_proh != empty
                continue;
            }
            for (var perm : association) {                                           // for each perm in ASSOCIATION
                long ua = perm.source();                                             // ua(perm)
                long atPerm = perm.target();                                         // at(perm)

                Set<Long> pcPerm = idx.activePcs(ua, atPerm);                        // PC_perm
                Set<Long> pcC = intersection(pcPerm, pcProh);                        // PC_c = PC_perm n PC_proh
                boolean relS = idx.contains(ua, s) || idx.contains(s, ua);
                boolean relAT = idx.contains(atPerm, atProh) || idx.contains(atProh, atPerm);

                if (!pcC.isEmpty() && relS && relAT && overlaps(perm.rights(), proh.rights())) {
                    int pattern;
                    if (ua == s) {                                                   // ua(perm) = s(proh)
                        if (atPerm == atProh) {
                            pattern = 9;
                        } else if (idx.contains(atPerm, atProh)) {
                            pattern = 17;
                        } else {
                            pattern = 15;
                        }
                    } else if (idx.contains(ua, s)) {                                    // contains(ua(perm), s(proh))
                        if (atPerm == atProh) {
                            pattern = 16;
                        } else if (idx.contains(atPerm, atProh)) {
                            pattern = 12;
                        } else {
                            pattern = 13;
                        }
                    } else {                                                         // contains(s(proh), ua(perm))
                        if (atPerm == atProh) {
                            pattern = 14;
                        } else if (idx.contains(atPerm, atProh)) {
                            pattern = 11;
                        } else {
                            pattern = 10;
                        }
                    }
                    for (long pc : pcC) {                                            // for each pc in PC_c
                        Set<Long> v = new LinkedHashSet<>(List.of(ua, atPerm, s, atProh, pc)); // V
                        cSet.add(new Conflict(perm, proh, v, pattern, pc));          // E = {perm, proh}
                    }
                }
            }
        }
        return new Result(new ArrayList<>(cSet));                                    // return C_set
    }

    /** PC_perm n PC_proh; both sets are small (one element per policy class). */
    private static Set<Long> intersection(Set<Long> a, Set<Long> b) {
        Set<Long> out = new LinkedHashSet<>();
        for (long x : a) {
            if (b.contains(x)) {
                out.add(x);
            }
        }
        return out;
    }

    private static boolean overlaps(AccessRightSet a, AccessRightSet b) {
        if (a == null || b == null) {
            return false;
        }
        for (String r : a) {
            if (b.contains(r)) {
                return true;
            }
        }
        return false;
    }
}
