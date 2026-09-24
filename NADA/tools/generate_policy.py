#!/usr/bin/env python3
"""Synthetic NGAC policy generator for the scalability experiments of NADA.

Generates a policy.json that NADA can load directly:

    python3 tools/generate_policy.py --rules 1000 --out case_studies/Synth1000/policy.json

The generated policy has the shape of a realistic organisation: a user-attribute
forest and an object-attribute forest of given depth and branching factor, all
rooted in a few policy classes, plus associations and prohibitions drawn at
random over those attributes. The seed makes every run reproducible.

Parameters (defaults in brackets):
  --rules N        total number of rules; 70% associations, 30% prohibitions [100]
  --pc N           number of policy classes [3]
  --depth N        depth of each attribute hierarchy [4]
  --branching N    branching factor of each hierarchy [3]
  --rights N       size of the access-right vocabulary [8]
  --seed N         random seed [1]
"""
import argparse, json, random, sys
from pathlib import Path


def build_forest(prefix, kind, depth, branching, pcs, rnd):
    """A forest of attributes rooted in the policy classes."""
    nodes, assignments, levels = [], [], []
    roots = []
    for i, pc in enumerate(pcs):
        name = f"{prefix}_root{i}"
        nodes.append({"name": name, "type": kind, "properties": {}})
        assignments.append({"source": name, "target": pc})
        roots.append(name)
    levels.append(roots)
    counter = 0
    for d in range(1, depth):
        current = []
        for parent in levels[-1]:
            for _ in range(branching):
                counter += 1
                name = f"{prefix}{d}_{counter}"
                nodes.append({"name": name, "type": kind, "properties": {}})
                assignments.append({"source": name, "target": parent})
                # 15% of the nodes are also assigned to a second policy class,
                # which is what makes PC(p) non-trivial for some rules
                if rnd.random() < 0.15 and len(pcs) > 1:
                    assignments.append({"source": name, "target": rnd.choice(pcs)})
                current.append(name)
        levels.append(current)
    return nodes, assignments, [n for lvl in levels for n in lvl], levels


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--rules", type=int, default=100)
    ap.add_argument("--pc", type=int, default=3)
    ap.add_argument("--depth", type=int, default=4)
    ap.add_argument("--branching", type=int, default=3)
    ap.add_argument("--rights", type=int, default=8)
    ap.add_argument("--seed", type=int, default=1)
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    rnd = random.Random(a.seed)

    pcs = [f"pc{i}" for i in range(a.pc)]
    nodes = [{"name": pc, "type": "PC", "properties": {}} for pc in pcs]
    assignments = []

    ua_nodes, ua_asg, uas, ua_levels = build_forest("UA", "UA", a.depth, a.branching, pcs, rnd)
    oa_nodes, oa_asg, oas, oa_levels = build_forest("OA", "OA", a.depth, a.branching, pcs, rnd)
    nodes += ua_nodes + oa_nodes
    assignments += ua_asg + oa_asg

    # one user and one object per leaf, so that every rule is exercisable
    for i, ua in enumerate(ua_levels[-1]):
        nodes.append({"name": f"u{i}", "type": "U", "properties": {}})
        assignments.append({"source": f"u{i}", "target": ua})
    for i, oa in enumerate(oa_levels[-1]):
        nodes.append({"name": f"o{i}", "type": "O", "properties": {}})
        assignments.append({"source": f"o{i}", "target": oa})

    rights = [f"r{i}" for i in range(a.rights)]
    n_assoc = int(a.rules * 0.7)
    n_proh = a.rules - n_assoc
    if n_assoc > len(uas) * len(oas):
        sys.exit("Too many rules for this hierarchy: increase --depth or --branching.")

    # index the attributes by the policy class they belong to, so that the
    # generated rules are active (Definition (Rule)); a few rules are drawn
    # across policy classes on purpose, and are therefore inactive
    by_pc_ua = {pc: [] for pc in pcs}
    by_pc_oa = {pc: [] for pc in pcs}
    parents = {}
    for asg in assignments:
        parents.setdefault(asg["source"], set()).add(asg["target"])

    def pcs_of(node):
        seen, stack, found = {node}, [node], set()
        while stack:
            n = stack.pop()
            for par in parents.get(n, ()):
                if par in pcs:
                    found.add(par)
                elif par not in seen:
                    seen.add(par); stack.append(par)
        return found

    for n in uas:
        for pc in pcs_of(n):
            by_pc_ua[pc].append(n)
    for n in oas:
        for pc in pcs_of(n):
            by_pc_oa[pc].append(n)

    # associations: distinct (ua, at) pairs, as in a loaded configuration
    pairs = set()
    associations = []
    while len(associations) < n_assoc:
        pc = rnd.choice(pcs)
        ua, oa = rnd.choice(by_pc_ua[pc]), rnd.choice(by_pc_oa[pc])
        if (ua, oa) in pairs:
            continue
        pairs.add((ua, oa))
        k = rnd.randint(1, 3)
        associations.append({"source": ua, "target": oa,
                             "operations": sorted(rnd.sample(rights, k))})

    prohibitions = []
    for i in range(n_proh):
        k = rnd.randint(1, 3)
        pc = rnd.choice(pcs)
        prohibitions.append({
            "name": f"proh{i}",
            "subject": rnd.choice(by_pc_ua[pc]),
            "accessRights": sorted(rnd.sample(rights, k)),
            "inclusion": [rnd.choice(by_pc_oa[pc])],
            "exclusion": [],
            "conjunctive": False,
        })

    out = Path(a.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    json.dump({"nodes": nodes, "assignments": assignments,
               "associations": associations, "prohibitions": prohibitions},
              open(out, "w"), indent=1)
    print(f"{out}: {len(nodes)} nodes, {len(assignments)} assignments, "
          f"{len(associations)} associations, {len(prohibitions)} prohibitions")


if __name__ == "__main__":
    main()
