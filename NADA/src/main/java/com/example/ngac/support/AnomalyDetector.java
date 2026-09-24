package com.example.ngac.support;

import gov.nist.ngac.pm.core.common.exception.PMException;
import gov.nist.ngac.pm.core.common.graph.node.Node;
import gov.nist.ngac.pm.core.common.graph.node.NodeType;
import gov.nist.ngac.pm.core.pap.PAP;
import gov.nist.ngac.pm.core.pap.operation.accessright.AccessRightSet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Anomaly detection over G_Com. Wires in Algorithm 3 (Full/Partial Redundancy Detection, see
 * Algorithm3_RedundancyDetector), Algorithm 4 (Hierarchical Redundancy Detection, see
 * Algorithm4_HierarchicalRedundancyDetector) and Algorithm 5 (Conflict Detection, see Algorithm5_ConflictDetector) - a
 * permission and a prohibition whose subject/object sides are related by containment (in either
 * direction) or exactly equal, and whose access rights overlap. (Algorithm numbers match the
 * paper's own narrative order: Algorithm 1 is Complemented Graph Construction and Algorithm 2 is
 * Resolving the Target Prohibition Set - both in Algorithm1_2_ComplementedGraphBuilder - so the three anomaly
 * algorithms are numbered 3, 4, 5.) Also classifies every redundancy finding into a
 * {@link RedundancyPattern} (Fig. 3/4's eight patterns, instantiated with this policy's real node
 * names) so NADA can show the matching diagram for whichever finding is selected; conflicts
 * (Algorithm 5) are reported in the summary and highlighted on the graph the same way, and
 * classified into Fig. 5's patterns (9-17) exactly like redundancies are classified into Fig.
 * 3/4's patterns (1-8) - see {@link ConflictPattern} for the classification rule (the paper's own
 * "Table: Conflict pattern assignment").
 */
public final class AnomalyDetector {

    public record Result(Set<Long> anomalousNodes,
                          Set<Algorithm1_2_ComplementedGraphBuilder.Edge> anomalousEdges,
                          List<String> messages,
                          List<FindingPattern> patterns) {
        public static Result empty() {
            return new Result(Set.of(), Set.of(), List.of("No anomaly detection implemented yet."), List.of());
        }
    }

    public Result detect(PAP pap,
                          List<Algorithm1_2_ComplementedGraphBuilder.Edge> g,
                          List<Algorithm1_2_ComplementedGraphBuilder.Edge> fp,
                          List<Algorithm1_2_ComplementedGraphBuilder.Edge> gCom) throws PMException {
        List<Algorithm1_2_ComplementedGraphBuilder.Edge> assignments = g.stream()
                .filter(e -> e.kind() == Algorithm1_2_ComplementedGraphBuilder.EdgeKind.ASSIGNMENT)
                .toList();
        List<Algorithm1_2_ComplementedGraphBuilder.Edge> associations = g.stream()
                .filter(e -> e.kind() == Algorithm1_2_ComplementedGraphBuilder.EdgeKind.ASSOCIATION)
                .toList();

        Algorithm3_RedundancyDetector.Result fullPartial = Algorithm3_RedundancyDetector.detect(pap, associations, fp);
        Algorithm4_HierarchicalRedundancyDetector.Result hierarchical =
                new Algorithm4_HierarchicalRedundancyDetector(pap).detect(assignments, associations, fp);
        Algorithm5_ConflictDetector.Result conflicts = new Algorithm5_ConflictDetector(pap).detect(associations, fp);

        List<String> messages = new ArrayList<>();
        messages.add("Algorithm 3 (full/partial redundancy): " + fullPartial.totalCount() + " pair(s) - "
                + fullPartial.fullPermissionRedundancy().size() + " full permission, "
                + fullPartial.partialPermissionRedundancy().size() + " partial permission, "
                + fullPartial.fullProhibitionRedundancy().size() + " full prohibition, "
                + fullPartial.partialProhibitionRedundancy().size() + " partial prohibition.");
        messages.add("Algorithm 4 (hierarchical redundancy): " + hierarchical.totalCount() + " pair(s) - "
                + hierarchical.hierarchicalPermissionRedundancy().size() + " permission, "
                + hierarchical.hierarchicalProhibitionRedundancy().size() + " prohibition.");
        messages.add("Algorithm 5 (potential conflicts): " + conflicts.conflicts().size() + " pair(s) - "
                + "a permission and a prohibition overlap on related (or identical) subjects/objects.");

        if (fullPartial.isEmpty() && hierarchical.isEmpty() && conflicts.isEmpty()) {
            messages.add("No redundancy or conflict found (Algorithm 3, 4 or 5).");
            return new Result(Set.of(), Set.of(), messages, List.of());
        }

        Set<Long> nodes = new HashSet<>();
        Set<Algorithm1_2_ComplementedGraphBuilder.Edge> edges = new HashSet<>();
        List<FindingPattern> patterns = new ArrayList<>();
        messages.add("");

        collectSameNode(fullPartial.fullPermissionRedundancy(), "Full permission redundancy", false, pap, nodes, edges, messages, patterns);
        collectSameNode(fullPartial.partialPermissionRedundancy(), "Partial permission redundancy", false, pap, nodes, edges, messages, patterns);
        collectSameNode(fullPartial.fullProhibitionRedundancy(), "Full prohibition redundancy", true, pap, nodes, edges, messages, patterns);
        collectSameNode(fullPartial.partialProhibitionRedundancy(), "Partial prohibition redundancy", true, pap, nodes, edges, messages, patterns);
        collectHierarchical(hierarchical.hierarchicalPermissionRedundancy(), "Hierarchical permission redundancy", false, pap, nodes, edges, messages, patterns);
        collectHierarchical(hierarchical.hierarchicalProhibitionRedundancy(), "Hierarchical prohibition redundancy", true, pap, nodes, edges, messages, patterns);
        collectConflicts(conflicts.conflicts(), pap, nodes, edges, messages, patterns);

        return new Result(nodes, edges, messages, patterns);
    }

