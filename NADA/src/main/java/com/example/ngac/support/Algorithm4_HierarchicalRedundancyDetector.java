package com.example.ngac.support;

import gov.nist.ngac.pm.core.common.exception.PMException;
import gov.nist.ngac.pm.core.pap.PAP;
import gov.nist.ngac.pm.core.pap.operation.accessright.AccessRightSet;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Algorithm 4 - Hierarchical Redundancy Detection (Definition "Hierarchical Redundancy").
 *
 * <pre>
 * p1 is hierarchically redundant with respect to p2 if
 *   Permissions : contains(ua(p2), ua(p1)) and contains(at(p2), at(p1)) and ars(p1) subseteq ars(p2)
 *   Prohibitions: contains(s(p2),  s(p1))  and contains(at(p2), at(p1)) and ars(p1) subseteq ars(p2)
 *
 * Input : G_Com = &lt;&lt;U, AT, PC&gt;, &lt;ASSIGNMENT, ASSOCIATION, FP&gt;&gt;
 * Output: Hierarchical redundancy &lt;HPe_Set, HPr_Set&gt;, where each element is a triple (V, E, pattern)
 *
 * // Process permissions
 * for each p2 = (ua2, ars2, at2) in ASSOCIATION
 *   PC_2 &lt;- {pc in PC | contains(pc, ua2) and contains(pc, at2)}
 *   if PC_2 != empty
 *     Red_2 &lt;- {(ua1, ars1, at1) in ASSOCIATION | contains(ua2, ua1) and contains(at2, at1)
 *                and ars1 subseteq ars2 and (ua1 != ua2 or at1 != at2)}
 *     for each p1 = (ua1, ars1, at1) in Red_2
 *       pattern &lt;- 3 (ua1 != ua2 and at1 != at2) | 4 (ua1 = ua2) | 5 (otherwise)
 *       V &lt;- {ua2, at2, ua1, at1} u PC_2; E &lt;- (p2, p1)
 *       HPe_Set &lt;- HPe_Set u {(V, E, pattern)}
 *
 * // Process flattened prohibitions
 * for each p2 = (at2, ars2, s2) in FP
 *   PC_2 &lt;- {pc in PC | contains(pc, s2) and contains(pc, at2)}
 *   if PC_2 != empty
 *     Red_2 &lt;- {(at1, ars1, s1) in FP | contains(s2, s1) and contains(at2, at1)
 *                and ars1 subseteq ars2 and (s1 != s2 or at1 != at2)}
 *     for each p1 = (at1, ars1, s1) in Red_2
 *       pattern &lt;- 6 (s1 != s2 and at1 != at2) | 7 (s1 = s2) | 8 (otherwise)
 *       V &lt;- {at2, s2, at1, s1} u PC_2; E &lt;- (p2, p1)
 *       HPr_Set &lt;- HPr_Set u {(V, E, pattern)}
 *
 * return &lt;HPe_Set, HPr_Set&gt;
 * </pre>
 *
 * Only active rules are considered (Definition "Rule"). By transitivity of contains, every p1 in
 * Red_2 is active wherever p2 is, i.e. PC(p2) subseteq PC(p1), so the redundancy holds exactly in
 * the policy classes of PC_2 = PC(p2) and each pair is reported once, with PC_2 recorded in V and
 * in {@link Algorithm3_RedundancyDetector.EdgePair#pcShared()}. That inclusion may be strict:
 * p1 then remains necessary in PC(p1) \ PC(p2), so a hierarchically redundant rule can be safely
 * removed only if it is covered in every policy class in which it is active.
 * E = (p2, p1) is ordered: p1 is the redundant rule and p2 the rule that covers it.
 * An ASSOCIATION edge is (source = ua, target = at); an FP edge is stored in its drawing direction
 * (source = at, target = s).
 */
public final class Algorithm4_HierarchicalRedundancyDetector {

    public record Result(List<Algorithm3_RedundancyDetector.Finding> hierarchicalPermissionRedundancy,
                          List<Algorithm3_RedundancyDetector.Finding> hierarchicalProhibitionRedundancy) {

        public boolean isEmpty() {
            return hierarchicalPermissionRedundancy.isEmpty() && hierarchicalProhibitionRedundancy.isEmpty();
        }

        public int totalCount() {
            return hierarchicalPermissionRedundancy.size() + hierarchicalProhibitionRedundancy.size();
        }
    }

    private final PAP pap;

    public Algorithm4_HierarchicalRedundancyDetector(PAP pap) {
        this.pap = pap;
    }

    /**
     * @param assignmentEdges  the ASSIGNMENT edges of G_Com (kept for the G_Com interface; containment is
     *                         evaluated through contains)
     * @param associationEdges the ASSOCIATION edges of G_Com (ua = source, at = target)
     * @param fpEdges          the FP edges of G_Com (at = source, s = target)
     */
    public Result detect(List<Algorithm1_2_ComplementedGraphBuilder.Edge> assignmentEdges,
                          List<Algorithm1_2_ComplementedGraphBuilder.Edge> associationEdges,
                          List<Algorithm1_2_ComplementedGraphBuilder.Edge> fpEdges) throws PMException {
        ContainmentIndex idx = new ContainmentIndex(pap);   // closure of contains, computed once
        Set<Algorithm3_RedundancyDetector.Finding> hPe = new LinkedHashSet<>();     // HPe_Set <- empty
        Set<Algorithm3_RedundancyDetector.Finding> hPr = new LinkedHashSet<>();     // HPr_Set <- empty

        // Process permissions
        for (var p2 : associationEdges) {                                          // for each p2 = (ua2, ars2, at2)
            long ua2 = p2.source();
            long at2 = p2.target();
            Set<Long> pc2 = idx.activePcs(ua2, at2);                               // PC_2
            if (pc2.isEmpty()) {                                                   // if PC_2 != empty
                continue;
            }
            List<Algorithm1_2_ComplementedGraphBuilder.Edge> red2 = new ArrayList<>(); // Red_2
            for (var p1 : associationEdges) {
                long ua1 = p1.source();
                long at1 = p1.target();
                if (idx.contains(ua2, ua1) && idx.contains(at2, at1)
                        && subset(p1.rights(), p2.rights())
                        && (ua1 != ua2 || at1 != at2)) {
                    red2.add(p1);
                }
            }
            for (var p1 : red2) {                                                  // for each p1 in Red_2
                long ua1 = p1.source();
                long at1 = p1.target();
                int pattern;
                if (ua1 != ua2 && at1 != at2) {
                    pattern = 3;
                } else if (ua1 == ua2) {
                    pattern = 4;
                } else {
                    pattern = 5;
                }
                Set<Long> v = vertices(pc2, ua2, at2, ua1, at1);                   // V <- {ua2, at2, ua1, at1} u PC_2
                var e = new Algorithm3_RedundancyDetector.EdgePair(p2, p1, pc2);   // E <- (p2, p1)
                hPe.add(new Algorithm3_RedundancyDetector.Finding(v, e, pattern));
            }
        }

        // Process flattened prohibitions
        for (var p2 : fpEdges) {                                                   // for each p2 = (at2, ars2, s2)
            long s2 = p2.target();
            long at2 = p2.source();
            Set<Long> pc2 = idx.activePcs(s2, at2);                                // PC_2
            if (pc2.isEmpty()) {                                                   // if PC_2 != empty
                continue;
            }
            List<Algorithm1_2_ComplementedGraphBuilder.Edge> red2 = new ArrayList<>(); // Red_2
            for (var p1 : fpEdges) {
                long s1 = p1.target();
                long at1 = p1.source();
                if (idx.contains(s2, s1) && idx.contains(at2, at1)
                        && subset(p1.rights(), p2.rights())
                        && (s1 != s2 || at1 != at2)) {
                    red2.add(p1);
                }
            }
            for (var p1 : red2) {                                                  // for each p1 in Red_2
                long s1 = p1.target();
                long at1 = p1.source();
                int pattern;
                if (s1 != s2 && at1 != at2) {
                    pattern = 6;
                } else if (s1 == s2) {
                    pattern = 7;
                } else {
                    pattern = 8;
                }
                Set<Long> v = vertices(pc2, at2, s2, at1, s1);                     // V <- {at2, s2, at1, s1} u PC_2
                var e = new Algorithm3_RedundancyDetector.EdgePair(p2, p1, pc2);   // E <- (p2, p1)
                hPr.add(new Algorithm3_RedundancyDetector.Finding(v, e, pattern));
            }
        }

        return new Result(new ArrayList<>(hPe), new ArrayList<>(hPr));           // return <HPe_Set, HPr_Set>
    }

    /** ars1 subseteq ars2. */
    private static boolean subset(AccessRightSet ars1, AccessRightSet ars2) {
        return ars1 != null && ars2 != null && ars2.containsAll(ars1);
    }

    /** V = {ids...} u policyClasses. */
    private static Set<Long> vertices(Set<Long> policyClasses, long... ids) {
        Set<Long> v = new LinkedHashSet<>();
        for (long id : ids) {
            v.add(id);
        }
        v.addAll(policyClasses);
        return v;
    }

}
