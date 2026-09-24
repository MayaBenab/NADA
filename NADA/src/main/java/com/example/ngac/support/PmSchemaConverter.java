package com.example.ngac.support;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Translates a policy written in the JSON schema of the NGAC reference implementation
 * (<em>pm.schema.json</em>, the format produced by {@code pap.serialize(new JSONSerializer())})
 * into the readable, name-based schema that NADA's loaders expect.
 *
 * <p>The reference schema identifies every policy element by a numeric id and stores assignments
 * and associations inside the node that owns them:</p>
 *
 * <pre>
 * {
 *   "resourceAccessRights": ["read", ...],
 *   "graph": {
 *     "pcs":     [ {"id": 1, "name": "LawFirmPolicy"} ],
 *     "uas":     [ {"id": 5, "name": "Attorneys", "assignments": [3, 1],
 *                   "associations": [ {"target": 9, "arset": ["refuse"]} ] } ],
 *     "oas":     [ ... ], "users": [ ... ], "objects": [ ... ]
 *   },
 *   "prohibitions": [ {"name": "p1", "subject": {"node": 5},
 *                      "containers": [ {"id": 9, "complement": false} ],
 *                      "arset": ["refuse"], "intersection": false } ],
 *   "obligations": [ ... ], "operations": { ... }
 * }
 * </pre>
 *
 * <p>NADA's own schema is the same model written with names, which is what makes a case study
 * readable in a paper and in a repository:</p>
 *
 * <pre>
 * {
 *   "nodes":        [ {"name": "Attorneys", "type": "UA"} ],
 *   "assignments":  [ {"source": "Attorneys", "target": "Office1"} ],
 *   "associations": [ {"source": "Attorneys", "target": "Case3", "operations": ["refuse"]} ],
 *   "prohibitions": [ {"name": "p1", "subject": "Attorneys", "accessRights": ["refuse"],
 *                      "inclusion": ["Case3"], "exclusion": [], "conjunctive": false} ]
 * }
 * </pre>
 *
 * <p>The translation is exact: {@code complement: false} is an inclusion container,
 * {@code complement: true} an exclusion container, and {@code intersection} is
 * {@code conjunctive} (both default to {@code true}). Obligations and operation definitions are
 * outside the scope of a static analysis and are ignored; a prohibition whose subject is a
 * process rather than a node is rejected, since Definition~(Rule) requires a subject in
 * {@code U u UA}.</p>
 */
public final class PmSchemaConverter {

    private PmSchemaConverter() {
    }

    /** True when this object is written in the reference implementation's schema. */
    public static boolean isPmSchema(JSONObject root) {
        return root.has("graph");
    }

    /** Rewrites a reference-implementation policy into NADA's name-based schema. */
    public static JSONObject toNadaSchema(JSONObject pm) {
        JSONObject graph = pm.optJSONObject("graph");
        if (graph == null) {
            throw new IllegalArgumentException("Not a Policy Machine JSON policy: no \"graph\" key");
        }

        Map<Long, String> nameById = new HashMap<>();
        JSONArray nodes = new JSONArray();
        JSONArray assignments = new JSONArray();
        JSONArray associations = new JSONArray();

        // Pass 1: names, so that ids can be resolved in any order.
        collectNames(graph, "pcs", nameById);
        collectNames(graph, "uas", nameById);
        collectNames(graph, "oas", nameById);
        collectNames(graph, "users", nameById);
        collectNames(graph, "objects", nameById);

        // Pass 2: nodes, assignments and associations.
        convert(graph, "pcs", "PC", nameById, nodes, assignments, associations);
        convert(graph, "uas", "UA", nameById, nodes, assignments, associations);
        convert(graph, "oas", "OA", nameById, nodes, assignments, associations);
        convert(graph, "users", "U", nameById, nodes, assignments, associations);
        convert(graph, "objects", "O", nameById, nodes, assignments, associations);

        JSONObject out = new JSONObject();
        out.put("nodes", nodes);
        out.put("assignments", assignments);
        out.put("associations", associations);
        out.put("prohibitions", convertProhibitions(pm.optJSONArray("prohibitions"), nameById));
        if (pm.has("resourceAccessRights")) {
            out.put("resourceAccessRights", pm.getJSONArray("resourceAccessRights"));
        }
        return out;
    }

    private static void collectNames(JSONObject graph, String key, Map<Long, String> nameById) {
        JSONArray arr = graph.optJSONArray(key);
        if (arr == null) {
            return;
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject n = arr.getJSONObject(i);
            nameById.put(n.getLong("id"), n.getString("name"));
        }
    }

    private static void convert(JSONObject graph, String key, String type,
                                 Map<Long, String> nameById,
                                 JSONArray nodes, JSONArray assignments, JSONArray associations) {
        JSONArray arr = graph.optJSONArray(key);
        if (arr == null) {
            return;
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject n = arr.getJSONObject(i);
            String name = n.getString("name");

            JSONObject node = new JSONObject();
            node.put("name", name);
            node.put("type", type);
            node.put("properties", n.optJSONObject("properties") == null
                    ? new JSONObject() : n.getJSONObject("properties"));
            nodes.put(node);

            JSONArray parents = n.optJSONArray("assignments");
            if (parents != null) {
                for (int j = 0; j < parents.length(); j++) {
                    JSONObject a = new JSONObject();
                    a.put("source", name);
                    a.put("target", nameOf(parents.getLong(j), nameById));
                    assignments.put(a);
                }
            }

            JSONArray assocs = n.optJSONArray("associations");
            if (assocs != null) {
                for (int j = 0; j < assocs.length(); j++) {
                    JSONObject src = assocs.getJSONObject(j);
                    JSONObject a = new JSONObject();
                    a.put("source", name);
                    a.put("target", nameOf(src.getLong("target"), nameById));
                    a.put("operations", src.getJSONArray("arset"));
                    associations.put(a);
                }
            }
        }
    }

    private static JSONArray convertProhibitions(JSONArray prohibitions, Map<Long, String> nameById) {
        JSONArray out = new JSONArray();
        if (prohibitions == null) {
            return out;
        }
        for (int i = 0; i < prohibitions.length(); i++) {
            JSONObject p = prohibitions.getJSONObject(i);
            String name = p.getString("name");

            JSONObject subject = p.getJSONObject("subject");
            if (!subject.has("node")) {
                throw new IllegalArgumentException("Prohibition \"" + name
                        + "\": subject is a process; only prohibitions whose subject is a user or "
                        + "a user attribute are analysed");
            }

            JSONArray inclusion = new JSONArray();
            JSONArray exclusion = new JSONArray();
            JSONArray containers = p.getJSONArray("containers");
            for (int j = 0; j < containers.length(); j++) {
                JSONObject c = containers.getJSONObject(j);
                String container = nameOf(c.getLong("id"), nameById);
                if (c.optBoolean("complement", false)) {
                    exclusion.put(container);
                } else {
                    inclusion.put(container);
                }
            }

            JSONObject q = new JSONObject();
            q.put("name", name);
            q.put("subject", nameOf(subject.getLong("node"), nameById));
            q.put("accessRights", p.getJSONArray("arset"));
            q.put("inclusion", inclusion);
            q.put("exclusion", exclusion);
            q.put("conjunctive", p.optBoolean("intersection", true));
            out.put(q);
        }
        return out;
    }

    private static String nameOf(long id, Map<Long, String> nameById) {
        String name = nameById.get(id);
        if (name == null) {
            throw new IllegalArgumentException("Unknown node id in Policy Machine JSON: " + id);
        }
        return name;
    }
}