    /**
     * Algorithm 5: a permission and a prohibition are named separately (like Algorithm 4's
     * findings), since a conflict pair almost never sits at the exact same (ua,at)/(s,at) node -
     * plus the overlapping rights, which are the actual rights simultaneously granted and denied.
     * Each conflict is also classified into one of Fig. 5's nine patterns (see {@link ConflictPattern}).
     */
    private void collectConflicts(List<Algorithm5_ConflictDetector.Conflict> conflictList,
                                   PAP pap,
                                   Set<Long> nodes,
                                   Set<Algorithm1_2_ComplementedGraphBuilder.Edge> edges,
                                   List<String> messages,
                                   List<FindingPattern> patterns) throws PMException {
        for (Algorithm5_ConflictDetector.Conflict c : conflictList) {
            Algorithm1_2_ComplementedGraphBuilder.Edge perm = c.permission();
            Algorithm1_2_ComplementedGraphBuilder.Edge proh = c.prohibition();
            edges.add(perm);
            edges.add(proh);
            nodes.addAll(c.v());                                             // V
            messages.add("  [Potential conflict] " + nameOf(pap, perm.source()) + " -> " + nameOf(pap, perm.target())
                    + " grants " + perm.rights() + ", but " + nameOf(pap, proh.target()) + " -> "
                    + nameOf(pap, proh.source()) + " denies " + proh.rights() + ruleName(proh)
                    + "  (overlap: " + c.overlappingRights() + ")  (conflicting in PC: "
                    + nameOf(pap, c.pc()) + ")");
            patterns.add(classifyConflict(c, pap));
        }
    }

    /**
     * Classifies one {@link Algorithm5_ConflictDetector.Conflict} into its Fig. 5 pattern number (9-17), by
     * independently resolving the subject side ({@code a1} vs {@code b1}) and the object side
     * ({@code a2} vs {@code b2}) into one of "equal", "a contains b" or "b contains a" - see the
     * table in {@link ConflictPattern}'s javadoc for the full 3x3 mapping.
     */
    private ConflictPattern classifyConflict(Algorithm5_ConflictDetector.Conflict c, PAP pap) throws PMException {
        Algorithm1_2_ComplementedGraphBuilder.Edge perm = c.permission();
        Algorithm1_2_ComplementedGraphBuilder.Edge proh = c.prohibition();
        long a1 = perm.source();
        long a2 = perm.target();
        long b2 = proh.source(); // FP edge stored (at, ars, s): source = at = b2
        long b1 = proh.target(); // target = s = b1

        boolean subjectShared = (a1 == b1);
        boolean objectShared = (a2 == b2);
        // Meaningful only when the corresponding side isn't shared - a conflict was only reported
        // because at least one of "equal / a contains b / b contains a" held on each side, so once
        // "equal" is ruled out, exactly one of the two containment directions must hold.
        boolean subjectAContainsB = !subjectShared && contains(pap, a1, b1);
        boolean objectAContainsB = !objectShared && contains(pap, a2, b2);

        int number = c.pattern();                                            // pattern computed by Algorithm 5

        return new ConflictPattern(number, subjectShared, objectShared, subjectAContainsB, objectAContainsB,
                nameOf(pap, a1), nameOf(pap, b1), nameOf(pap, a2), nameOf(pap, b2),
                rightsLabel(perm.rights()), rightsLabel(proh.rights()));
    }

