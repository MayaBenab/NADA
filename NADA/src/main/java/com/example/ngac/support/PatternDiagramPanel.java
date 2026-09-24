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
 * Draws one {@link RedundancyPattern} - a single redundancy finding, instantiated with real node
 * names - in the same visual notation as the paper's Fig. 3 ("Full or Partial Redundancies") and
 * Fig. 4 ("Hierarchical redundancies"): circles for nodes, a solid black arrow for an
 * assignment/containment path, a thin dashed black arrow (ua -&gt; at) for an association, and a
 * bold black dash-dot arrow (at -&gt; subject, reversed) for a flattened prohibition. Entirely
 * black and white, matching those figures and the rest of this tool's graph rendering.
 *
 * One method, {@link #paintComponent}, covers all eight patterns generically: a "shared" side
 * (patterns 1, 2, 4, 5, 7, 8) is drawn as a single node with both relation-arrows leaving from
 * (or arriving at) it; a "split" side (patterns 1-8 as applicable) is drawn as two nodes, top
 * (the dominated rule, p1) and bottom (the dominant rule, p2), joined by a solid containment
 * arrow pointing from the contained (top) down to the container (bottom). When both sides happen
 * to be shared (patterns 1 and 2 - Algorithm 3's exact-same-pair case) the two relation-arrows
 * would otherwise coincide, so they are drawn as a small parallel fan instead, exactly like the
 * two dashed lines in Fig. 3.
 */
public final class PatternDiagramPanel extends JPanel {

    private static final int LEFT_X = 140;
    private static final int RIGHT_X = 420;
    private static final int TOP_Y = 80;
    private static final int BOTTOM_Y = 220;
    private static final int SHARED_Y = 150;
    private static final double NODE_R = 36;

    private RedundancyPattern pattern;

    public PatternDiagramPanel() {
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(560, 300));
    }

    public void setPattern(RedundancyPattern pattern) {
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

        if (pattern.pcNote() != null && !pattern.pcNote().isBlank()) {
            g2.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 12));
            g2.setColor(new Color(0x33, 0x33, 0x33));
            g2.drawString(pattern.pcNote(), 16, 40);
        }

        Point2D.Double leftTop = new Point2D.Double(LEFT_X, pattern.leftShared() ? SHARED_Y : TOP_Y);
        Point2D.Double leftBottom = new Point2D.Double(LEFT_X, pattern.leftShared() ? SHARED_Y : BOTTOM_Y);
        Point2D.Double rightTop = new Point2D.Double(RIGHT_X, pattern.rightShared() ? SHARED_Y : TOP_Y);
        Point2D.Double rightBottom = new Point2D.Double(RIGHT_X, pattern.rightShared() ? SHARED_Y : BOTTOM_Y);

        // Column headers ("ua"/"u or ua" on the left, "at" on the right), matching Fig. 3/4.
        g2.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 12));
        g2.setColor(new Color(0x55, 0x55, 0x55));
        g2.drawString(pattern.leftAxisLabel(), LEFT_X - 10, TOP_Y - 50);
        g2.drawString(pattern.rightAxisLabel(), RIGHT_X - 10, TOP_Y - 50);

        // Solid containment arrows, only where that side is actually split.
        g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        if (!pattern.leftShared()) {
            drawContainmentArrow(g2, leftTop, leftBottom);
        }
        if (!pattern.rightShared()) {
            drawContainmentArrow(g2, rightTop, rightBottom);
        }

        // The two relation arrows (association or flattened-prohibition), fanned apart when both
        // sides are shared (patterns 1/2) so they don't draw exactly on top of each other.
        Point2D.Double topLeftPt = leftTop;
        Point2D.Double topRightPt = rightTop;
        Point2D.Double bottomLeftPt = leftBottom;
        Point2D.Double bottomRightPt = rightBottom;
        if (pattern.leftShared() && pattern.rightShared()) {
            topLeftPt = new Point2D.Double(LEFT_X, SHARED_Y - 14);
            topRightPt = new Point2D.Double(RIGHT_X, SHARED_Y - 14);
            bottomLeftPt = new Point2D.Double(LEFT_X, SHARED_Y + 14);
            bottomRightPt = new Point2D.Double(RIGHT_X, SHARED_Y + 14);
        }
        drawRelationArrow(g2, topLeftPt, topRightPt, pattern.rightsTop(), pattern.isProhibition());
        drawRelationArrow(g2, bottomLeftPt, bottomRightPt, pattern.rightsBottom(), pattern.isProhibition());

        // Nodes drawn last so their circles sit cleanly on top of the arrow ends.
        drawNode(g2, leftTop, pattern.leftShared() ? sharedLabel(pattern.leftTop()) : pattern.leftTop());
        if (!pattern.leftShared()) {
            drawNode(g2, leftBottom, pattern.leftBottom());
        }
        drawNode(g2, rightTop, pattern.rightShared() ? sharedLabel(pattern.rightTop()) : pattern.rightTop());
        if (!pattern.rightShared()) {
            drawNode(g2, rightBottom, pattern.rightBottom());
        }
    }

    private String sharedLabel(String name) {
        return name;
    }

    private void drawNode(Graphics2D g2, Point2D.Double center, String name) {
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
     * Association (ua -&gt; at, thin dashed) or flattened prohibition (at -&gt; subject, reversed,
     * bold dash-dot) arrow between a left-side and a right-side point.
     */
    private void drawRelationArrow(Graphics2D g2, Point2D.Double leftPt, Point2D.Double rightPt,
                                    String rightsLabel, boolean isProhibition) {
        Point2D.Double from = isProhibition ? rightPt : leftPt;
        Point2D.Double to = isProhibition ? leftPt : rightPt;
        Point2D.Double start = shrinkToward(from, to, NODE_R + 2);
        Point2D.Double end = shrinkToward(to, from, NODE_R + 2);

        g2.setColor(Color.BLACK);
        g2.setStroke(isProhibition
                ? new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{8f, 3f, 1f, 3f}, 0f)
                : new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{5f, 4f}, 0f));
        g2.draw(new Line2D.Double(start, end));
        drawArrowHead(g2, start, end);

        if (rightsLabel != null && !rightsLabel.isBlank()) {
            double mx = (leftPt.x + rightPt.x) / 2;
            double my = (leftPt.y + rightPt.y) / 2;
            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            g2.setColor(Color.BLACK);
            g2.drawString(rightsLabel, (float) mx - g2.getFontMetrics().stringWidth(rightsLabel) / 2f,
                    (float) my - 6);
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
