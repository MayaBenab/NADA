package com.example.ngac.support;

import gov.nist.ngac.pm.core.common.exception.PMException;
import gov.nist.ngac.pm.core.common.graph.node.NodeType;
import gov.nist.ngac.pm.core.pap.PAP;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Lays out a G_Com edge list. Tries Graphviz's own "dot" layout engine first (the same tool
 * already used to export the PDF/PNG figures), so the on-screen graph in the GUI matches those
 * exported figures exactly. Falls back to a simple hand-rolled layered layout (U/UA on the
 * left, O/OA on the right, PC in a row at the bottom) if "dot" is not installed or fails.
 */
public final class GComLayout {

    public static final int NODE_H_SPACING = 150;
    public static final int NODE_V_SPACING = 130;
    public static final int MARGIN = 70;
    public static final int SIDE_GAP = 220;
    private static final double GRAPHVIZ_SCALE = 100.0; // pixels per inch

    private final Map<Long, Point2D.Double> positions;
    private final int width;
    private final int height;

    private GComLayout(Map<Long, Point2D.Double> positions, int width, int height) {
        this.positions = positions;
        this.width = width;
        this.height = height;
    }

    public Point2D.Double position(long nodeId) {
        return positions.get(nodeId);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public static GComLayout compute(PAP pap, List<Algorithm1_2_ComplementedGraphBuilder.Edge> gCom) throws PMException {
        if (gCom.isEmpty()) {
            return new GComLayout(new HashMap<>(), 400, 300);
        }
        try {
            return computeViaGraphviz(pap, gCom);
        } catch (Exception ex) {
            return computeLayered(pap, gCom);
        }
    }

    // ---------------------------------------------------------------------
    // Graphviz-based layout (preferred): shells out to "dot -Tplain" and reads back the exact
    // node positions Graphviz computed for this graph.
    // ---------------------------------------------------------------------

    private static GComLayout computeViaGraphviz(PAP pap, List<Algorithm1_2_ComplementedGraphBuilder.Edge> gCom) throws Exception {
        String dot = GComDotExporter.toDot(pap, gCom, Set.of(), Set.of());

        Path dotFile = Files.createTempFile("gcom", ".dot");
        Path plainFile = Files.createTempFile("gcom", ".plain");
        try {
            Files.writeString(dotFile, dot);

            ProcessBuilder pb = new ProcessBuilder("dot", "-Tplain", "-o", plainFile.toString(), dotFile.toString());
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            proc.getInputStream().readAllBytes();
            boolean finished = proc.waitFor(15, TimeUnit.SECONDS);
            if (!finished || proc.exitValue() != 0) {
                throw new IOException("dot did not complete successfully");
            }

            return parsePlain(Files.readString(plainFile));
        } finally {
            Files.deleteIfExists(dotFile);
            Files.deleteIfExists(plainFile);
        }
    }

    private static GComLayout parsePlain(String plain) throws IOException {
        Map<Long, Point2D.Double> raw = new HashMap<>();
        double graphWidthInches = 0;
        double graphHeightInches = 0;

        for (String line : plain.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] parts = trimmed.split("\\s+");
            if (parts[0].equals("graph") && parts.length >= 4) {
                graphWidthInches = Double.parseDouble(parts[2]);
                graphHeightInches = Double.parseDouble(parts[3]);
            } else if (parts[0].equals("node") && parts.length >= 4) {
                long id = Long.parseLong(parts[1]);
                double xIn = Double.parseDouble(parts[2]);
                double yIn = Double.parseDouble(parts[3]);
                raw.put(id, new Point2D.Double(xIn * GRAPHVIZ_SCALE, yIn * GRAPHVIZ_SCALE));
            }
        }

        if (raw.isEmpty()) {
            throw new IOException("no nodes parsed from dot output");
        }

        // flip Y (Graphviz's origin is bottom-left, Swing's is top-left) and add a margin
        double heightPx = graphHeightInches * GRAPHVIZ_SCALE;
        Map<Long, Point2D.Double> positions = new HashMap<>();
        for (Map.Entry<Long, Point2D.Double> e : raw.entrySet()) {
            Point2D.Double p = e.getValue();
            positions.put(e.getKey(), new Point2D.Double(p.x + MARGIN, (heightPx - p.y) + MARGIN));
        }

        int width = (int) (graphWidthInches * GRAPHVIZ_SCALE) + MARGIN * 2;
        int height = (int) heightPx + MARGIN * 2;
        return new GComLayout(positions, Math.max(width, 400), Math.max(height, 300));
    }

    // ---------------------------------------------------------------------
    // Fallback layout: U/UA on the left, O/OA on the right, PC in a row at the bottom, used only
    // if Graphviz ("dot") is not available on this machine.
    // ---------------------------------------------------------------------

