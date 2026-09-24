package com.example.ngac.support;

import gov.nist.ngac.pm.core.common.exception.PMException;
import gov.nist.ngac.pm.core.common.graph.node.Node;
import gov.nist.ngac.pm.core.common.graph.node.NodeType;
import gov.nist.ngac.pm.core.pap.PAP;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Transitive closure of {@code contains}, computed once and shared by Algorithms 3, 4 and 5.
 *
 * <pre>
 * Definition (Containment):
 *   1. contains(x, x) for any policy element x
 *   2. contains(x, y) if (y, x) in ASSIGNMENT
 *   3. contains(x, z) if contains(x, y) and contains(y, z)
 * </pre>
 *
 * The index stores, for every policy element y, the set of its containers { x | contains(x, y) },
 * so that each containment test is a hash-set lookup. This is the precomputation the complexity
 * analysis of the paper assumes ("assuming constant-time containment tests"). It also caches
 * PC(p), the set of policy classes in which a rule with the given endpoints is active
 * (Definition (Rule)), since the detection algorithms query it once per rule rather than once per
 * pair.
 */
public final class ContainmentIndex {

    private final Map<Long, Set<Long>> containersOf = new HashMap<>();
    private final Set<Long> policyClasses = new LinkedHashSet<>();
    private final Map<Long, Set<Long>> activePcsCache = new HashMap<>();

    public ContainmentIndex(PAP pap) throws PMException {
        for (Node n : pap.query().graph().search(NodeType.ANY, Map.of())) {
            if (isAdminNode(n)) {
                continue;
            }
            long y = n.getId();
            if (n.getType() == NodeType.PC) {
                policyClasses.add(y);
            }
            Set<Long> containers = new LinkedHashSet<>();
            containers.add(y);                                        // rule 1
            Deque<Long> toVisit = new ArrayDeque<>();
            toVisit.push(y);
            while (!toVisit.isEmpty()) {
                long current = toVisit.pop();
                for (long parent : pap.query().graph().getAdjacentDescendants(current)) {
                    if (parent >= 0 && containers.add(parent)) {      // rules 2 and 3
                        toVisit.push(parent);
                    }
                }
            }
            containersOf.put(y, containers);
        }
    }

    private static boolean isAdminNode(Node n) {
        return n.getId() < 0 || n.getName().equals("PM_ADMIN") || n.getName().startsWith("PM_ADMIN:");
    }

    /** contains(container, contained), in constant time. */
    public boolean contains(long container, long contained) {
        if (container == contained) {
            return true;
        }
        Set<Long> containers = containersOf.get(contained);
        return containers != null && containers.contains(container);
    }

    /** PC, the policy classes of the graph. */
    public Set<Long> policyClasses() {
        return policyClasses;
    }

    /** PC(p) for a rule whose endpoints are {@code subject} and {@code at}. */
    public Set<Long> activePcs(long subject, long at) {
        long key = subject * 1_000_003L + at;                          // cheap pair key
        Set<Long> cached = activePcsCache.get(key);
        if (cached != null) {
            return cached;
        }
        Set<Long> result = new LinkedHashSet<>();
        for (long pc : policyClasses) {
            if (contains(pc, subject) && contains(pc, at)) {
                result.add(pc);
            }
        }
        activePcsCache.put(key, result);
        return result;
    }
}
