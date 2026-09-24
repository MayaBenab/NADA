package com.example.ngac.support;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;

/**
 * Draws one {@link ConflictPattern} - a single conflict finding (Algorithm 5), instantiated with
 * real node names - in the paper's Fig. 5 notation: circles for nodes, a thin dashed black arrow
 * for the permission (a1 -&gt; a2), a bold black dash-dot arrow for the flattened prohibition
 * (b2 -&gt; b1, reversed), and a solid black arrow for a containment path.
 *
 * <p>Unlike {@link PatternDiagramPanel} (Fig. 3/4), where the dominated rule always sits on top
 * and the dominant rule always on the bottom on <em>both</em> sides at once, a conflict's subject
 * side and object side are independent: either one can have a1 containing b1, b1 containing a1,
 * or the two equal. This panel resolves each side separately - always drawing the *contained*
 * node on top and the *container* on the bottom (matching Fig. 3/4's own arrow convention, solid
 * arrow from contained up top down to the container) - which is why the permission/prohibition
 * arrows sometimes end up horizontal (Patterns 10, 12) and sometimes cross diagonally (Patterns
 * 11, 13): the crossing is a direct, honest picture of "this side is aligned, that side is
 * reversed", not a stylistic choice.
 */
public final class ConflictPatternDiagramPanel extends JPanel {

    private static final int LEFT_X = 140;
    private static final int RIGHT_X = 420;
    private static final int TOP_Y = 80;
    private static final int BOTTOM_Y = 220;
    private static final int SHARED_Y = 150;
    private static final double NODE_R = 36;

    private ConflictPattern pattern;

