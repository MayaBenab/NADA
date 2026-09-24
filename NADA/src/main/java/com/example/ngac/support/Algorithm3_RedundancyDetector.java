package com.example.ngac.support;

import gov.nist.ngac.pm.core.common.exception.PMException;
import gov.nist.ngac.pm.core.pap.PAP;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Algorithm 3 - Full/Partial Redundancy Detection (Definition "Full and Partial Redundancy").
 *
 * <pre>
 * Definition (Full and Partial Redundancy). Let p1, p2 be two distinct rules of the same type
 * (both permission rules or both prohibition rules).
 *   - p1, p2 are fully redundant iff
 *       ua(p1) = ua(p2) and at(p1) = at(p2) and ars(p1) = ars(p2)
 *     (or with s in place of ua for prohibitions).
 *   - p1, p2 are partially redundant iff
 *       ua(p1) = ua(p2) and at(p1) = at(p2) and ars(p1) != ars(p2) and ars(p1) n ars(p2) != empty
 *     (or with s in place of ua for prohibitions).
 *
 * Input : G_Com = &lt;&lt;U, AT, PC&gt;, &lt;ASSIGNMENT, ASSOCIATION, FP&gt;&gt;
 * Output: FullPerm_set, PartialPerm_set, FullProh_set, PartialProh_set:
 *         sets of triples (V, E, pattern), where &lt;V, E&gt; is the subgraph of G_Com
 *         matching Pattern pattern
 *
 * // Process permissions
 * for each p1 = (ua(p1), ars(p1), at(p1)) in ASSOCIATION
 *   PC_1 &lt;- {pc in PC | contains(pc, ua(p1)) and contains(pc, at(p1))}
 *   if PC_1 != empty
 *     for each p2 in ASSOCIATION, p2 after p1
 *       if ua(p1) = ua(p2) and at(p1) = at(p2)
 *         V &lt;- {ua(p1), at(p1)} u PC_1; E &lt;- {p1, p2}; pattern &lt;- 1
 *         if ars(p1) = ars(p2)                                       -&gt; FullPerm_set
 *         else if ars(p1) != ars(p2) and ars(p1) n ars(p2) != empty  -&gt; PartialPerm_set
 *
 * // Process prohibitions
 * for each p1 = (at(p1), ars(p1), s(p1)) in FP
 *   PC_1 &lt;- {pc in PC | contains(pc, s(p1)) and contains(pc, at(p1))}
 *   if PC_1 != empty
 *     for each p2 in FP, p2 after p1
 *       if s(p1) = s(p2) and at(p1) = at(p2)
 *         V &lt;- {s(p1), at(p1)} u PC_1; E &lt;- {p1, p2}; pattern &lt;- 2
 *         if ars(p1) = ars(p2)                                       -&gt; FullProh_set
 *         else if ars(p1) != ars(p2) and ars(p1) n ars(p2) != empty  -&gt; PartialProh_set
 * </pre>
 *
 * Definition (Rule): a rule is active in pc iff pc contains both its endpoints, and PC(p) is the
 * set of policy classes in which p is active; only active rules are considered. Since p1 and p2
 * share the same endpoints, PC(p2) = PC(p1) = PC_1: the redundancy holds in every policy class of
 * PC_1 and never in only some of them, so each pair is reported once, with PC_1 recorded in V.
 *
 * "p2 after p1" is the order of the edge lists (declaration order), so each unordered pair of
 * distinct rules is compared exactly once. FP is a multiset (Algorithm 1): two distinct
 * prohibitions yielding the same edge are two elements of the list, so a full prohibition
 * redundancy is observable. An ASSOCIATION edge is (source = ua, target = at); an FP edge is
 * stored in its drawing direction (source = at, target = s).
 *
 * The policy classes of PC_1 belong to V and are also recorded in {@link EdgePair#pcShared()}
 * for display.
 */
public final class Algorithm3_RedundancyDetector {

    /** Pattern numbers used by Algorithm 3. */
    public static final int PATTERN_PERMISSION = 1;
    public static final int PATTERN_PROHIBITION = 2;

    /**
     * A pair of rules. For Algorithm 3 it is E = {p1, p2}; for Algorithm 4 it is the ordered pair
     * E = (p2, p1) = (dominant rule, dominated rule). pcShared holds the policy class of the
     * finding (the pc that is also in V).
     */
    public record EdgePair(Algorithm1_2_ComplementedGraphBuilder.Edge first, Algorithm1_2_ComplementedGraphBuilder.Edge second,
                            Set<Long> pcShared) {
        /** Convenience constructor for pairs with no policy class attached. */
        public EdgePair(Algorithm1_2_ComplementedGraphBuilder.Edge first, Algorithm1_2_ComplementedGraphBuilder.Edge second) {
            this(first, second, Set.of());
        }
    }

    /** One finding (V, E, pattern). */
    public record Finding(Set<Long> v, EdgePair e, int pattern) { }

    public record Result(List<Finding> fullPermissionRedundancy,
                          List<Finding> partialPermissionRedundancy,
                          List<Finding> fullProhibitionRedundancy,
                          List<Finding> partialProhibitionRedundancy) {

        public boolean isEmpty() {
            return totalCount() == 0;
        }

        public int totalCount() {
            return fullPermissionRedundancy.size() + partialPermissionRedundancy.size()
                    + fullProhibitionRedundancy.size() + partialProhibitionRedundancy.size();
        }
    }

    private Algorithm3_RedundancyDetector() {
    }

    public static Result detect(PAP pap,
                                 List<Algorithm1_2_ComplementedGraphBuilder.Edge> association,
                                 List<Algorithm1_2_ComplementedGraphBuilder.Edge> fp) throws PMException {
        ContainmentIndex idx = new ContainmentIndex(pap);   // closure of contains, computed once
        List<Finding> fullPerm = new ArrayList<>();       // FullPerm_set <- empty
        List<Finding> partialPerm = new ArrayList<>();    // PartialPerm_set <- empty
        List<Finding> fullProh = new ArrayList<>();       // FullProh_set <- empty
        List<Finding> partialProh = new ArrayList<>();    // PartialProh_set <- empty

        // Process permissions: p = (ua, ars, at), ua = source, at = target.
        for (int i = 0; i < association.size(); i++) {                       // for each p1 in ASSOCIATION
            var p1 = association.get(i);
            long ua = p1.source();
            long at = p1.target();
            Set<Long> pc1 = idx.activePcs(ua, at);                           // PC_1
            if (pc1.isEmpty()) {                                             // if PC_1 != empty
                continue;
            }
            for (int j = i + 1; j < association.size(); j++) {               // for each p2 after p1
                var p2 = association.get(j);
                if (ua == p2.source() && at == p2.target()) {                // ua(p1)=ua(p2), at(p1)=at(p2)
                    Set<Long> v = vertices(ua, at, pc1);                     // V <- {ua(p1), at(p1)} u PC_1
                    EdgePair e = new EdgePair(p1, p2, pc1);                  // E <- {p1, p2}
                    classify(new Finding(v, e, PATTERN_PERMISSION),          // pattern <- 1
                            fullPerm, partialPerm);
                }
            }
        }

        // Process prohibitions: p = (at, ars, s), stored as source = at, target = s.
        for (int i = 0; i < fp.size(); i++) {                                // for each p1 in FP
            var p1 = fp.get(i);
            long s = p1.target();
            long at = p1.source();
            Set<Long> pc1 = idx.activePcs(s, at);                            // PC_1
            if (pc1.isEmpty()) {                                             // if PC_1 != empty
                continue;
            }
            for (int j = i + 1; j < fp.size(); j++) {                        // for each p2 after p1
                var p2 = fp.get(j);
                if (s == p2.target() && at == p2.source()) {                 // s(p1)=s(p2), at(p1)=at(p2)
                    Set<Long> v = vertices(s, at, pc1);                      // V <- {s(p1), at(p1)} u PC_1
                    EdgePair e = new EdgePair(p1, p2, pc1);                  // E <- {p1, p2}
                    classify(new Finding(v, e, PATTERN_PROHIBITION),         // pattern <- 2
                            fullProh, partialProh);
                }
            }
        }

        return new Result(fullPerm, partialPerm, fullProh, partialProh);
    }

    /**
     * if ars(p1) = ars(p2) -&gt; full;
     * else if ars(p1) != ars(p2) and ars(p1) n ars(p2) != empty -&gt; partial.
     */
    private static void classify(Finding f, List<Finding> fullOut, List<Finding> partialOut) {
        var ars1 = f.e().first().rights();
        var ars2 = f.e().second().rights();
        if (ars1 == null || ars2 == null) {
            return;
        }
        if (ars1.equals(ars2)) {
            fullOut.add(f);
        } else if (intersects(ars1, ars2)) {                                 // ars1 != ars2 already holds here
            partialOut.add(f);
        }
    }

    /** V = {a, b} u policyClasses. */
    private static Set<Long> vertices(long a, long b, Set<Long> policyClasses) {
        Set<Long> v = new LinkedHashSet<>();
        v.add(a);
        v.add(b);
        v.addAll(policyClasses);
        return v;
    }

    private static boolean intersects(Iterable<String> a, Collection<String> b) {
        for (String right : a) {
            if (b.contains(right)) {
                return true;
            }
        }
        return false;
    }
}
