package com.example.ngac.support;

import gov.nist.ngac.pm.core.common.exception.PMException;
import gov.nist.ngac.pm.core.common.graph.node.Node;
import gov.nist.ngac.pm.core.common.graph.node.NodeType;
import gov.nist.ngac.pm.core.pap.PAP;
import gov.nist.ngac.pm.core.pap.operation.accessright.AccessRightSet;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.QuadCurve2D;
import java.awt.geom.RoundRectangle2D;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Draws a G_Com edge list entirely in black and white, matching the line-style convention used
 * in the paper's own figures (e.g. Fig. 3/4: solid = assignment/containment path, thin dashed =
 * association, bold dash-dot = flattened prohibition) so a screenshot or exported PNG needs no
 * further work for a grayscale print journal: ASSIGNMENT edges as solid black lines, ASSOCIATION
 * edges as thin dashed black lines labeled with their access rights, and FP (DENIAL) edges as
 * bold black dash-dot curves - drawn in their correct, reversed direction (from the denied
 * attribute towards the subject), since this is a self-contained renderer with no NGAC type
 * constraints on drawing. Node types are told apart by shape alone (rounded rect / ellipse /
 * filled block), not color. Anomaly highlighting (see setHighlights) also stays monochrome: a
 * heavier stroke plus a light gray fill, never a color swap, so a highlighted screenshot still
 * reads correctly in black and white.
 *
 * The graph shown can change over time (setGraph), so a NADA-style UI can load G, then
 * prohibitions, then swap in the computed G_Com, all in the same window.
 *
 * Zoom and pan: Ctrl + mouse wheel zooms around the mouse pointer (the plain wheel still
 * scrolls); dragging with the mouse moves the view; zoomIn / zoomOut / resetZoom / fitToWindow
 * are available for toolbar buttons and menu items. A "zoom" property change is fired whenever
 * the zoom level changes. toImage() always renders the whole graph at 100%, whatever the zoom.
 */
public final class GComGraphPanel extends JPanel {

    private static final Color LINE_COLOR = Color.BLACK;
    private static final Color HIGHLIGHT_FILL = new Color(0xd9, 0xd9, 0xd9);

    private PAP pap;
    private List<Algorithm1_2_ComplementedGraphBuilder.Edge> gCom;
    private GComLayout layout;

    private Set<Long> highlightNodes = Set.of();
    private Set<Algorithm1_2_ComplementedGraphBuilder.Edge> highlightEdges = Set.of();

    public static final double MIN_ZOOM = 0.1;
    public static final double MAX_ZOOM = 5.0;
    private static final double ZOOM_STEP = 1.2;

    /** Current zoom factor (1.0 = 100%). */
    private double zoom = 1.0;

    /** Drag-to-pan state. */
    private Point dragStartOnScreen;
    private Point viewAtDragStart;

    public GComGraphPanel(PAP pap, List<Algorithm1_2_ComplementedGraphBuilder.Edge> gCom, GComLayout layout) {
        this.pap = pap;
        this.gCom = gCom;
        this.layout = layout;
        setBackground(Color.WHITE);
        updatePreferredSize();
        installZoomAndPan();
    }

    /** Replaces the graph currently shown (e.g. after loading G, or after computing G_Com). */
    public void setGraph(PAP pap, List<Algorithm1_2_ComplementedGraphBuilder.Edge> gCom, GComLayout layout) {
        this.pap = pap;
        this.gCom = gCom;
        this.layout = layout;
        this.highlightNodes = Set.of();
        this.highlightEdges = Set.of();
        updatePreferredSize();
        revalidate();
        repaint();
    }

    // -----------------------------------------------------------------
    // Zoom and pan
    // -----------------------------------------------------------------

    public double getZoom() {
        return zoom;
    }

    public void zoomIn() {
        zoomAt(zoom * ZOOM_STEP, null);
    }

    public void zoomOut() {
        zoomAt(zoom / ZOOM_STEP, null);
    }

    /** Back to 100%. */
    public void resetZoom() {
        zoomAt(1.0, null);
    }

