package com.example.ngac.support;

import gov.nist.ngac.pm.core.common.exception.PMException;
import gov.nist.ngac.pm.core.common.graph.node.Node;
import gov.nist.ngac.pm.core.common.graph.node.NodeType;
import gov.nist.ngac.pm.core.pap.PAP;
import gov.nist.ngac.pm.core.pap.operation.accessright.AccessRightSet;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Exports a G_Com edge list (as produced by Algorithm1_2_ComplementedGraphBuilder) to Graphviz DOT format,
 * so it can be rendered with "dot -Tpdf gcom.dot -o gcom.pdf" for the paper's figures.
 *
 * Entirely black and white, matching the paper's own line-style convention (solid = assignment,
 * dashed = association, bold dash-dot = flattened prohibition/denial) so the rendered PDF/PNG
 * needs no further editing for a grayscale print journal. Node types are told apart by shape
 * alone (box / rounded box / ellipse / filled black box), never by color.
 *
 * highlightNodes / highlightEdges let an anomaly-detection pass mark specific nodes or edges
 * (e.g. a redundancy found by Algorithm 3/4) so they stand out - done with a heavier line weight
 * and a light gray fill, not a color swap, so highlighting also stays print-safe.
 */
public final class GComDotExporter {

    private GComDotExporter() {
    }

    public static String toDot(PAP pap,
                                List<Algorithm1_2_ComplementedGraphBuilder.Edge> gCom,
                                Set<Long> highlightNodes,
                                Set<Algorithm1_2_ComplementedGraphBuilder.Edge> highlightEdges) throws PMException {
        StringBuilder sb = new StringBuilder();
        sb.append("digraph GCom {\n");
        sb.append("  rankdir=TB;\n");
        sb.append("  bgcolor=\"white\";\n");
        sb.append("  node [fontname=\"Helvetica\", fontsize=11];\n");
        sb.append("  edge [fontname=\"Helvetica\", fontsize=10];\n\n");

        Set<Long> nodeIds = new LinkedHashSet<>();
        for (Algorithm1_2_ComplementedGraphBuilder.Edge e : gCom) {
            nodeIds.add(e.source());
            nodeIds.add(e.target());
        }

        for (long id : nodeIds) {
            Node n = pap.query().graph().getNodeById(id);
            sb.append("  \"").append(id).append("\" [label=\"").append(escape(n.getName())).append("\", ")
                    .append(styleFor(n.getType(), highlightNodes.contains(id)))
                    .append("];\n");
        }
        sb.append("\n");

        for (Algorithm1_2_ComplementedGraphBuilder.Edge e : gCom) {
            boolean hl = highlightEdges.contains(e);
            sb.append("  \"").append(e.source()).append("\" -> \"").append(e.target()).append("\" [")
                    .append(edgeStyleFor(e, hl))
                    .append("];\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    private static String styleFor(NodeType t, boolean highlighted) {
        String shape;
        boolean filledBlock = false;
        switch (t) {
            case U -> shape = "box";
            case UA -> shape = "box";
            case OA, O -> shape = "ellipse";
            case PC -> { shape = "box"; filledBlock = true; }
            default -> shape = "box";
        }
        String fill = highlighted ? "#d9d9d9" : (filledBlock ? "#000000" : "#ffffff");
        String fontcolor = (filledBlock && !highlighted) ? "#ffffff" : "#000000";
        String style = (t == NodeType.PC || t == NodeType.UA) ? "filled,rounded" : "filled";

        StringBuilder s = new StringBuilder();
        s.append("shape=").append(shape)
                .append(", style=\"").append(style).append("\"")
                .append(", fillcolor=\"").append(fill).append("\"")
                .append(", color=\"#000000\"")
                .append(", fontcolor=\"").append(fontcolor).append("\"");
        if (highlighted) {
            s.append(", penwidth=3");
        }
        return s.toString();
    }

    private static String edgeStyleFor(Algorithm1_2_ComplementedGraphBuilder.Edge e, boolean highlighted) {
        StringBuilder s = new StringBuilder();
        String penwidth = highlighted ? "3" : "1";
        switch (e.kind()) {
            case ASSIGNMENT -> s.append("color=\"#000000\", style=solid, arrowsize=0.8, penwidth=").append(penwidth);
            case ASSOCIATION -> s.append("color=\"#000000\", style=dashed, fontcolor=\"#000000\", penwidth=")
                    .append(penwidth).append(", label=\"").append(rightsLabel(e.rights())).append("\"");
            // Graphviz's edge "style" attribute has no native dash-dot pattern - "dashed" with a
            // noticeably heavier penwidth than ASSOCIATION's is the closest print-safe
            // approximation to the paper's bold dash-dot flattened-prohibition line.
            case DENIAL -> s.append("color=\"#000000\", style=\"dashed\", penwidth=")
                    .append(highlighted ? "3.5" : "2.2").append(", fontcolor=\"#000000\", label=\"")
                    .append(rightsLabel(e.rights())).append("\"");
        }
        return s.toString();
    }

    private static String rightsLabel(AccessRightSet rights) {
        if (rights == null || rights.isEmpty()) {
            return "";
        }
        return escape(String.join(", ", rights));
    }

    private static String escape(String s) {
        return s.replace("\"", "\\\"");
    }
}
