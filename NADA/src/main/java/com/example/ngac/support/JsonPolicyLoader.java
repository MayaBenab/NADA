package com.example.ngac.support;

import gov.nist.ngac.pm.core.common.exception.PMException;
import gov.nist.ngac.pm.core.pap.PAP;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Loads a whole NGAC Policy from a single JSON file: the Graph (nodes, assignments,
 * associations) AND its prohibitions together, in one object, matching the paper's Policy =
 * Configuration C = &lt;PE, R&gt; = Graph + Prohibitions.
 *
 * Schema - the union of {@link JsonGraphLoader}'s and {@link JsonProhibitionsLoader}'s schemas
 * in a single JSON object:
 *
 * {
 *   "nodes":        [ {"name": "...", "type": "U|UA|O|OA|PC", "properties": {}}, ... ],
 *   "assignments":  [ {"source": "childName", "target": "parentName"}, ... ],
 *   "associations": [ {"source": "uaName", "target": "attrName", "operations": ["read", ...]}, ... ],
 *   "prohibitions": [
 *     {"name": "Proh_1", "subject": "...", "accessRights": ["..."],
 *      "inclusion": ["..."], "exclusion": [], "conjunctive": true},
 *     ...
 *   ]
 * }
 *
 * All four arrays are optional (an absent one is treated as empty) - in particular a Policy with
 * no prohibitions is simply a policy.json with no "prohibitions" key, and FP will be empty for it.
 *
 * A policy written in the JSON schema of the NGAC reference implementation (the output of
 * {@code pap.serialize(new JSONSerializer())}, recognised by its "graph" key) is accepted as
 * well: it is translated into the schema above by {@link PmSchemaConverter} before being loaded,
 * so that a configuration exported from a running deployment can be analysed without being
 * rewritten.
 *
 * There is deliberately only ONE file per Policy (no separate graph.json / prohibitions.json):
 * a case study under case_studies/&lt;Name&gt;/ is exactly one policy.json.
 */
public final class JsonPolicyLoader {

    public record LoadedPolicy(PAP pap, Map<String, Long> ids) {
        public long id(String name) {
            Long v = ids.get(name);
            if (v == null) {
                throw new IllegalArgumentException("Unknown node in policy JSON: " + name);
            }
            return v;
        }
    }

    private JsonPolicyLoader() {
    }

    public static LoadedPolicy load(Path jsonFile) throws IOException, PMException {
        JSONObject root = new JSONObject(Files.readString(jsonFile));
        if (PmSchemaConverter.isPmSchema(root)) {                 // reference implementation schema
            root = PmSchemaConverter.toNadaSchema(root);
        }
        JsonGraphLoader.LoadedGraph graph = JsonGraphLoader.loadFromObject(root);
        JsonProhibitionsLoader.loadFromObject(root, graph.pap(), graph.ids());
        return new LoadedPolicy(graph.pap(), graph.ids());
    }
}