    /** Chooses the zoom at which the whole graph fits in the visible area. */
    public void fitToWindow() {
        JViewport viewport = viewport();
        if (viewport == null || layout == null || layout.width() <= 0 || layout.height() <= 0) {
            return;
        }
        Dimension extent = viewport.getExtentSize();
        double fit = Math.min(extent.getWidth() / layout.width(), extent.getHeight() / layout.height());
        setZoomInternal(fit);
        SwingUtilities.invokeLater(() -> viewport.setViewPosition(new Point(0, 0)));
    }

    /**
     * Sets the zoom to newZoom, keeping the graph point under anchor (panel coordinates) at the
     * same place on screen. A null anchor means the centre of the visible area.
     */
    private void zoomAt(double newZoom, Point anchor) {
        JViewport viewport = viewport();
        double oldZoom = zoom;
        Point viewPos = viewport != null ? viewport.getViewPosition() : new Point(0, 0);
        if (anchor == null) {
            Dimension extent = viewport != null ? viewport.getExtentSize() : getSize();
            anchor = new Point(viewPos.x + extent.width / 2, viewPos.y + extent.height / 2);
        }
        if (!setZoomInternal(newZoom) || viewport == null) {
            return;
        }
        double ratio = zoom / oldZoom;
        int offsetX = anchor.x - viewPos.x;
        int offsetY = anchor.y - viewPos.y;
        Point target = new Point((int) Math.round(anchor.x * ratio) - offsetX,
                                 (int) Math.round(anchor.y * ratio) - offsetY);
        SwingUtilities.invokeLater(() -> viewport.setViewPosition(clampToView(viewport, target)));
    }

