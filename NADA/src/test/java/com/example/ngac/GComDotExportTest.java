package com.example.ngac;

import com.example.ngac.support.Algorithm1_2_ComplementedGraphBuilder;
import com.example.ngac.support.GComDotExporter;
import com.example.ngac.support.UseCasePolicyBuilder;
import gov.nist.ngac.pm.core.common.exception.PMException;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Exports G_Com for the drone use case to a Graphviz .dot file, so it can be rendered to a
 * PDF/PNG figure with: dot -Tpdf gcom.dot -o gcom.pdf
 *
 * highlightNodes / highlightEdges are empty for now. Once the anomaly-detection pass exists,
 * fill these two sets with what it flags, and the corresponding nodes/edges will be drawn in
 * orange on the exported figure.
 */
class GComDotExportTest {

    @Test
    void exportGComToDot() throws PMException, IOException {
        UseCasePolicyBuilder builder = UseCasePolicyBuilder.build();
        Algorithm1_2_ComplementedGraphBuilder gcb = new Algorithm1_2_ComplementedGraphBuilder(builder.pap());

        List<Algorithm1_2_ComplementedGraphBuilder.Edge> gCom = gcb.build();

        Set<Long> highlightNodes = Set.of();
        Set<Algorithm1_2_ComplementedGraphBuilder.Edge> highlightEdges = Set.of();

        String dot = GComDotExporter.toDot(builder.pap(), gCom, highlightNodes, highlightEdges);

        File out = new File("gcom.dot");
        try (FileWriter writer = new FileWriter(out)) {
            writer.write(dot);
        }

        System.out.println("G_Com DOT file written to: " + out.getAbsolutePath());
    }
}