    public ConflictPatternDiagramPanel() {
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(560, 300));
    }

    public void setPattern(ConflictPattern pattern) {
        this.pattern = pattern;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (pattern == null) {
            g2.setColor(new Color(0x88, 0x88, 0x88));
            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            g2.drawString("Select a finding on the left to see its pattern diagram.", 20, 30);
            return;
        }

        g2.setColor(Color.BLACK);
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        g2.drawString(pattern.caption(), 16, 22);

        // Resolve each side's two positions: the CONTAINED node always on top, the CONTAINER
        // always on the bottom (or a single shared point when that side is shared/equal).
        Point2D.Double a1Pt, b1Pt, a2Pt, b2Pt;
        if (pattern.subjectShared()) {
            a1Pt = b1Pt = new Point2D.Double(LEFT_X, SHARED_Y);
        } else if (pattern.subjectAContainsB()) {
            // a1 contains b1 -> b1 is contained (top), a1 is the container (bottom).
            b1Pt = new Point2D.Double(LEFT_X, TOP_Y);
            a1Pt = new Point2D.Double(LEFT_X, BOTTOM_Y);
        } else {
            // b1 contains a1 -> a1 is contained (top), b1 is the container (bottom).
            a1Pt = new Point2D.Double(LEFT_X, TOP_Y);
            b1Pt = new Point2D.Double(LEFT_X, BOTTOM_Y);
        }
        if (pattern.objectShared()) {
            a2Pt = b2Pt = new Point2D.Double(RIGHT_X, SHARED_Y);
        } else if (pattern.objectAContainsB()) {
            b2Pt = new Point2D.Double(RIGHT_X, TOP_Y);
            a2Pt = new Point2D.Double(RIGHT_X, BOTTOM_Y);
        } else {
            a2Pt = new Point2D.Double(RIGHT_X, TOP_Y);
            b2Pt = new Point2D.Double(RIGHT_X, BOTTOM_Y);
        }

        // Column headers, matching Fig. 5's own a1/b1 (left) and a2/b2 (right) labelling.
        g2.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 12));
        g2.setColor(new Color(0x55, 0x55, 0x55));
        g2.drawString("ua (permission) / u or ua (prohibition)", LEFT_X - 90, TOP_Y - 50);
        g2.drawString("at", RIGHT_X - 10, TOP_Y - 50);

        // Solid containment arrows, contained (top) -> container (bottom), only where that side
        // actually has two distinct nodes.
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        if (!pattern.subjectShared()) {
            Point2D.Double top = pattern.subjectAContainsB() ? b1Pt : a1Pt;
            Point2D.Double bottom = pattern.subjectAContainsB() ? a1Pt : b1Pt;
            drawContainmentArrow(g2, top, bottom);
        }
        if (!pattern.objectShared()) {
            Point2D.Double top = pattern.objectAContainsB() ? b2Pt : a2Pt;
            Point2D.Double bottom = pattern.objectAContainsB() ? a2Pt : b2Pt;
            drawContainmentArrow(g2, top, bottom);
        }

        // The permission (a1 -> a2, thin dashed) and prohibition (b2 -> b1, bold dash-dot) arrows -
        // horizontal when both sides resolve the same way, diagonal (crossing) when they don't.
        // Fanned apart into a small parallel pair only in the degenerate case where every endpoint
        // coincides (Pattern 9 - both sides shared), so the two arrows don't draw on top of each other.
        Point2D.Double permFrom = a1Pt, permTo = a2Pt, prohFrom = b2Pt, prohTo = b1Pt;
        if (pattern.subjectShared() && pattern.objectShared()) {
            permFrom = new Point2D.Double(LEFT_X, SHARED_Y - 14);
            permTo = new Point2D.Double(RIGHT_X, SHARED_Y - 14);
            prohFrom = new Point2D.Double(RIGHT_X, SHARED_Y + 14);
            prohTo = new Point2D.Double(LEFT_X, SHARED_Y + 14);
        }
        drawRelationArrow(g2, permFrom, permTo, pattern.permRights(), false);
        drawRelationArrow(g2, prohFrom, prohTo, pattern.prohRights(), true);

        // Nodes drawn last so their circles sit cleanly on top of the arrow ends. When a side is
        // shared, a1/b1 (or a2/b2) sit at the exact same point - draw once, label with both roles.
        drawNode(g2, a1Pt, pattern.subjectShared() ? pattern.a1Name() : pattern.a1Name(), "a1");
        if (!pattern.subjectShared()) {
            drawNode(g2, b1Pt, pattern.b1Name(), "b1");
        } else {
            drawRoleTag(g2, a1Pt, "a1 = b1");
        }
        drawNode(g2, a2Pt, pattern.a2Name(), "a2");
        if (!pattern.objectShared()) {
            drawNode(g2, b2Pt, pattern.b2Name(), "b2");
        } else {
            drawRoleTag(g2, a2Pt, "a2 = b2");
        }
    }

    private void drawNode(Graphics2D g2, Point2D.Double center, String name, String role) {
        Ellipse2D circle = new Ellipse2D.Double(center.x - NODE_R, center.y - NODE_R, NODE_R * 2, NODE_R * 2);
        g2.setColor(Color.WHITE);
        g2.fill(circle);
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(1.8f));
        g2.draw(circle);

        int size = 12;
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, size);
        FontMetrics fm = g2.getFontMetrics(font);
        double maxWidth = NODE_R * 2 - 10;
        String label = name == null ? "" : name;
        while (size > 8 && fm.stringWidth(label) > maxWidth) {
            size--;
            font = new Font(Font.SANS_SERIF, Font.PLAIN, size);
            fm = g2.getFontMetrics(font);
        }
        while (fm.stringWidth(label) > maxWidth && label.length() > 1) {
            label = label.substring(0, label.length() - 1);
            fm = g2.getFontMetrics(font);
            if (fm.stringWidth(label + "…") <= maxWidth) {
                label = label + "…";
                break;
            }
        }
        g2.setFont(font);
        g2.setColor(Color.BLACK);
        int textWidth = fm.stringWidth(label);
        g2.drawString(label, (float) (center.x - textWidth / 2.0), (float) (center.y + fm.getAscent() / 2.0 - 2));

        drawRoleTag(g2, center, role);
    }

    /** Small italic role tag (a1/b1/a2/b2, or "a1 = b1" when shared) above-right of a node. */
    private void drawRoleTag(Graphics2D g2, Point2D.Double center, String role) {
        g2.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 11));
        g2.setColor(new Color(0x77, 0x77, 0x77));
        g2.drawString(role, (float) (center.x + NODE_R * 0.55), (float) (center.y - NODE_R * 0.75));
    }

    /** Solid black arrow, contained (top) -&gt; container (bottom) - an assignment/containment path. */
    private void drawContainmentArrow(Graphics2D g2, Point2D.Double top, Point2D.Double bottom) {
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(1.6f));
        Point2D.Double from = shrinkToward(top, bottom, NODE_R + 2);
        Point2D.Double to = shrinkToward(bottom, top, NODE_R + 2);
        g2.draw(new Line2D.Double(from, to));
        drawArrowHead(g2, from, to);
    }

    /**
     * Permission (a1 -&gt; a2, thin dashed) or flattened prohibition (b2 -&gt; b1, bold dash-dot)
     * arrow between two arbitrary points - horizontal or diagonal, whichever the resolved
     * positions call for.
     */
    private void drawRelationArrow(Graphics2D g2, Point2D.Double from, Point2D.Double to,
                                    String rightsLabel, boolean isProhibition) {
        Point2D.Double start = shrinkToward(from, to, NODE_R + 2);
        Point2D.Double end = shrinkToward(to, from, NODE_R + 2);

        g2.setColor(Color.BLACK);
        g2.setStroke(isProhibition
                ? new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{8f, 3f, 1f, 3f}, 0f)
                : new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{5f, 4f}, 0f));
        g2.draw(new Line2D.Double(start, end));
        drawArrowHead(g2, start, end);

        if (rightsLabel != null && !rightsLabel.isBlank()) {
            double mx = (from.x + to.x) / 2;
            double my = (from.y + to.y) / 2;
            String label = (isProhibition ? "ars(proh): " : "ars(perm): ") + rightsLabel;
            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            g2.setColor(Color.BLACK);
            g2.drawString(label, (float) mx - g2.getFontMetrics().stringWidth(label) / 2f, (float) my - 6);
        }
    }

    /** Moves `to` back toward `from` by `distance`, so lines/arrowheads stop at a node's edge. */
    private Point2D.Double shrinkToward(Point2D.Double moving, Point2D.Double other, double distance) {
        double dx = other.x - moving.x;
        double dy = other.y - moving.y;
        double dist = Math.hypot(dx, dy);
        if (dist <= distance || dist == 0) {
            return moving;
        }
        double ratio = distance / dist;
        return new Point2D.Double(moving.x + dx * ratio, moving.y + dy * ratio);
    }

    private void drawArrowHead(Graphics2D g2, Point2D.Double from, Point2D.Double to) {
        double angle = Math.atan2(to.y - from.y, to.x - from.x);
        double arrowLen = 10;
        double arrowAngle = Math.toRadians(24);
        GeneralPath arrow = new GeneralPath();
        arrow.moveTo(to.x, to.y);
        arrow.lineTo(to.x - arrowLen * Math.cos(angle - arrowAngle), to.y - arrowLen * Math.sin(angle - arrowAngle));
        arrow.lineTo(to.x - arrowLen * Math.cos(angle + arrowAngle), to.y - arrowLen * Math.sin(angle + arrowAngle));
        arrow.closePath();
        g2.setColor(Color.BLACK);
        g2.fill(arrow);
    }
}