    /** Applies a clamped zoom level; returns false if nothing changed. */
    private boolean setZoomInternal(double newZoom) {
        double clamped = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, newZoom));
        if (Math.abs(clamped - zoom) < 1e-9) {
            return false;
        }
        double old = zoom;
        zoom = clamped;
        updatePreferredSize();
        setSize(getPreferredSize());
        revalidate();
        repaint();
        firePropertyChange("zoom", old, zoom);
        return true;
    }

    private void updatePreferredSize() {
        int w = layout == null ? 0 : (int) Math.ceil(layout.width() * zoom);
        int h = layout == null ? 0 : (int) Math.ceil(layout.height() * zoom);
        setPreferredSize(new Dimension(w, h));
    }

    private JViewport viewport() {
        return (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, this);
    }

    private Point clampToView(JViewport viewport, Point p) {
        Dimension view = getPreferredSize();
        Dimension extent = viewport.getExtentSize();
        int x = Math.max(0, Math.min(p.x, view.width - extent.width));
        int y = Math.max(0, Math.min(p.y, view.height - extent.height));
        return new Point(x, y);
    }

    private void installZoomAndPan() {
        setToolTipText("Ctrl + mouse wheel: zoom   |   drag: move the view");

        // Ctrl + wheel zooms around the pointer; the plain wheel is handed to the scroll pane.
        addMouseWheelListener(e -> {
            if (e.isControlDown()) {
                zoomAt(zoom * Math.pow(ZOOM_STEP, -e.getPreciseWheelRotation()), e.getPoint());
            } else {
                JScrollPane scrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
                if (scrollPane != null) {
                    scrollPane.dispatchEvent(SwingUtilities.convertMouseEvent(this, e, scrollPane));
                }
            }
        });

        // Drag to move the view.
        MouseAdapter pan = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                JViewport viewport = viewport();
                if (viewport == null) {
                    return;
                }
                dragStartOnScreen = e.getLocationOnScreen();
                viewAtDragStart = viewport.getViewPosition();
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                JViewport viewport = viewport();
                if (viewport == null || dragStartOnScreen == null) {
                    return;
                }
                Point now = e.getLocationOnScreen();
                Point target = new Point(viewAtDragStart.x - (now.x - dragStartOnScreen.x),
                                         viewAtDragStart.y - (now.y - dragStartOnScreen.y));
                viewport.setViewPosition(clampToView(viewport, target));
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragStartOnScreen = null;
                setCursor(Cursor.getDefaultCursor());
            }
        };
        addMouseListener(pan);
        addMouseMotionListener(pan);
    }

    /** Renders the whole graph at 100% (independent of the on-screen zoom), e.g. for PNG export. */
    public BufferedImage toImage() {
        int w = Math.max(1, layout == null ? 1 : layout.width());
        int h = Math.max(1, layout == null ? 1 : layout.height());
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, w, h);
        paintGraph(g2);
        g2.dispose();
        return image;
    }

    public void setHighlights(Set<Long> nodes, Set<Algorithm1_2_ComplementedGraphBuilder.Edge> edges) {
        this.highlightNodes = nodes;
        this.highlightEdges = edges;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            if (pap == null || gCom.isEmpty()) {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
                g2.setColor(new Color(0x88, 0x88, 0x88));
                g2.drawString("No graph loaded yet. Use the toolbar above to load G, then prohibitions.", 20, 30);
                return;
            }
            g2.scale(zoom, zoom);
            paintGraph(g2);
        } finally {
            g2.dispose();
        }
    }

    /** Draws the graph in layout coordinates (zoom already applied by the caller, if any). */
    private void paintGraph(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        if (pap == null || gCom.isEmpty()) {
            return;
        }

        try {
            drawEdges(g2, Algorithm1_2_ComplementedGraphBuilder.EdgeKind.ASSIGNMENT);
            drawEdges(g2, Algorithm1_2_ComplementedGraphBuilder.EdgeKind.ASSOCIATION);
            drawEdges(g2, Algorithm1_2_ComplementedGraphBuilder.EdgeKind.DENIAL);
            drawNodes(g2);
        } catch (PMException e) {
            g2.setColor(Color.BLACK);
            g2.drawString("Error while drawing graph: " + e.getMessage(), 10, 20);
        }
    }

    private void drawNodes(Graphics2D g2) throws PMException {
        Set<Long> seen = new HashSet<>();
        for (Algorithm1_2_ComplementedGraphBuilder.Edge e : gCom) {
            seen.add(e.source());
            seen.add(e.target());
        }
        for (long id : seen) {
            Node n = pap.query().graph().getNodeById(id);
            Point2D.Double p = layout.position(id);
            if (p == null) {
                continue;
            }
            boolean hl = highlightNodes.contains(id);
            drawNode(g2, p, n.getName(), n.getType(), hl);
        }
    }

    private void drawNode(Graphics2D g2, Point2D.Double p, String name, NodeType type, boolean highlighted) {
        double w = 90;
        double h = 34;
        double x = p.x - w / 2;
        double y = p.y - h / 2;

        // Node types are told apart by shape alone (no color): rounded rect for U/UA (UA has a
        // slightly larger corner radius), ellipse for OA/O, and a filled black block for PC -
        // matching the black-and-white convention used throughout this panel and the paper.
        Shape shape;
        boolean filledBlock = false;

        switch (type) {
            case U -> shape = new RoundRectangle2D.Double(x, y, w, h, 6, 6);
            case UA -> shape = new RoundRectangle2D.Double(x, y, w, h, 14, 14);
            case OA, O -> shape = new Ellipse2D.Double(x, y, w, h);
            case PC -> {
                shape = new RoundRectangle2D.Double(x, y, w, h, 20, 20);
                filledBlock = true;
            }
            default -> shape = new RoundRectangle2D.Double(x, y, w, h, 6, 6);
        }

        g2.setColor(highlighted ? HIGHLIGHT_FILL : (filledBlock ? Color.BLACK : Color.WHITE));
        g2.fill(shape);
        g2.setStroke(new BasicStroke(highlighted ? 3f : 1.6f));
        g2.setColor(Color.BLACK);
        g2.draw(shape);

        g2.setColor(filledBlock && !highlighted ? Color.WHITE : Color.BLACK);
        FontMetrics fm = g2.getFontMetrics();
        int textWidth = fm.stringWidth(name);
        g2.drawString(name, (float) (p.x - textWidth / 2.0), (float) (p.y + fm.getAscent() / 2.0 - 2));
    }

    private void drawEdges(Graphics2D g2, Algorithm1_2_ComplementedGraphBuilder.EdgeKind kind) {
        for (Algorithm1_2_ComplementedGraphBuilder.Edge e : gCom) {
            if (e.kind() != kind) {
                continue;
            }
            Point2D.Double from = layout.position(e.source());
            Point2D.Double to = layout.position(e.target());
            if (from == null || to == null) {
                continue;
            }
            boolean hl = highlightEdges.contains(e);
            switch (kind) {
                case ASSIGNMENT -> drawStraightEdge(g2, from, to, false, null, hl);
                case ASSOCIATION -> drawStraightEdge(g2, from, to, true, labelFor(e.rights()), hl);
                case DENIAL -> drawCurvedEdge(g2, from, to, labelFor(e.rights()), hl);
            }
        }
    }

    private String labelFor(AccessRightSet rights) {
        if (rights == null || rights.isEmpty()) {
            return null;
        }
        return String.join(", ", rights);
    }

    private void drawStraightEdge(Graphics2D g2, Point2D.Double from, Point2D.Double to,
                                   boolean dashed, String label, boolean highlighted) {
        g2.setColor(LINE_COLOR);
        g2.setStroke(dashed
                ? new BasicStroke(highlighted ? 3f : 1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{5f, 4f}, 0f)
                : new BasicStroke(highlighted ? 3f : 1.4f));
        g2.draw(new Line2D.Double(from, to));
        drawArrowHead(g2, from, to, LINE_COLOR);
        if (label != null) {
            double mx = (from.x + to.x) / 2;
            double my = (from.y + to.y) / 2;
            g2.setColor(LINE_COLOR);
            g2.drawString(label, (float) mx + 4, (float) my - 4);
        }
    }

    /** Bold black dash-dot curve, matching the paper's flattened-prohibition line style. */
    private void drawCurvedEdge(Graphics2D g2, Point2D.Double from, Point2D.Double to,
                                 String label, boolean highlighted) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dist = Math.hypot(dx, dy);
        double offset = Math.min(dist * 0.25, 90);
        double nx = dist == 0 ? 0 : -dy / dist;
        double ny = dist == 0 ? 0 : dx / dist;
        double cx = (from.x + to.x) / 2 + nx * offset;
        double cy = (from.y + to.y) / 2 + ny * offset;

        QuadCurve2D curve = new QuadCurve2D.Double(from.x, from.y, cx, cy, to.x, to.y);
        g2.setColor(LINE_COLOR);
        g2.setStroke(new BasicStroke(highlighted ? 3.6f : 2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f,
                new float[]{8f, 3f, 1f, 3f}, 0f));
        g2.draw(curve);

        drawArrowHead(g2, new Point2D.Double(cx, cy), to, LINE_COLOR);

        if (label != null) {
            g2.setColor(LINE_COLOR);
            g2.drawString(label, (float) cx + 4, (float) cy - 4);
        }
    }

    private void drawArrowHead(Graphics2D g2, Point2D.Double from, Point2D.Double to, Color color) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dist = Math.hypot(dx, dy);
        double angle = Math.atan2(dy, dx);
        double arrowLen = 10;
        double arrowAngle = Math.toRadians(22);
        double nodeHalfW = 45;
        double ratio = dist == 0 ? 0 : Math.max(0, (dist - nodeHalfW) / dist);
        double tipX = from.x + dx * ratio;
        double tipY = from.y + dy * ratio;

        GeneralPath arrow = new GeneralPath();
        arrow.moveTo(tipX, tipY);
        arrow.lineTo(tipX - arrowLen * Math.cos(angle - arrowAngle), tipY - arrowLen * Math.sin(angle - arrowAngle));
        arrow.lineTo(tipX - arrowLen * Math.cos(angle + arrowAngle), tipY - arrowLen * Math.sin(angle + arrowAngle));
        arrow.closePath();
        g2.setColor(color);
        g2.fill(arrow);
    }
}
