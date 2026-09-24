package com.example.ngac.support;

import gov.nist.ngac.pm.core.common.exception.PMException;
import gov.nist.ngac.pm.core.common.graph.node.Node;
import gov.nist.ngac.pm.core.common.graph.node.NodeType;
import gov.nist.ngac.pm.core.common.prohibition.NodeProhibition;
import gov.nist.ngac.pm.core.common.prohibition.Prohibition;
import gov.nist.ngac.pm.core.pap.PAP;
import gov.nist.ngac.pm.core.pap.graph.Association;
import gov.nist.ngac.pm.core.pap.operation.accessright.AccessRightSet;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Algorithms 1 and 2 of the paper: construction of the Complemented Graph G_Com
 * (Definition "Complemented Graph") and resolution of a prohibition's target prohibition set
 * (procedure ResolveTPS).
 *
 * <pre>
 * Definition (Complemented Graph).
 * Given C = &lt;PE, R&gt; with R = &lt;ASSIGNMENT, ASSOCIATION, PROHIBITION&gt;,
 *   G_Com = &lt;PE, E&gt;,  E = &lt;ASSIGNMENT, ASSOCIATION, FP&gt;,
 *   FP    = Multiset union over &lt;s, ars, tau&gt; in PROHIBITION of { (at, ars, s) | at in top(tps) }
 *           (identical edges produced by distinct prohibitions are all kept).
 *
 * Each FP edge is oriented from the denied element at toward the subject s (the reverse of an
 * ASSOCIATION edge (ua, ars, at)), so grants and denials remain distinguishable in G_Com.
 *
 * Definition (NGAC policy, PROHIBITION). A prohibition (s, ars, tps), also written
 * &lt;s, ars, tau&gt;, has its tps denoted by its target prohibition specification
 *   tau = &lt;op, (alpha_1, c_1), ..., (alpha_n, c_n)&gt;   (tau = (alpha_1, c_1) when n = 1)
 * with op in {conjunctive, disjunctive}, alpha_i in {inclusion, exclusion},
 * c_i in UA u OA u PC:
 *   tps = rho(alpha_1, c_1)             if n = 1
 *       = INTERSECT_i rho(alpha_i, c_i)  if n &gt; 1 and op = conjunctive
 *       = UNION_i     rho(alpha_i, c_i)  if n &gt; 1 and op = disjunctive
 *   rho(inclusion, c) = { x in Universe(c) | contains(c, x) }
 *   rho(exclusion, c) = Universe(c) \ { x in Universe(c) | contains(c, x) }
 *   Universe(c) = UA u U        if c in UA
 *               = OA            if c in OA
 *               = UA u U u OA   if c in PC
 * Objects are object attributes in NGAC (O is a subset of OA): nodes of type O are therefore
 * included wherever the definition says OA.
 *
 * contains (Definition "Containment"):
 *   1. contains(x, x) for any x in U u UA u OA u PC   (reflexive on every policy element)
 *   2. contains(x, y) if (y, x) in ASSIGNMENT
 *   3. contains(x, z) if contains(x, y) and contains(y, z)
 *
 * top(tps) = { x in tps | there is no y in tps \ {x} with contains(y, x) }.
 * </pre>
 *
 * <ul>
 *   <li>{@link #computeG()}: PE with the ASSIGNMENT edges (child to parent) and the ASSOCIATION
 *       edges (ua to at, carrying the granted access rights).</li>
 *   <li>{@link #computeFP()}: Algorithm 1 (the FP part) - for every prohibition &lt;s, ars, tps&gt;,
 *       calls {@link #resolveTPS(TargetSpec)} (Algorithm 2) and adds one edge (at, ars, s) per
 *       at in top(tps).</li>
 *   <li>{@link #computeGCom(List)}: E = ASSIGNMENT, ASSOCIATION and FP together.</li>
 * </ul>
 *
 * The JSON / PM-core representation of a prohibition stores tau as two lists (inclusion,
 * exclusion) plus a boolean (conjunctive); {@link #targetSpecOf(Prohibition)} rebuilds tau from
 * them so that the resolution below follows Algorithm 2 line by line.
 */
public final class Algorithm1_2_ComplementedGraphBuilder {

    public enum EdgeKind { ASSIGNMENT, ASSOCIATION, DENIAL }

    /**
     * An edge of G_Com. {@code name} is the name of the prohibition an FP edge comes from
     * (Definition (NGAC policy, PROHIBITION)); it is null for ASSIGNMENT and ASSOCIATION edges.
     * Two distinct prohibitions may flatten to the same (at, ars, s) triple, so the name is what
     * keeps those two edges distinguishable in reports.
     */
    public record Edge(long source, long target, EdgeKind kind, AccessRightSet rights, String name) {
        public Edge(long source, long target, EdgeKind kind, AccessRightSet rights) {
            this(source, target, kind, rights, null);
        }
    }

    /** Complement operator alpha_i of a container specification. */
    public enum Complement { INCLUSION, EXCLUSION }

    /** Intersection operator op of a target prohibition specification. */
    public enum IntersectionOp { CONJUNCTIVE, DISJUNCTIVE }

    /** A container specification (alpha_i, c_i). */
    public record ContainerSpec(Complement alpha, long container) { }

    /** A target prohibition specification tau = &lt;op, (alpha_1, c_1), ..., (alpha_n, c_n)&gt;. */
    public record TargetSpec(IntersectionOp op, List<ContainerSpec> specs) {
        public int n() {
            return specs.size();
        }
    }

    private final PAP pap;

    /**
     * Precomputed transitive closure of contains (Section 5, complexity paragraph: "assuming the
     * transitive closure of contains is precomputed, so that each containment test takes O(1)
     * time"): containersOf.get(y) = { x | contains(x, y) }, y itself included (rule 1).
     * Built once, lazily, by {@link #buildContainmentIndex()}.
     */
    private Map<Long, Set<Long>> containersOf;

    /** Non-admin policy elements grouped by node type, used to build Universe(c) without rescanning PE. */
    private Map<NodeType, Set<Long>> elementsByType;

    public Algorithm1_2_ComplementedGraphBuilder(PAP pap) {
        this.pap = pap;
    }

    // -----------------------------------------------------------------
    // ASSIGNMENT and ASSOCIATION (unchanged in G_Com)
    // -----------------------------------------------------------------

    public List<Edge> computeG() throws PMException {
        List<Edge> edges = new ArrayList<>();
        Collection<Node> allNodes = pap.query().graph().search(NodeType.ANY, Map.of());

        for (Node n : allNodes) {
            if (isAdminNode(n)) {
                continue;
            }
            long id = n.getId();

            // ASSIGNMENT: n -> each of its direct parents. (getAdjacentDescendants(id) returns
            // n's direct parents in this version of policy-machine-core - see the README note
            // on the library's ascendant/descendant naming.)
            for (long parentId : pap.query().graph().getAdjacentDescendants(id)) {
                if (parentId < 0) {
                    continue;
                }
                edges.add(new Edge(id, parentId, EdgeKind.ASSIGNMENT, null));
            }

            // ASSOCIATION: n -> target, with the granted access rights (only UA nodes can be an
            // association source, so this is naturally empty for every other node type).
            for (Association a : pap.query().graph().getAssociationsWithSource(id)) {
                edges.add(new Edge(a.source(), a.target(), EdgeKind.ASSOCIATION, a.arset()));
            }
        }
        return edges;
    }

    private boolean isAdminNode(Node n) {
        return n.getId() < 0 || n.getName().equals("PM_ADMIN") || n.getName().startsWith("PM_ADMIN:");
    }

    // -----------------------------------------------------------------
    // Algorithm 1: FP (flattened prohibitions)
    // -----------------------------------------------------------------

    public List<Edge> computeFP() throws PMException {
        // FP is a multiset (Algorithm 1 uses multiset union): an edge (at, ars, s) produced by
        // two distinct prohibitions is kept twice, so that Algorithm 3 can report them as fully
        // redundant (Definition "Full and Partial Redundancy").
        List<Edge> fp = new ArrayList<>();                                  // FP <- empty
        buildContainmentIndex();                                            // closure of contains, once
        for (Prohibition p : pap.query().prohibitions().getProhibitions()) { // for all <s, ars, tau>
            if (!(p instanceof NodeProhibition np)) {
                continue; // process prohibitions are out of scope for this graph
            }
            long s = np.getNodeId();
            AccessRightSet ars = p.getAccessRightSet();
            TargetSpec tau = targetSpecOf(p);

            Set<Long> t = resolveTPS(tau);                                  // t <- ResolveTPS(tau)
            for (long at : t) {                                             // for all at in t
                fp.add(new Edge(at, s, EdgeKind.DENIAL, ars, p.getName())); // FP <- FP (+) {(at, ars, s)}
                                                                            // edge oriented from at toward s
            }
        }
        return fp;
    }

    /** Rebuilds tau from the PM-core representation (inclusion set, exclusion set, conjunctive). */
    private TargetSpec targetSpecOf(Prohibition p) {
        List<ContainerSpec> specs = new ArrayList<>();
        p.getInclusionSet().stream().sorted()
                .forEach(c -> specs.add(new ContainerSpec(Complement.INCLUSION, c)));
        p.getExclusionSet().stream().sorted()
                .forEach(c -> specs.add(new ContainerSpec(Complement.EXCLUSION, c)));
        IntersectionOp op = p.isConjunctive() ? IntersectionOp.CONJUNCTIVE : IntersectionOp.DISJUNCTIVE;
        return new TargetSpec(op, specs);
    }

    // -----------------------------------------------------------------
    // Algorithm 2: ResolveTPS
    // -----------------------------------------------------------------

    /** Returns t = top(tps), the containment-maximal elements of the set denoted by tau. */
    Set<Long> resolveTPS(TargetSpec tau) throws PMException {
        int n = tau.n();
        if (n == 0) {
            return new LinkedHashSet<>(); // an empty specification denotes no element
        }

        Set<Long> tps;
        if (n == 1) {
            ContainerSpec only = tau.specs().get(0);
            tps = rho(only.alpha(), only.container());                  // tps <- rho(alpha_1, c_1)
        } else {
            List<Set<Long>> s = new ArrayList<>();
            for (ContainerSpec spec : tau.specs()) {                    // for i = 1 to n
                s.add(rho(spec.alpha(), spec.container()));             //   S_i <- rho(alpha_i, c_i)
            }
            tps = new LinkedHashSet<>(s.get(0));
            if (tau.op() == IntersectionOp.CONJUNCTIVE) {
                for (int i = 1; i < n; i++) {
                    tps.retainAll(s.get(i));                            // tps <- INTERSECT S_i
                }
            } else {
                for (int i = 1; i < n; i++) {
                    tps.addAll(s.get(i));                               // tps <- UNION S_i
                }
            }
        }

        Set<Long> t = new LinkedHashSet<>();                            // t <- empty
        for (long x : tps) {                                            // for all x in tps
            boolean maximal = true;
            for (long y : tps) {
                if (y != x && contains(y, x)) {                         // exists y in tps\{x}: contains(y,x)
                    maximal = false;
                    break;
                }
            }
            if (maximal) {
                t.add(x);                                               // t <- t u {x}
            }
        }
        return t;
    }

    /** rho(alpha, c): the set of policy elements defined by the container specification (alpha, c). */
    private Set<Long> rho(Complement alpha, long c) throws PMException {
        Set<Long> universe = universeOf(c);
        Set<Long> containedByC = new LinkedHashSet<>();
        for (long x : universe) {
            if (contains(c, x)) {
                containedByC.add(x);
            }
        }
        if (alpha == Complement.INCLUSION) {
            return containedByC;
        }
        Set<Long> complement = new LinkedHashSet<>(universe);
        complement.removeAll(containedByC);
        return complement;
    }

    /**
     * Universe(c): UA u U if c in UA, OA if c in OA, UA u U u OA if c in PC. Nodes of type O are
     * counted as OA (O is a subset of OA in NGAC). A container is always UA, OA or PC.
     */
    private Set<Long> universeOf(long c) throws PMException {
        NodeType type = pap.query().graph().getNodeById(c).getType();
        Set<NodeType> allowedTypes = switch (type) {
            case UA -> Set.of(NodeType.UA, NodeType.U);
            case OA -> Set.of(NodeType.OA, NodeType.O);
            case PC -> Set.of(NodeType.UA, NodeType.U, NodeType.OA, NodeType.O);
            default -> throw new IllegalArgumentException(
                    "Prohibition container must be UA, OA or PC, was " + type + " for node " + c);
        };
        buildContainmentIndex();
        Set<Long> result = new LinkedHashSet<>();
        for (NodeType t : allowedTypes) {
            result.addAll(elementsByType.getOrDefault(t, Set.of()));
        }
        return result;
    }

    /**
     * contains(x, y) per Definition (Containment): reflexive on every policy element (rule 1),
     * otherwise y reaches x through ASSIGNMENT (rules 2-3). isAscendant(y, x) is true when y is
     * transitively assigned to x in this version of policy-machine-core.
     */
    private boolean contains(long x, long y) throws PMException {
        if (x == y) {
            return true;                                                    // rule 1
        }
        buildContainmentIndex();
        Set<Long> containers = containersOf.get(y);
        if (containers != null) {
            return containers.contains(x);                                  // O(1) lookup in the closure
        }
        return pap.query().graph().isAscendant(y, x);                       // y outside PE (admin node)
    }

    /**
     * Builds, once, the transitive closure of contains over the non-admin policy elements: for
     * every y, all x reachable from y by following ASSIGNMENT edges (rules 2 and 3), plus y itself
     * (rule 1). Also groups the elements by type for Universe(c).
     */
    private void buildContainmentIndex() throws PMException {
        if (containersOf != null) {
            return;
        }
        Map<Long, Set<Long>> closure = new HashMap<>();
        Map<NodeType, Set<Long>> byType = new HashMap<>();
        for (Node n : pap.query().graph().search(NodeType.ANY, Map.of())) {
            if (isAdminNode(n)) {
                continue;
            }
            long y = n.getId();
            byType.computeIfAbsent(n.getType(), k -> new LinkedHashSet<>()).add(y);

            Set<Long> containers = new LinkedHashSet<>();
            containers.add(y);                                              // rule 1: contains(y, y)
            Deque<Long> toVisit = new ArrayDeque<>();
            toVisit.push(y);
            while (!toVisit.isEmpty()) {
                long current = toVisit.pop();
                // getAdjacentDescendants(id) returns id's direct parents in this version of
                // policy-machine-core (see computeG()).
                for (long parent : pap.query().graph().getAdjacentDescendants(current)) {
                    if (parent >= 0 && containers.add(parent)) {            // rules 2 and 3
                        toVisit.push(parent);
                    }
                }
            }
            closure.put(y, containers);
        }
        containersOf = closure;
        elementsByType = byType;
    }

    // -----------------------------------------------------------------
    // G_Com
    // -----------------------------------------------------------------

    public List<Edge> computeGCom(List<Edge> g) throws PMException {
        List<Edge> result = new ArrayList<>(g);
        result.addAll(computeFP());
        return result;
    }

    /** Convenience one-shot: computeGCom(computeG()). Kept for older callers/tests. */
    public List<Edge> build() throws PMException {
        return computeGCom(computeG());
    }
}
