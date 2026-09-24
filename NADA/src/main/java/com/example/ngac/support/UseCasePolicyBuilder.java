package com.example.ngac.support;

import gov.nist.ngac.pm.core.common.exception.PMException;
import gov.nist.ngac.pm.core.pap.PAP;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Builds the "Drone" case study by loading it from case_studies/Drone/policy.json (the same file
 * NADA's case-study tree uses), so the drone data lives in exactly one place. Used by
 * GComDotExportTest to produce a publication-quality vector (.dot) figure of G_Com without
 * going through the GUI.
 */
public final class UseCasePolicyBuilder {

    private final PAP pap;
    private final Map<String, Long> ids;

    private UseCasePolicyBuilder(PAP pap, Map<String, Long> ids) {
        this.pap = pap;
        this.ids = ids;
    }

    public static UseCasePolicyBuilder build() throws IOException, PMException {
        Path policyFile = Path.of("case_studies", "Drone", "policy.json");
        JsonPolicyLoader.LoadedPolicy loaded = JsonPolicyLoader.load(policyFile);
        return new UseCasePolicyBuilder(loaded.pap(), loaded.ids());
    }

    public PAP pap() {
        return pap;
    }

    public long id(String name) {
        Long v = ids.get(name);
        if (v == null) {
            throw new IllegalArgumentException("Unknown node in Drone case study: " + name);
        }
        return v;
    }
}