    private static GComLayout computeLayered(PAP pap, List<Algorithm1_2_ComplementedGraphBuilder.Edge> gCom) throws PMException {
        Set<Long> allNodes = new HashSet<>();
        for (Algorithm1_2_ComplementedGraphBuilder.Edge e : gCom) {
            allNodes.add(e.source());
            allNodes.add(e.target());
        }

        Map<Long, NodeType> typeOf = new HashMap<>();
        for (long id : allNodes) {
            typeOf.put(id, pap.query().graph().getNodeById(id).getType());
        }

        Set<Long> leftNodes = new HashSet<>();
        Set<Long> rightNodes = new HashSet<>();
        List<Long> pcNodes = new ArrayList<>();
        for (long id : allNodes) {
            NodeType t = typeOf.get(id);
            if (t == NodeType.PC) {
                pcNodes.add(id);
            } else if (t == NodeType.U || t == NodeType.UA) {
                leftNodes.add(id);
            } else {
                rightNodes.add(id);
            }
        }
        pcNodes.sort(Long::compareTo);

        Map<Long, List<Long>> parentsOf = new HashMap<>();
        for (Algorithm1_2_ComplementedGraphBuilder.Edge e : gCom) {
            if (e.kind() != Algorithm1_2_ComplementedGraphBuilder.EdgeKind.ASSIGNMENT) {
                continue;
            }
            boolean sameSide = (leftNodes.contains(e.source()) && leftNodes.contains(e.target()))
                    || (rightNodes.contains(e.source()) && rightNodes.contains(e.target()));
            if (sameSide) {
                parentsOf.computeIfAbsent(e.source(), k -> new ArrayList<>()).add(e.target());
            }
        }

        Map<Long, Integer> depthCache = new HashMap<>();
        for (long n : leftNodes) {
            depthOf(n, parentsOf, depthCache, new HashSet<>());
        }
        for (long n : rightNodes) {
            depthOf(n, parentsOf, depthCache, new HashSet<>());
        }

        SideLayout leftLayout = layoutSideLocal(leftNodes, depthCache);
        SideLayout rightLayout = layoutSideLocal(rightNodes, depthCache);

        Map<Long, Point2D.Double> positions = new HashMap<>();
        for (Map.Entry<Long, Point2D.Double> e : leftLayout.local.entrySet()) {
            Point2D.Double p = e.getValue();
            positions.put(e.getKey(), new Point2D.Double(MARGIN + p.x, MARGIN + p.y));
        }
        double rightBaseX = MARGIN + leftLayout.width + SIDE_GAP;
        for (Map.Entry<Long, Point2D.Double> e : rightLayout.local.entrySet()) {
            Point2D.Double p = e.getValue();
            positions.put(e.getKey(), new Point2D.Double(rightBaseX + p.x, MARGIN + p.y));
        }

        int maxRows = Math.max(leftLayout.rows, rightLayout.rows);
        int totalWidth = MARGIN + leftLayout.width + SIDE_GAP + rightLayout.width + MARGIN;

        int pcRow = maxRows;
        double pcRowWidth = Math.max(pcNodes.size(), 1) * (double) NODE_H_SPACING;
        double pcStartX = MARGIN + (totalWidth - MARGIN * 2 - pcRowWidth) / 2.0;
        for (int i = 0; i < pcNodes.size(); i++) {
            double x = pcStartX + i * NODE_H_SPACING + NODE_H_SPACING / 2.0;
            double y = MARGIN + pcRow * NODE_V_SPACING;
            positions.put(pcNodes.get(i), new Point2D.Double(x, y));
        }

        int height = MARGIN * 2 + (pcRow + 1) * NODE_V_SPACING;
        return new GComLayout(positions, Math.max(totalWidth, 400), Math.max(height, 300));
    }

    private static final class SideLayout {
        final Map<Long, Point2D.Double> local;
        final int width;
        final int rows;

        SideLayout(Map<Long, Point2D.Double> local, int width, int rows) {
            this.local = local;
            this.width = width;
            this.rows = rows;
        }
    }

    private static SideLayout layoutSideLocal(Set<Long> sideNodes, Map<Long, Integer> depthCache) {
        if (sideNodes.isEmpty()) {
            return new SideLayout(new HashMap<>(), 0, 0);
        }
        int maxDepth = 0;
        for (long n : sideNodes) {
            maxDepth = Math.max(maxDepth, depthCache.get(n));
        }

        Map<Integer, List<Long>> rows = new HashMap<>();
        for (long n : sideNodes) {
            int row = maxDepth - depthCache.get(n);
            rows.computeIfAbsent(row, k -> new ArrayList<>()).add(n);
        }

        int maxRowSize = 1;
        for (List<Long> row : rows.values()) {
            row.sort(Long::compareTo);
            maxRowSize = Math.max(maxRowSize, row.size());
        }

        Map<Long, Point2D.Double> local = new HashMap<>();
        for (Map.Entry<Integer, List<Long>> entry : rows.entrySet()) {
            int row = entry.getKey();
            List<Long> ids = entry.getValue();
            int n = ids.size();
            double rowWidth = n * NODE_H_SPACING;
            double startX = (maxRowSize * NODE_H_SPACING - rowWidth) / 2.0;
            for (int i = 0; i < n; i++) {
                double x = startX + i * NODE_H_SPACING + NODE_H_SPACING / 2.0;
                double y = row * NODE_V_SPACING;
                local.put(ids.get(i), new Point2D.Double(x, y));
            }
        }
        return new SideLayout(local, maxRowSize * NODE_H_SPACING, maxDepth + 1);
    }

    private static int depthOf(long n, Map<Long, List<Long>> parentsOf, Map<Long, Integer> memo, Set<Long> visiting) {
        Integer cached = memo.get(n);
        if (cached != null) {
            return cached;
        }
        if (!visiting.add(n)) {
            return 0;
        }
        List<Long> parents = parentsOf.get(n);
        int d;
        if (parents == null || parents.isEmpty()) {
            d = 0;
        } else {
            int maxParentDepth = 0;
            for (long p : parents) {
                maxParentDepth = Math.max(maxParentDepth, depthOf(p, parentsOf, memo, visiting));
            }
            d = 1 + maxParentDepth;
        }
        visiting.remove(n);
        memo.put(n, d);
        return d;
    }
}