    /**
     * Definition (Containment): contains(x,x) for every policy element (U, UA, OA incl. O, PC); otherwise
     * contains(container, contained) iff contained is an ascendant of container in the sense used
     * throughout this project - matches the identical helper in Algorithm4_HierarchicalRedundancyDetector and
     * the closure() check inside Algorithm5_ConflictDetector, kept local here since it's only needed for
     * pattern classification (detection itself is already done by the time this runs).
     */
    private boolean contains(PAP pap, long container, long contained) throws PMException {
        if (container == contained) {
            return true; // Definition (Containment), rule 1: reflexive on U, UA, OA (incl. O) and PC
        }
        return pap.query().graph().isAscendant(contained, container);
    }

    /**
     * Algorithm 3: p1 and p2 share the same (source, target) - one line is enough, Pattern 1/2.
     * Each finding carries the policy class pc (in V) through which both rules are active
     * (Definition (Rule)); it is reported for permissions and prohibitions alike.
     */
    private void collectSameNode(List<Algorithm3_RedundancyDetector.Finding> findings,
                                  String label,
                                  boolean isProhibition,
                                  PAP pap,
                                  Set<Long> nodes,
                                  Set<Algorithm1_2_ComplementedGraphBuilder.Edge> edges,
                                  List<String> messages,
                                  List<FindingPattern> patterns) {
        for (Algorithm3_RedundancyDetector.Finding finding : findings) {
            Algorithm3_RedundancyDetector.EdgePair pair = finding.e();       // E
            Algorithm1_2_ComplementedGraphBuilder.Edge p1 = pair.first();
            Algorithm1_2_ComplementedGraphBuilder.Edge p2 = pair.second();
            edges.add(p1);
            edges.add(p2);
            nodes.addAll(finding.v());                                       // V
            String pcSuffix = !pair.pcShared().isEmpty()
                    ? "  (redundant in PC: " + pcNames(pair.pcShared(), pap) + ")"
                    : "";
            messages.add("  [" + label + "] " + nameOf(pap, p1.source()) + " -> " + nameOf(pap, p1.target())
                    + " : " + p1.rights() + ruleName(p1) + " vs " + p2.rights() + ruleName(p2) + pcSuffix);
            patterns.add(classifySameNode(finding, label, isProhibition, pap));
        }
    }

    private String pcNames(Set<Long> pcIds, PAP pap) {
        List<String> names = new ArrayList<>();
        for (long id : pcIds) {
            names.add(nameOf(pap, id));
        }
        return String.join(", ", names);
    }

    /**
     * Algorithm 4: p1 (dominated, "second()") and p2 (dominant, "first()") sit at different
     * (source, target) pairs, so both need to be named for the message to make sense. Pattern
     * 3-8, depending on which side(s) actually differ. Each finding carries the policy class pc
     * (in V) in which the dominant rule p2 is active - and hence, by transitivity of contains,
     * the dominated rule p1 as well - so p1 is redundant in that pc.
     */
    private void collectHierarchical(List<Algorithm3_RedundancyDetector.Finding> findings,
                                      String label,
                                      boolean isProhibition,
                                      PAP pap,
                                      Set<Long> nodes,
                                      Set<Algorithm1_2_ComplementedGraphBuilder.Edge> edges,
                                      List<String> messages,
                                      List<FindingPattern> patterns) throws PMException {
        for (Algorithm3_RedundancyDetector.Finding finding : findings) {
            Algorithm3_RedundancyDetector.EdgePair pair = finding.e();       // E = (p2, p1)
            Algorithm1_2_ComplementedGraphBuilder.Edge dominant = pair.first();
            Algorithm1_2_ComplementedGraphBuilder.Edge dominated = pair.second();
            edges.add(dominant);
            edges.add(dominated);
            nodes.addAll(finding.v());                                       // V
            String pcSuffix = !pair.pcShared().isEmpty()
                    ? "  (redundant in PC: " + pcNames(pair.pcShared(), pap) + ")"
                    : "";
            messages.add("  [" + label + "] " + nameOf(pap, dominated.source()) + " -> " + nameOf(pap, dominated.target())
                    + " : " + dominated.rights() + ruleName(dominated) + "  (already covered by "
                    + nameOf(pap, dominant.source()) + " -> " + nameOf(pap, dominant.target())
                    + " : " + dominant.rights() + ruleName(dominant) + ")" + pcSuffix);
            patterns.add(classifyHierarchical(finding, label, isProhibition, pap));
        }
    }

