package com.example.ngac.support;

import gov.nist.ngac.pm.core.common.exception.PMException;
import gov.nist.ngac.pm.core.impl.memory.pap.MemoryPAP;
import gov.nist.ngac.pm.core.pap.PAP;
import gov.nist.ngac.pm.core.pap.operation.accessright.AccessRightSet;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads the base NGAC graph G (nodes + ASSIGNMENT + ASSOCIATION edges) from a JSON file, using
 * the same key names as POMA's policy files:
 *
 * {
 *   "nodes": [ {"name": "...", "type": "U|UA|O|OA|PC", "properties": {}}, ... ],
 *   "assignments": [ {"source": "childName", "target": "parentName"}, ... ],
 *   "associations": [ {"source": "uaName", "target": "attrName", "operations": ["read", ...]}, ... ]
 * }
 *
 * These same three keys are also the graph portion of a combined policy.json (see
 * {@link JsonPolicyLoader}) - {@link #loadFromObject(JSONObject)} reads only these three keys and
 * ignores anything else in the object, so it works unchanged whether the object came from a
 * graph-only file or a combined policy file that also has a "prohibitions" key.
 */
public final class JsonGraphLoader {

    public record LoadedGraph(PAP pap, Map<String, Long> ids) {
        public long id(String name) {
            Long v = ids.get(name);
            if (v == null) {
                throw new IllegalArgumentException("Unknown node in graph JSON: " + name);
            }
            return v;
        }
    }

    private JsonGraphLoader() {
    }

    public static LoadedGraph load(Path jsonFile) throws IOException, PMException {
        String text = Files.readString(jsonFile);
        return loadFromObject(new JSONObject(text));
    }

    /**
     * Same as {@link #load(Path)}, but from an already-parsed JSON object rather than a file on
     * its own - used by {@link JsonPolicyLoader} to read the "nodes"/"assignments"/"associations"
     * portion out of a single combined policy.json that also carries "prohibitions".
     */
    public static LoadedGraph loadFromObject(JSONObject root) throws PMException {
        PAP pap = new MemoryPAP();
        Map<String, Long> ids = new HashMap<>();

        Map<String, String> typeByName = new HashMap<>();
        JSONArray nodes = root.optJSONArray("nodes");
        if (nodes == null) {
            nodes = new JSONArray();
        }
        for (int i = 0; i < nodes.length(); i++) {
            JSONObject n = nodes.getJSONObject(i);
            typeByName.put(n.getString("name"), n.getString("type"));
        }

        Map<String, List<String>> parentsByChild = new HashMap<>();
        JSONArray assignments = root.optJSONArray("assignments");
        if (assignments == null) {
            assignments = new JSONArray();
        }
        for (int i = 0; i < assignments.length(); i++) {
            JSONObject a = assignments.getJSONObject(i);
            parentsByChild.computeIfAbsent(a.getString("source"), k -> new ArrayList<>())
                    .add(a.getString("target"));
        }

        JSONArray associations = root.optJSONArray("associations");
        if (associations == null) {
            associations = new JSONArray();
        }

        // Register every access right of the policy before creating associations: those used by
        // an association, those used by a prohibition (a right may be denied without ever being
        // granted), and those declared in "resourceAccessRights" when the file carries that key
        // (the reference implementation always does).
        Set<String> allRights = new HashSet<>();
        for (int i = 0; i < associations.length(); i++) {
            JSONArray ops = associations.getJSONObject(i).getJSONArray("operations");
            for (int j = 0; j < ops.length(); j++) {
                allRights.add(ops.getString(j));
            }
        }
        JSONArray declared = root.optJSONArray("resourceAccessRights");
        if (declared != null) {
            for (int i = 0; i < declared.length(); i++) {
                allRights.add(declared.getString(i));
            }
        }
        JSONArray prohibitions = root.optJSONArray("prohibitions");
        if (prohibitions != null) {
            for (int i = 0; i < prohibitions.length(); i++) {
                JSONObject p = prohibitions.getJSONObject(i);
                JSONArray rights = p.optJSONArray("accessRights");
                if (rights == null) {
                    rights = p.optJSONArray("ops");          // reference implementation / POMA
                }
                if (rights == null) {
                    rights = p.optJSONArray("arset");
                }
                if (rights != null) {
                    for (int j = 0; j < rights.length(); j++) {
                        allRights.add(rights.getString(j));
                    }
                }
            }
        }
        if (!allRights.isEmpty()) {
            pap.modify().operations().setResourceAccessRights(new AccessRightSet(allRights));
        }

        // create nodes in topological order (a node's parents are created before the node itself)
        for (String name : typeByName.keySet()) {
            ensureCreated(name, typeByName, parentsByChild, ids, pap, new HashSet<>());
        }

        for (int i = 0; i < associations.length(); i++) {
            JSONObject a = associations.getJSONObject(i);
            String source = a.getString("source");
            String target = a.getString("target");
            JSONArray ops = a.getJSONArray("operations");
            Set<String> rights = new HashSet<>();
            for (int j = 0; j < ops.length(); j++) {
                rights.add(ops.getString(j));
            }
            pap.modify().graph().associate(idOf(source, ids), idOf(target, ids), new AccessRightSet(rights));
        }

        return new LoadedGraph(pap, ids);
    }

    private static long idOf(String name, Map<String, Long> ids) {
        Long v = ids.get(name);
        if (v == null) {
            throw new IllegalArgumentException("Unknown node referenced in graph JSON: " + name);
        }
        return v;
    }

    private static long ensureCreated(String name,
                                       Map<String, String> typeByName,
                                       Map<String, List<String>> parentsByChild,
                                       Map<String, Long> ids,
                                       PAP pap,
                                       Set<String> visiting) throws PMException {
        Long existing = ids.get(name);
        if (existing != null) {
            return existing;
        }
        if (!visiting.add(name)) {
            throw new IllegalStateException("Cycle detected in assignments involving: " + name);
        }

        String type = typeByName.get(name);
        if (type == null) {
            throw new IllegalArgumentException("Node referenced but not declared in 'nodes': " + name);
        }

        List<String> parentNames = parentsByChild.getOrDefault(name, List.of());
        List<Long> parentIds = new ArrayList<>();
        for (String parentName : parentNames) {
            parentIds.add(ensureCreated(parentName, typeByName, parentsByChild, ids, pap, visiting));
        }

        long id = switch (type) {
            case "PC" -> pap.modify().graph().createPolicyClass(name);
            case "UA" -> pap.modify().graph().createUserAttribute(name, parentIds);
            case "OA" -> pap.modify().graph().createObjectAttribute(name, parentIds);
            case "U" -> pap.modify().graph().createUser(name, parentIds);
            case "O" -> pap.modify().graph().createObject(name, parentIds);
            default -> throw new IllegalArgumentException("Unknown node type '" + type + "' for node: " + name);
        };

        visiting.remove(name);
        ids.put(name, id);
        return id;
    }
}
