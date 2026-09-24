#!/usr/bin/env python3
"""Rewrites a policy from NADA's readable, name-based JSON into the JSON schema of the NGAC
reference implementation (core/src/main/resources/json/pm.schema.json), the format produced by
pap.serialize(new JSONSerializer()).

    python3 tools/to_pm_schema.py case_studies/Drone/policy.json [-o out.json]

Without -o the file is rewritten in place. The translation is the exact inverse of
PmSchemaConverter.java: inclusion containers become {"complement": false}, exclusion containers
{"complement": true}, and "conjunctive" becomes "intersection".
"""
import argparse
import json
import sys

BUCKET = {"PC": "pcs", "UA": "uas", "OA": "oas", "U": "users", "O": "objects"}


def to_pm(nada):
    nodes = nada.get("nodes", [])
    ids = {n["name"]: i + 1 for i, n in enumerate(nodes)}

    graph = {b: [] for b in BUCKET.values()}
    by_name = {}
    for n in nodes:
        entry = {"id": ids[n["name"]], "name": n["name"]}
        if n.get("properties"):
            entry["properties"] = n["properties"]
        if n["type"] != "PC":
            entry["assignments"] = []
        if n["type"] == "UA":
            entry["associations"] = []
        graph[BUCKET[n["type"]]].append(entry)
        by_name[n["name"]] = entry

    for a in nada.get("assignments", []):
        by_name[a["source"]]["assignments"].append(ids[a["target"]])
    for a in nada.get("associations", []):
        by_name[a["source"]]["associations"].append(
            {"target": ids[a["target"]], "arset": a["operations"]})

    rights = {r for a in nada.get("associations", []) for r in a["operations"]}
    prohibitions = []
    for p in nada.get("prohibitions", []):
        rights.update(p["accessRights"])
        containers = [{"id": ids[c], "complement": False} for c in p.get("inclusion", [])]
        containers += [{"id": ids[c], "complement": True} for c in p.get("exclusion", [])]
        prohibitions.append({
            "name": p["name"],
            "subject": {"node": ids[p["subject"]]},
            "containers": containers,
            "arset": p["accessRights"],
            "intersection": p.get("conjunctive", True),
        })

    return {
        "resourceAccessRights": sorted(rights),
        "graph": graph,
        "prohibitions": prohibitions,
        "obligations": [],
        "operations": {"resourceOperations": [], "adminOperations": []},
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("files", nargs="+")
    ap.add_argument("-o", "--out")
    args = ap.parse_args()
    if args.out and len(args.files) > 1:
        sys.exit("-o can only be used with a single input file")
    for path in args.files:
        with open(path, encoding="utf-8") as f:
            nada = json.load(f)
        if "graph" in nada:
            print(f"{path}: already in the reference schema, skipped")
            continue
        out = args.out or path
        with open(out, "w", encoding="utf-8") as f:
            json.dump(to_pm(nada), f, indent=2)
            f.write("\n")
        print(f"{path} -> {out}")


if __name__ == "__main__":
    main()