    /** Pattern 1 (permission) or Pattern 2 (prohibition): both sides are shared, no containment arrow. */
    private RedundancyPattern classifySameNode(Algorithm3_RedundancyDetector.Finding finding, String title,
                                                boolean isProhibition, PAP pap) {
        Algorithm3_RedundancyDetector.EdgePair pair = finding.e();
        Algorithm1_2_ComplementedGraphBuilder.Edge p1 = pair.first();
        Algorithm1_2_ComplementedGraphBuilder.Edge p2 = pair.second();
        String leftName = nameOf(pap, p1.source());
        String rightName = nameOf(pap, p1.target());
        int number = finding.pattern();                                      // pattern computed by Algorithm 3
        String pcNote = !pair.pcShared().isEmpty()
                ? "redundant in PC: " + pcNames(pair.pcShared(), pap)
                : null;
        return new RedundancyPattern(number, title, isProhibition, true, true,
                leftName, leftName, rightName, rightName,
                rightsLabel(p1.rights()), rightsLabel(p2.rights()), pcNote);
    }

    /** Pattern 3/6 (both sides differ), 4/7 (left/subject shared) or 5/8 (right/at shared). */
    private RedundancyPattern classifyHierarchical(Algorithm3_RedundancyDetector.Finding finding, String title,
                                                    boolean isProhibition, PAP pap) throws PMException {
        Algorithm3_RedundancyDetector.EdgePair pair = finding.e();
        Algorithm1_2_ComplementedGraphBuilder.Edge dominant = pair.first();
        Algorithm1_2_ComplementedGraphBuilder.Edge dominated = pair.second();
        long leftDominant, leftDominated, rightDominant, rightDominated;
        if (isProhibition) {
            // FP edge is stored (at, ars, s): source = at, target = s.
            leftDominant = dominant.target();
            leftDominated = dominated.target();
            rightDominant = dominant.source();
            rightDominated = dominated.source();
        } else {
            leftDominant = dominant.source();
            leftDominated = dominated.source();
            rightDominant = dominant.target();
            rightDominated = dominated.target();
        }
        boolean leftShared = leftDominant == leftDominated;
        boolean rightShared = rightDominant == rightDominated;
        int number = finding.pattern();                                      // pattern computed by Algorithm 4
        String pcNote = !pair.pcShared().isEmpty()
                ? "redundant in PC: " + pcNames(pair.pcShared(), pap)
                : null;
        return new RedundancyPattern(number, title, isProhibition, leftShared, rightShared,
                nameOf(pap, leftDominated), nameOf(pap, leftDominant),
                nameOf(pap, rightDominated), nameOf(pap, rightDominant),
                rightsLabel(dominated.rights()), rightsLabel(dominant.rights()), pcNote);
    }

    /**
     * " (prohibitionName)" for an FP edge, empty otherwise: two distinct prohibitions may flatten
     * to the same triple, and their names are what tells the two findings apart.
     */
    private String ruleName(Algorithm1_2_ComplementedGraphBuilder.Edge e) {
        return (e.name() == null || e.name().isBlank()) ? "" : " (" + e.name() + ")";
    }

    private String rightsLabel(AccessRightSet rights) {
        if (rights == null || rights.isEmpty()) {
            return "";
        }
        return String.join(", ", rights);
    }

    private String nameOf(PAP pap, long id) {
        try {
            Node n = pap.query().graph().getNodeById(id);
            return n.getName();
        } catch (Exception e) {
            return String.valueOf(id);
        }
    }
}
