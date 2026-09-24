package com.example.ngac.support;

import gov.nist.ngac.pm.core.common.exception.PMException;
import gov.nist.ngac.pm.core.pap.PAP;
import gov.nist.ngac.pm.core.pap.operation.accessright.AccessRightSet;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Loads prohibitions (Proh_1, Proh_2, ...) from a JSON file and adds them to an already-loaded
 * PAP (see JsonGraphLoader). Schema:
 *
 * {
 *   "prohibitions": [
 *     {
 *       "name": "Proh_1",
 *       "subject": "Ali",
 *       "accessRights": ["drive"],
 *       "inclusion": ["Emergency"],
 *       "exclusion": [],
 *       "conjunctive": true
 *     },
 *     ...
 *   ]
 * }
 *
 * The serialization used by the NGAC reference implementation (and by the policies distributed
 * with POMA) is also accepted, so that such a policy can be loaded without being rewritten:
 *
 * {
 *   "prohibitions": [
 *     {
 *       "name": "prohibition4",
 *       "subject": "C-Suit",
 *       "ops": ["disapprove", "accept"],
 *       "intersection": false,
 *       "containers": {"Case3": false, "Archive": true}
 *     },
 *     ...
 *   ]
 * }
 *
 * where "ops" is "accessRights", "intersection" is "conjunctive", and each entry of "containers"
 * maps a container to its complement flag: false puts it in the inclusion set, true in the
 * exclusion set. The two styles may be mixed in the same file, and an explicit "accessRights",
 * "inclusion", "exclusion" or "conjunctive" always wins over its counterpart.
 *
 * The "prohibitions" key is also the prohibitions portion of a combined policy.json (see
 * {@link JsonPolicyLoader}) - {@link #loadFromObject(JSONObject, PAP, Map)} reads only this key,
 * so it works unchanged whether the object came from a prohibitions-only file or a combined
 * policy file that also has "nodes"/"assignments"/"associations".
 */
public final class JsonProhibitionsLoader {

    private JsonProhibitionsLoader() {
    }

    public static void load(Path jsonFile, PAP pap, Map<String, Long> ids) throws IOException, PMException {
        String text = Files.readString(jsonFile);
        loadFromObject(new JSONObject(text), pap, ids);
    }

    public static void loadFromObject(JSONObject root, PAP pap, Map<String, Long> ids) throws PMException {
        JSONArray prohibitions = root.optJSONArray("prohibitions");
        if (prohibitions == null) {
            prohibitions = new JSONArray();
        }

        for (int i = 0; i < prohibitions.length(); i++) {
            JSONObject p = prohibitions.getJSONObject(i);
            String name = p.getString("name");
            long subject = idOf(p.getString("subject"), ids);

            // Access rights: "accessRights" (NADA) or "ops" (reference implementation / POMA).
            JSONArray rightsArr = p.optJSONArray("accessRights");
            if (rightsArr == null) {
                rightsArr = p.optJSONArray("ops");
            }
            if (rightsArr == null) {
                throw new IllegalArgumentException(
                        "Prohibition \"" + name + "\": missing \"accessRights\" (or \"ops\")");
            }
            Set<String> rights = new HashSet<>();
            for (int j = 0; j < rightsArr.length(); j++) {
                rights.add(rightsArr.getString(j));
            }

            Set<Long> inclusion = new HashSet<>();
            Set<Long> exclusion = new HashSet<>();

            // "containers": {"<name>": <isComplement>} - reference implementation / POMA style.
            JSONObject containers = p.optJSONObject("containers");
            if (containers != null) {
                for (String container : containers.keySet()) {
                    long id = idOf(container, ids);
                    if (containers.getBoolean(container)) {      // complement -> exclusion
                        exclusion.add(id);
                    } else {                                     // container  -> inclusion
                        inclusion.add(id);
                    }
                }
            }

            // "inclusion" / "exclusion" arrays - NADA style; added to whatever "containers" gave.
            JSONArray inclusionArr = p.optJSONArray("inclusion");
            if (inclusionArr != null) {
                for (int j = 0; j < inclusionArr.length(); j++) {
                    inclusion.add(idOf(inclusionArr.getString(j), ids));
                }
            }
            JSONArray exclusionArr = p.optJSONArray("exclusion");
            if (exclusionArr != null) {
                for (int j = 0; j < exclusionArr.length(); j++) {
                    exclusion.add(idOf(exclusionArr.getString(j), ids));
                }
            }

            if (inclusion.isEmpty() && exclusion.isEmpty()) {
                throw new IllegalArgumentException("Prohibition \"" + name
                        + "\": no target; expected \"inclusion\"/\"exclusion\" or \"containers\"");
            }

            // Conjunctive reading: "conjunctive" (NADA) or "intersection" (reference impl. / POMA).
            boolean conjunctive = p.has("conjunctive")
                    ? p.getBoolean("conjunctive")
                    : p.optBoolean("intersection", true);

            pap.modify().prohibitions().createNodeProhibition(
                    name, subject, new AccessRightSet(rights), inclusion, exclusion, conjunctive);
        }
    }

    private static long idOf(String name, Map<String, Long> ids) {
        Long v = ids.get(name);
        if (v == null) {
            throw new IllegalArgumentException("Unknown node referenced in prohibitions JSON: " + name);
        }
        return v;
    }
}
