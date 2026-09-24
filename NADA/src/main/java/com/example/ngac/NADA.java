package com.example.ngac;

import com.example.ngac.support.AnomalyDetector;
import com.example.ngac.support.AppLookAndFeel;
import com.example.ngac.support.Algorithm1_2_ComplementedGraphBuilder;
import com.example.ngac.support.ConflictPattern;
import com.example.ngac.support.ConflictPatternDiagramPanel;
import com.example.ngac.support.FindingPattern;
import com.example.ngac.support.GComGraphPanel;
import com.example.ngac.support.GComLayout;
import com.example.ngac.support.JsonPolicyLoader;
import com.example.ngac.support.PatternDiagramPanel;
import com.example.ngac.support.RedundancyPattern;
import gov.nist.ngac.pm.core.common.graph.node.Node;
import gov.nist.ngac.pm.core.pap.PAP;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.border.TitledBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * NADA (NGAC Anomaly Detection & Analysis): a small, self-contained desktop tool for computing
 * and visualizing the NGAC "Complemented Graph" G_Com = (G = ASSIGNMENT u ASSOCIATION) u FP
 * (Flattened Prohibitions), and for running the anomaly-detection algorithms (redundancy and
 * conflict) over it. Formerly known during development as "G_Com Studio" - same tool, new name.
 *
 * The window is laid out like POMA: a menu bar and toolbar on top, a tree of case studies on the
 * left, the raw JSON of whichever file is selected at the top right, and the current
 * visualization (G alone, FP alone, or G_Com, depending on the pipeline stage) at the
 * bottom right.
 *
 * Terminology (see the formal Definition in README.md/the paper): a Graph is
 * PE + ASSIGNMENT &cup; ASSOCIATION (PE = the policy elements/nodes). A Policy - called a
 * Configuration C = &lt;PE, R&gt; in the paper, with R = ASSIGNMENT, ASSOCIATION, PROHIBITION -
 * is that Graph plus a set of prohibitions. A "case study" is a subfolder of case_studies/
 * holding its whole Policy in a single policy.json file (nodes, assignments, associations and
 * prohibitions together - there is deliberately no separate graph.json/prohibitions.json split).
 * Clicking a case study in the left tree loads its whole Policy in one step - no separate
 * "import" action is needed. "File > Import..." is only for loading a policy.json file from
 * outside case_studies/.
 *
 * Once a Policy is loaded, the toolbar walks through the pipeline in a fixed order, left to
 * right: 1) Compute &amp; Visualize FP (shown alone, deliberately not merged with G - see
 * computeFp()), 2) Compute &amp; Visualize G_Com, 3) Detect Anomalies. Each step is disabled
 * until the previous one has run, so the order is enforced by the UI, not just documented - in
 * particular, G_Com is always computed and drawn (step 2) before Detect Anomalies (step 3) can
 * run.
 *
 * See README.md (Help > User Guide from inside the tool) for the JSON schemas and a full
 * walk-through.
 *
 * Run with:
 *   mvn -q compile exec:java "-Dexec.mainClass=com.example.ngac.NADA"
 */
public final class NADA {

    private static final String APP_NAME = "NADA";
    private static final String APP_VERSION = "1.0";
    private static final String CASE_STUDIES_DIR = "case_studies";

    // Brand palette - a single accent color used consistently across the header, toolbar and tree.
    private static final Color BRAND_DARK = new Color(0x1f, 0x2d, 0x3d);
    private static final Color BRAND_ACCENT = new Color(0x2f, 0x80, 0xa6);
    private static final Color SURFACE = new Color(0xf5, 0xf6, 0xf8);
    private static final Color HAIRLINE = new Color(0xd6, 0xda, 0xdf);
    private static final Color STATUS_INFO = new Color(0x42, 0x82, 0xc4);
    private static final Color STATUS_OK = new Color(0x2e, 0x7d, 0x32);
    private static final Color STATUS_WARN = new Color(0xb4, 0x5c, 0x00);
    private static final Color STATUS_ERROR = new Color(0xc6, 0x28, 0x28);

    private enum StatusKind { INFO, SUCCESS, WARN, ERROR }

    /** What's currently drawn in the Visualization panel - drives its title, export filenames
     * and the Export tooltips. */
    private enum Stage { NONE, G, FP, GCOM }

    private PAP pap;
    private Map<String, Long> ids;
    private List<Algorithm1_2_ComplementedGraphBuilder.Edge> g;
    private List<Algorithm1_2_ComplementedGraphBuilder.Edge> fp;
    private List<Algorithm1_2_ComplementedGraphBuilder.Edge> gCom;
    private Stage currentStage = Stage.NONE;
    private String currentStageLabel = "";

    // Raw text of the single policy.json file currently loaded, shown as-is in the "Policy
    // JSON" panel (see updatePolicyJsonView()).
    private String currentPolicyJson;

    // Disk path of the policy.json currently loaded (or null if none). Kept so that
    // "Refresh Case Study Tree" can re-read it from disk: that action only used to rebuild the
    // list of case-study folders, so editing a policy.json externally and clicking Refresh (or
    // even re-clicking the already-selected case study, which Swing's JTree does not treat as a
    // new selection) left the "Policy JSON" panel and the loaded graph stuck on the old content.
    private Path currentPolicyPath;

    private JFrame frame;
    private GComGraphPanel graphPanel;
    private JScrollPane graphScroll;
    private JLabel statusIcon;
    private JLabel statusLabel;
    private JTree caseStudyTree;
    private DefaultMutableTreeNode treeRoot;
    private JTextArea jsonView;

    private JMenuItem computeFpItem;
    private JMenuItem computeGComItem;
    private JMenuItem detectItem;
    private JMenuItem exportPngItem;
    private JMenuItem exportJsonItem;

    private JButton computeFpButton;
    private JButton computeGComButton;
    private JButton detectButton;
    private JButton exportPngButton;
    private JButton exportJsonButton;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            AppLookAndFeel.apply();
            new NADA().start();
        });
    }

    private void start() {
        graphPanel = new GComGraphPanel(null, List.of(), emptyLayout());

        statusIcon = new JLabel("●");
        statusLabel = new JLabel();

        frame = new JFrame(APP_NAME + " " + APP_VERSION + " — NGAC Complemented Graph Analyzer");
        frame.setIconImage(buildAppIcon());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.setJMenuBar(buildMenuBar());

        JPanel top = new JPanel(new BorderLayout());
        top.add(buildHeaderBanner(), BorderLayout.NORTH);
        top.add(buildToolBar(), BorderLayout.SOUTH);

        frame.add(top, BorderLayout.NORTH);
        frame.add(buildMainSplit(), BorderLayout.CENTER);
        frame.add(buildStatusBar(), BorderLayout.SOUTH);

        frame.setMinimumSize(new Dimension(980, 640));
        frame.setSize(1300, 850);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        setStatus("No policy loaded yet. Pick a case study on the left (its whole policy.json "
                + "loads in one step) or use File > Import for an external file.", StatusKind.INFO);
        refreshCaseStudyTree();
    }

    private GComLayout emptyLayout() {
        try {
            return GComLayout.compute(null, List.of());
        } catch (Exception e) {
            throw new IllegalStateException(e); // unreachable: compute() never touches pap on an empty list
        }
    }

    // -----------------------------------------------------------------
    // Branding: window icon, header banner
    // -----------------------------------------------------------------

    /** A small graph glyph (three linked nodes) drawn in code, so the tool ships with no external image files. */
    private Image buildAppIcon() {
        int size = 64;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setColor(BRAND_DARK);
        g2.fillRoundRect(0, 0, size, size, 16, 16);

        Point top = new Point(size / 2, 15);
        Point left = new Point(15, size - 15);
        Point right = new Point(size - 15, size - 15);

        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(3f));
        g2.drawLine(top.x, top.y, left.x, left.y);
        g2.drawLine(top.x, top.y, right.x, right.y);
        g2.drawLine(left.x, left.y, right.x, right.y);

        int r = 9;
        for (Point p : new Point[]{top, left, right}) {
            g2.setColor(BRAND_ACCENT);
            g2.fillOval(p.x - r, p.y - r, 2 * r, 2 * r);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(p.x - r, p.y - r, 2 * r, 2 * r);
        }
        g2.dispose();
        return img;
    }

    private JPanel buildHeaderBanner() {
        JPanel banner = new JPanel(new BorderLayout());
        banner.setBackground(BRAND_DARK);
        banner.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));

        JLabel title = new JLabel(APP_NAME);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        title.setForeground(Color.WHITE);

        JLabel subtitle = new JLabel("NGAC Complemented Graph Analyzer — load a Policy, then Compute FP → Compute G_Com → Detect Anomalies");
        subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 12f));
        subtitle.setForeground(new Color(0xc7, 0xd6, 0xe0));

        JPanel textStack = new JPanel();
        textStack.setOpaque(false);
        textStack.setLayout(new BoxLayout(textStack, BoxLayout.Y_AXIS));
        textStack.add(title);
        textStack.add(Box.createVerticalStrut(3));
        textStack.add(subtitle);

        JLabel version = new JLabel("v" + APP_VERSION);
        version.setForeground(new Color(0x9c, 0xc7, 0xdb));
        version.setFont(version.getFont().deriveFont(Font.PLAIN, 12f));

        banner.add(textStack, BorderLayout.WEST);
        banner.add(version, BorderLayout.EAST);
        return banner;
    }

    private JToolBar buildToolBar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.setBackground(SURFACE);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, HAIRLINE),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));

        // No "load" buttons here: picking a case study on the left already loads its whole
        // Policy (graph + prohibitions) in one step. The toolbar starts at the first
        // computation and enforces the order visualize-then-detect: FP, then G_Com, then
        // anomaly detection - each step stays disabled until the previous one has completed.

        computeFpButton = toolbarButton("1. Compute & Visualize FP",
                "Step 1 of 3: compute the Flattened Prohibitions (FP) edges from the loaded "
                        + "policy's prohibitions, and draw them. Load a policy on the left first.");
        computeFpButton.addActionListener(e -> computeFp());
        computeFpButton.setEnabled(false);

        computeGComButton = toolbarButton("2. Compute & Visualize G_Com",
                "Step 2 of 3: compute and draw the Complemented Graph G_Com = G ∪ FP. "
                        + "Do this before Detect Anomalies (step 3).");
        computeGComButton.addActionListener(e -> computeGCom());
        computeGComButton.setEnabled(false);

        detectButton = toolbarButton("3. Detect Anomalies",
                "Step 3 of 3: run anomaly detection over G_Com. Run this after visualizing "
                        + "G_Com (step 2), not before.");
        detectButton.addActionListener(e -> detectAnomalies());
        detectButton.setEnabled(false);

        exportPngButton = toolbarButton("Export PNG",
                "Save the visualization currently on screen as a PNG image.");
        exportPngButton.addActionListener(e -> exportPng());
        exportPngButton.setEnabled(false);

        exportJsonButton = toolbarButton("Export JSON",
                "Save the full G_Com (assignment + association + FP edges) as a JSON file - "
                        + "always G_Com, regardless of what's currently drawn above.");
        exportJsonButton.addActionListener(e -> exportJson());
        exportJsonButton.setEnabled(false);

        bar.add(computeFpButton);
        bar.add(computeGComButton);
        bar.addSeparator(new Dimension(18, 0));
        bar.add(detectButton);
        bar.addSeparator(new Dimension(18, 0));
        bar.add(exportPngButton);
        bar.add(exportJsonButton);

        // Zoom controls for the graph view (also: Ctrl + mouse wheel, drag to move, View menu).
        bar.addSeparator(new Dimension(18, 0));
        JButton zoomOutButton = toolbarButton("−", "Zoom out (Ctrl + minus, or Ctrl + mouse wheel)");
        zoomOutButton.addActionListener(e -> graphPanel.zoomOut());
        JButton zoomInButton = toolbarButton("+", "Zoom in (Ctrl + plus, or Ctrl + mouse wheel)");
        zoomInButton.addActionListener(e -> graphPanel.zoomIn());
        JButton zoomResetButton = toolbarButton("100%", "Actual size (Ctrl + 0)");
        zoomResetButton.addActionListener(e -> graphPanel.resetZoom());
        JButton zoomFitButton = toolbarButton("Fit", "Fit the whole graph in the window (Ctrl + 9)");
        zoomFitButton.addActionListener(e -> graphPanel.fitToWindow());
        JLabel zoomLabel = new JLabel(" 100% ");
        zoomLabel.setToolTipText("Current zoom. Ctrl + mouse wheel to zoom, drag the graph to move it.");
        graphPanel.addPropertyChangeListener("zoom",
                evt -> zoomLabel.setText(" " + Math.round(graphPanel.getZoom() * 100) + "% "));
        bar.add(zoomOutButton);
        bar.add(zoomLabel);
        bar.add(zoomInButton);
        bar.add(zoomResetButton);
        bar.add(zoomFitButton);
        return bar;
    }

    private JButton toolbarButton(String text, String tooltip) {
        JButton button = new JButton(text);
        button.setToolTipText(tooltip);
        button.setFocusPainted(false);
        button.setMargin(new Insets(6, 10, 6, 10));
        return button;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(SURFACE);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, HAIRLINE),
                BorderFactory.createEmptyBorder(5, 12, 5, 12)));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.setOpaque(false);
        left.add(statusIcon);
        left.add(statusLabel);
        bar.add(left, BorderLayout.WEST);
        return bar;
    }

    private void setStatus(String text, StatusKind kind) {
        statusLabel.setText(text);
        Color color = switch (kind) {
            case INFO -> STATUS_INFO;
            case SUCCESS -> STATUS_OK;
            case WARN -> STATUS_WARN;
            case ERROR -> STATUS_ERROR;
        };
        statusIcon.setForeground(color);
    }

    // -----------------------------------------------------------------
    // Visualization stage: what's currently drawn, shown in the panel title and used to make
    // the Export buttons say exactly what they will save (for a first-time / novice reader).
    // -----------------------------------------------------------------

    /**
     * Sets what's currently drawn. {@code label} is the full human-readable description shown
     * in the panel title and the Export PNG tooltip/dialogs (e.g. "G_Com = G &cup; FP, the full
     * Complemented Graph (29 edges)") - built by the caller, since each stage needs a different
     * edge-count breakdown (see the three call sites: loadPolicyFile, computeFp, computeGCom).
     */
    private void setVisualizationStage(Stage stage, String label) {
        currentStage = stage;
        currentStageLabel = label;
        String title = stage == Stage.NONE
                ? "Visualization - nothing computed yet, load a policy on the left"
                : "Visualization - currently showing: " + label;
        graphScroll.setBorder(titledBorder(title));
        updateExportTooltips();
    }

    private void updateExportTooltips() {
        String pngTip = currentStage == Stage.NONE
                ? "Save the visualization currently on screen as a PNG image."
                : "Save exactly what's drawn above right now (" + currentStageLabel + ") as a PNG image.";
        exportPngButton.setToolTipText(pngTip);
        exportPngItem.setToolTipText(pngTip);
    }

    // -----------------------------------------------------------------
    // Layout: case study tree (left) | JSON view (top right) / graph (bottom right)
    // -----------------------------------------------------------------

    private JSplitPane buildMainSplit() {
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBorder(titledBorder("Case Studies"));
        treeRoot = new DefaultMutableTreeNode(new TreeItem("Case Studies", null, Kind.ROOT));
        caseStudyTree = new JTree(new DefaultTreeModel(treeRoot));
        caseStudyTree.setRootVisible(true);
        caseStudyTree.setRowHeight(22);
        caseStudyTree.setCellRenderer(new CaseStudyTreeRenderer());
        caseStudyTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        ToolTipManager.sharedInstance().registerComponent(caseStudyTree);
        caseStudyTree.addTreeSelectionListener(e -> {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) caseStudyTree.getLastSelectedPathComponent();
            if (node == null || !(node.getUserObject() instanceof TreeItem item)) {
                return;
            }
            onTreeSelection(item, node);
        });
        leftPanel.add(new JScrollPane(caseStudyTree), BorderLayout.CENTER);
        leftPanel.setPreferredSize(new Dimension(240, 0));

        jsonView = readOnlyTextArea();
        JScrollPane jsonScroll = new JScrollPane(jsonView);
        jsonScroll.setBorder(titledBorder("Policy JSON (policy.json)"));
        jsonScroll.setPreferredSize(new Dimension(0, 260));

        graphScroll = new JScrollPane(graphPanel);
        setVisualizationStage(Stage.NONE, "");

        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, jsonScroll, graphScroll);
        rightSplit.setResizeWeight(0.3);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightSplit);
        mainSplit.setResizeWeight(0.0);
        return mainSplit;
    }

    private TitledBorder titledBorder(String title) {
        TitledBorder border = BorderFactory.createTitledBorder(title);
        border.setTitleFont(border.getTitleFont().deriveFont(Font.BOLD));
        border.setTitleColor(BRAND_DARK);
        return border;
    }

    private JTextArea readOnlyTextArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setTabSize(2);
        return area;
    }

    // -----------------------------------------------------------------
    // Tree node model
    // -----------------------------------------------------------------

    private enum Kind { ROOT, CASE_STUDY, POLICY_FILE }

    private static final class TreeItem {
        final String label;
        final Path path;
        final Kind kind;

        TreeItem(String label, Path path, Kind kind) {
            this.label = label;
            this.path = path;
            this.kind = kind;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Bolds case study folder names and adds tooltips, on top of the default folder/file icons. */
    private static final class CaseStudyTreeRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                                                        boolean expanded, boolean leaf, int row, boolean hasFocus) {
            Component c = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
            if (value instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof TreeItem item) {
                switch (item.kind) {
                    case CASE_STUDY -> {
                        setFont(getFont().deriveFont(Font.BOLD));
                        setToolTipText("Case study \"" + item.label + "\" - click to load its whole "
                                + "Policy (policy.json) in one step.");
                    }
                    case POLICY_FILE -> setToolTipText("The whole Policy for this case study: nodes, "
                            + "assignments, associations and prohibitions together, in one file.");
                    case ROOT -> setToolTipText("All case-study policies under case_studies/. Each one is "
                            + "a single policy.json file.");
                }
            }
            return c;
        }
    }

    private void onTreeSelection(TreeItem item, DefaultMutableTreeNode node) {
        switch (item.kind) {
            case CASE_STUDY -> {
                loadCaseStudy(item.path);
                caseStudyTree.expandPath(new TreePath(node.getPath()));
            }
            case POLICY_FILE -> loadPolicyFile(item.path);
            case ROOT -> { /* nothing to do */ }
        }
    }

    // -----------------------------------------------------------------
    // Menu bar
    // -----------------------------------------------------------------

    private JMenuBar buildMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic(KeyEvent.VK_F);

        JMenuItem importPolicyItem = new JMenuItem("Import Policy (JSON, external file)...");
        importPolicyItem.setMnemonic(KeyEvent.VK_I);
        importPolicyItem.setToolTipText("Load a whole Policy (nodes, assignments, associations and "
                + "prohibitions, one policy.json) from outside case_studies/ and draw its Graph. Case "
                + "studies on the left load automatically - use this only for external files.");
        importPolicyItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_1, InputEvent.CTRL_DOWN_MASK));
        importPolicyItem.addActionListener(e -> importPolicy());

        exportPngItem = new JMenuItem("Export Current Visualization as PNG...");
        exportPngItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK));
        exportPngItem.setEnabled(false);
        exportPngItem.addActionListener(e -> exportPng());

        exportJsonItem = new JMenuItem("Export G_Com as JSON...");
        exportJsonItem.setToolTipText("Always exports the full G_Com (assignment + association + FP edges), "
                + "regardless of what the Visualization panel currently shows above.");
        exportJsonItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        exportJsonItem.setEnabled(false);
        exportJsonItem.addActionListener(e -> exportJson());

        JMenuItem refreshItem = new JMenuItem("Refresh Case Study Tree");
        refreshItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK));
        refreshItem.addActionListener(e -> refreshCaseStudyTree());

        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK));
        exitItem.addActionListener(e -> frame.dispose());

        fileMenu.add(importPolicyItem);
        fileMenu.addSeparator();
        fileMenu.add(exportPngItem);
        fileMenu.add(exportJsonItem);
        fileMenu.addSeparator();
        fileMenu.add(refreshItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);

        JMenu computeMenu = new JMenu("Compute");
        computeMenu.setMnemonic(KeyEvent.VK_C);
        computeFpItem = new JMenuItem("1. Compute & Visualize Flattened Prohibitions (FP)");
        computeFpItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_3, InputEvent.CTRL_DOWN_MASK));
        computeFpItem.setEnabled(false);
        computeFpItem.addActionListener(e -> computeFp());
        computeGComItem = new JMenuItem("2. Compute & Visualize Complemented Graph (G_Com)");
        computeGComItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_4, InputEvent.CTRL_DOWN_MASK));
        computeGComItem.setEnabled(false);
        computeGComItem.addActionListener(e -> computeGCom());
        computeMenu.add(computeFpItem);
        computeMenu.add(computeGComItem);

        JMenu analyzeMenu = new JMenu("Analyze");
        analyzeMenu.setMnemonic(KeyEvent.VK_A);
        detectItem = new JMenuItem("3. Detect Anomalies (after G_Com is visualized)");
        detectItem.setEnabled(false);
        detectItem.addActionListener(e -> detectAnomalies());
        analyzeMenu.add(detectItem);

        JMenu viewMenu = new JMenu("View");
        viewMenu.setMnemonic(KeyEvent.VK_V);
        JMenuItem zoomInItem = new JMenuItem("Zoom In");
        zoomInItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, InputEvent.CTRL_DOWN_MASK));
        zoomInItem.addActionListener(e -> graphPanel.zoomIn());
        JMenuItem zoomOutItem = new JMenuItem("Zoom Out");
        zoomOutItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, InputEvent.CTRL_DOWN_MASK));
        zoomOutItem.addActionListener(e -> graphPanel.zoomOut());
        JMenuItem zoomResetItem = new JMenuItem("Actual Size (100%)");
        zoomResetItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK));
        zoomResetItem.addActionListener(e -> graphPanel.resetZoom());
        JMenuItem zoomFitItem = new JMenuItem("Fit to Window");
        zoomFitItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_9, InputEvent.CTRL_DOWN_MASK));
        zoomFitItem.addActionListener(e -> graphPanel.fitToWindow());
        viewMenu.add(zoomInItem);
        viewMenu.add(zoomOutItem);
        viewMenu.add(zoomResetItem);
        viewMenu.add(zoomFitItem);
        // Numeric-keypad + and - also zoom.
        javax.swing.JRootPane root = frame.getRootPane();
        root.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, InputEvent.CTRL_DOWN_MASK), "nadaZoomIn");
        root.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, InputEvent.CTRL_DOWN_MASK), "nadaZoomOut");
        root.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, InputEvent.CTRL_DOWN_MASK), "nadaZoomIn");
        root.getActionMap().put("nadaZoomIn", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                graphPanel.zoomIn();
            }
        });
        root.getActionMap().put("nadaZoomOut", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                graphPanel.zoomOut();
            }
        });

        JMenu helpMenu = new JMenu("Help");
        helpMenu.setMnemonic(KeyEvent.VK_H);
        JMenuItem readmeItem = new JMenuItem("User Guide (README)");
        readmeItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0));
        readmeItem.addActionListener(e -> showReadme());
        JMenuItem aboutItem = new JMenuItem("About " + APP_NAME);
        aboutItem.addActionListener(e -> showAbout());
        helpMenu.add(readmeItem);
        helpMenu.add(aboutItem);

        menuBar.add(fileMenu);
        menuBar.add(computeMenu);
        menuBar.add(analyzeMenu);
        menuBar.add(viewMenu);
        menuBar.add(helpMenu);
        return menuBar;
    }

    private void showAbout() {
        String message = "<html><body style='width: 320px; font-family: sans-serif;'>"
                + "<h2 style='margin-bottom:0;'>" + APP_NAME + "</h2>"
                + "<p style='margin-top:2px; color:#555;'>Version " + APP_VERSION + "</p>"
                + "<p>A standalone tool for computing and visualizing the NGAC "
                + "<i>Complemented Graph</i>:</p>"
                + "<p style='font-family:monospace;'>G_Com = &lt;PE, ASSIGNMENT &cup; ASSOCIATION &cup; FP&gt;</p>"
                + "<p style='color:#555;'>Graph = PE + ASSIGNMENT &cup; ASSOCIATION. A Policy "
                + "(a Configuration C = &lang;PE, R&rang; in the paper, R = ASSIGNMENT, "
                + "ASSOCIATION, PROHIBITION) is that Graph plus its Prohibitions.</p>"
                + "<p><b>Workflow</b>:</p>"
                + "<p>0. Load a Policy &mdash; pick a case study on the left (loads automatically), "
                + "or File &gt; Import for an external file.<br>"
                + "1. Compute &amp; Visualize FP &mdash; shown alone first, so its denial edges can be "
                + "read on their own before step 2 folds them into the full graph.<br>"
                + "2. Compute &amp; Visualize G_Com &mdash; always done <i>before</i> anomaly detection.<br>"
                + "3. Detect Anomalies.</p>"
                + "<p>Built on NIST's <tt>policy-machine-core</tt> reference implementation. "
                + "See Help &gt; User Guide for the JSON schemas, case study format, and the "
                + "formal G_Com definition.</p>"
                + "</body></html>";
        JOptionPane.showMessageDialog(frame, message, "About " + APP_NAME, JOptionPane.INFORMATION_MESSAGE);
    }

    private void showReadme() {
        Path readmePath = Path.of("README.md");
        String content;
        if (Files.exists(readmePath)) {
            try {
                content = Files.readString(readmePath);
            } catch (IOException ex) {
                content = "Could not read README.md: " + ex.getMessage();
            }
        } else {
            content = "README.md was not found next to this project's pom.xml.\n"
                    + "(Expected at: " + readmePath.toAbsolutePath() + ")";
        }

        JTextArea area = new JTextArea(content);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setCaretPosition(0);
        area.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(760, 560));
        JOptionPane.showMessageDialog(frame, scroll, APP_NAME + " — User Guide (README.md)",
                JOptionPane.PLAIN_MESSAGE);
    }

    // -----------------------------------------------------------------
    // Case studies tree (left)
    // -----------------------------------------------------------------

    private void refreshCaseStudyTree() {
        Path root = Path.of(CASE_STUDIES_DIR);
        if (!Files.isDirectory(root)) {
            try {
                Files.createDirectories(root);
            } catch (IOException e) {
                showError("Could not create " + root.toAbsolutePath() + ": " + e.getMessage());
                return;
            }
        }

        treeRoot.removeAllChildren();
        List<Path> caseStudyDirs = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path p : stream) {
                if (Files.isDirectory(p) && Files.exists(p.resolve("policy.json"))) {
                    caseStudyDirs.add(p);
                }
            }
        } catch (IOException e) {
            showError("Failed to scan " + root.toAbsolutePath() + ": " + e.getMessage());
            return;
        }
        caseStudyDirs.sort((a, b) -> a.getFileName().toString().compareTo(b.getFileName().toString()));

        for (Path dir : caseStudyDirs) {
            DefaultMutableTreeNode csNode = new DefaultMutableTreeNode(
                    new TreeItem(dir.getFileName().toString(), dir, Kind.CASE_STUDY));
            Path policyFile = dir.resolve("policy.json");
            csNode.add(new DefaultMutableTreeNode(new TreeItem("policy.json", policyFile, Kind.POLICY_FILE)));
            treeRoot.add(csNode);
        }

        ((DefaultTreeModel) caseStudyTree.getModel()).reload();
        caseStudyTree.expandRow(0);

        if (caseStudyDirs.isEmpty()) {
            setStatus("No case studies found under " + root.toAbsolutePath()
                    + " (each needs its own subfolder with a policy.json).", StatusKind.WARN);
        }

        // Rebuilding the tree above only re-scans which case-study folders exist - it does not
        // by itself re-read the policy.json that's currently displayed. If a policy.json is
        // already loaded (e.g. the currently selected case study), reload it from disk now, so
        // editing the file externally and clicking "Refresh Case Study Tree" actually shows the
        // new content instead of leaving the "Policy JSON" panel and graph on the old version.
        if (currentPolicyPath != null && Files.exists(currentPolicyPath)
                && !selectTreePathFor(currentPolicyPath)) {
            // The active policy.json isn't one of the "policy.json" leaves in the freshly
            // rebuilt tree (e.g. it was loaded via File > Import Policy, from outside
            // case_studies/) - reload it directly from disk instead.
            loadPolicyFile(currentPolicyPath);
        }
    }

    /** Re-selects the tree node for the given policy.json path, if it's in the (freshly rebuilt)
     * tree. Selecting it fires the normal tree-selection handling, which reloads that file from
     * disk - used by {@link #refreshCaseStudyTree()} so the panel stays in sync with what's
     * actually on disk instead of showing stale content after an external edit. Returns whether
     * a matching node was found and selected. */
    private boolean selectTreePathFor(Path policyPath) {
        for (int i = 0; i < treeRoot.getChildCount(); i++) {
            DefaultMutableTreeNode csNode = (DefaultMutableTreeNode) treeRoot.getChildAt(i);
            for (int j = 0; j < csNode.getChildCount(); j++) {
                DefaultMutableTreeNode fileNode = (DefaultMutableTreeNode) csNode.getChildAt(j);
                if (fileNode.getUserObject() instanceof TreeItem item && policyPath.equals(item.path)) {
                    caseStudyTree.setSelectionPath(new TreePath(fileNode.getPath()));
                    return true;
                }
            }
        }
        return false;
    }

    /** Refreshes the "Policy JSON" panel to show the raw text of the single policy.json currently loaded. */
    private void updatePolicyJsonView() {
        jsonView.setText(currentPolicyJson == null ? "(no policy loaded)" : currentPolicyJson);
        jsonView.setCaretPosition(0);
    }

    private void loadCaseStudy(Path dir) {
        loadPolicyFile(dir.resolve("policy.json"), dir.getFileName().toString());
    }

    /**
     * Loads a whole Policy from a single policy.json file (nodes, assignments, associations and
     * prohibitions together - see {@link JsonPolicyLoader}) and draws its Graph. Used both for
     * case studies (via {@link #loadCaseStudy(Path)}) and for File &gt; Import Policy.
     */
    private void loadPolicyFile(Path policyFile) {
        loadPolicyFile(policyFile, null);
    }

    private void loadPolicyFile(Path policyFile, String caseStudyName) {
        resetPipelineState();
        try {
            currentPolicyJson = Files.readString(policyFile);
            currentPolicyPath = policyFile;
            updatePolicyJsonView();

            JsonPolicyLoader.LoadedPolicy loaded = JsonPolicyLoader.load(policyFile);
            pap = loaded.pap();
            ids = loaded.ids();
            g = new Algorithm1_2_ComplementedGraphBuilder(pap).computeG();
            graphPanel.setGraph(pap, g, GComLayout.compute(pap, g));
            setVisualizationStage(Stage.G, "G = PE + ASSIGNMENT ∪ ASSOCIATION (" + g.size() + " edges)");
            computeFpItem.setEnabled(true);
            computeFpButton.setEnabled(true);

            String label = caseStudyName != null ? "Policy \"" + caseStudyName + "\"" : "Policy from " + policyFile.getFileName();
            setStatus(label + " loaded (G = " + g.size() + " edges). Next: 1. Compute & Visualize FP.",
                    StatusKind.SUCCESS);
        } catch (Exception ex) {
            String what = caseStudyName != null ? "case study \"" + caseStudyName + "\"" : String.valueOf(policyFile);
            showError("Failed to load " + what + ": " + ex.getMessage());
        }
    }

    private void resetPipelineState() {
        fp = null;
        gCom = null;
        setVisualizationStage(Stage.NONE, "");
        computeFpItem.setEnabled(false);
        computeGComItem.setEnabled(false);
        detectItem.setEnabled(false);
        exportPngItem.setEnabled(false);
        exportJsonItem.setEnabled(false);
        computeFpButton.setEnabled(false);
        computeGComButton.setEnabled(false);
        detectButton.setEnabled(false);
        exportPngButton.setEnabled(false);
        exportJsonButton.setEnabled(false);
    }

    // -----------------------------------------------------------------
    // File > Import... (manual, for files outside case_studies/)
    // -----------------------------------------------------------------

    private void importPolicy() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select the policy.json file");
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        caseStudyTree.clearSelection();
        loadPolicyFile(chooser.getSelectedFile().toPath());
    }

    // -----------------------------------------------------------------
    // Compute > ...
    // -----------------------------------------------------------------

    private void computeFp() {
        try {
            fp = new Algorithm1_2_ComplementedGraphBuilder(pap).computeFP();

            // Draw FP alone, deliberately NOT merged with G here: step 2 (Compute & Visualize
            // G_Com) already draws G union FP right after this, so showing that same combined
            // picture twice in a row would just be a redundant repeat. Showing FP alone first
            // lets the reader inspect the denial edges on their own before seeing them folded
            // into the full graph.
            graphPanel.setGraph(pap, fp, GComLayout.compute(pap, fp));
            setVisualizationStage(Stage.FP, "FP alone, the Flattened Prohibitions ("
                    + fp.size() + " denial edges)");

            computeGComItem.setEnabled(true);
            computeGComButton.setEnabled(true);
            setStatus("Step 1 done - FP computed and visualized alone: " + fp.size() + " denial edge(s). "
                    + "Next: 2. Compute & Visualize G_Com.", StatusKind.SUCCESS);
        } catch (Exception ex) {
            showError("Failed to compute FP: " + ex.getMessage());
        }
    }

    private void computeGCom() {
        try {
            Algorithm1_2_ComplementedGraphBuilder gcb = new Algorithm1_2_ComplementedGraphBuilder(pap);
            gCom = gcb.computeGCom(g);
            graphPanel.setGraph(pap, gCom, GComLayout.compute(pap, gCom));
            setVisualizationStage(Stage.GCOM, "G_Com = G ∪ FP, the full Complemented Graph ("
                    + gCom.size() + " edges)");

            detectItem.setEnabled(true);
            exportPngItem.setEnabled(true);
            exportJsonItem.setEnabled(true);
            detectButton.setEnabled(true);
            exportPngButton.setEnabled(true);
            exportJsonButton.setEnabled(true);

            setStatus("Step 2 done - G_Com computed and visualized (ASSIGNMENT+ASSOCIATION: " + g.size()
                    + ", FP: " + fp.size() + ", G_Com: " + gCom.size() + "). Next: 3. Detect Anomalies.",
                    StatusKind.SUCCESS);
        } catch (Exception ex) {
            showError("Failed to compute G_Com: " + ex.getMessage());
        }
    }

    // -----------------------------------------------------------------
    // Analyze > ...
    // -----------------------------------------------------------------

    private void detectAnomalies() {
        try {
            AnomalyDetector.Result result = new AnomalyDetector().detect(pap, g, fp, gCom);
            graphPanel.setHighlights(result.anomalousNodes(), result.anomalousEdges());

            String headline = result.messages().isEmpty() ? "Detection complete." : result.messages().get(0);
            StatusKind kind = result.anomalousEdges().isEmpty() ? StatusKind.SUCCESS : StatusKind.WARN;
            setStatus(headline, kind);

            showDetectionResults(result);
        } catch (Exception ex) {
            showError("Detection failed: " + ex.getMessage());
        }
    }

    private void showDetectionResults(AnomalyDetector.Result result) {
        JTextArea area = new JTextArea(String.join("\n", result.messages()));
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setCaretPosition(0);
        area.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        JScrollPane summaryScroll = new JScrollPane(area);
        summaryScroll.setBorder(BorderFactory.createTitledBorder("Summary"));
        summaryScroll.setPreferredSize(new Dimension(900, 170));

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        content.add(summaryScroll, BorderLayout.NORTH);

        if (!result.patterns().isEmpty()) {
            content.add(buildPatternViewer(result.patterns()), BorderLayout.CENTER);
        }

        JDialog dialog = new JDialog(frame, "Detection Results", true);
        dialog.setContentPane(content);
        JButton close = new JButton("Close");
        close.addActionListener(e -> dialog.dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(close);
        content.add(south, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(900, result.patterns().isEmpty() ? 260 : 620));
        dialog.setLocationRelativeTo(frame);
        dialog.setVisible(true);
    }

    /**
     * List of every classified finding on the left (Pattern number + a short label) - redundancies
     * (Fig. 3/4, Patterns 1-8) and conflicts (Fig. 5, Patterns 9-17) together, in the order
     * AnomalyDetector reported them - with the matching diagram on the right, drawn with this
     * policy's real node names in the same black-and-white notation as the paper's own figures,
     * switching between {@link PatternDiagramPanel} and {@link ConflictPatternDiagramPanel}
     * (via a {@link CardLayout}) as the selection changes.
     */
    private JSplitPane buildPatternViewer(java.util.List<FindingPattern> patterns) {
        DefaultListModel<FindingPattern> model = new DefaultListModel<>();
        for (FindingPattern p : patterns) {
            model.addElement(p);
        }
        JList<FindingPattern> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer((jList, value, index, isSelected, cellHasFocus) -> {
            String tag = value instanceof ConflictPattern ? "[Conflict] " : "[Redundancy] ";
            JLabel label = new JLabel(tag + value.caption() + ": " + value.describeEndpoints());
            label.setOpaque(true);
            label.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            label.setBackground(isSelected ? new Color(0xdd, 0xdd, 0xdd) : Color.WHITE);
            label.setForeground(Color.BLACK);
            return label;
        });

        PatternDiagramPanel redundancyDiagram = new PatternDiagramPanel();
        ConflictPatternDiagramPanel conflictDiagram = new ConflictPatternDiagramPanel();
        CardLayout cards = new CardLayout();
        JPanel diagramCards = new JPanel(cards);
        diagramCards.add(redundancyDiagram, "redundancy");
        diagramCards.add(conflictDiagram, "conflict");

        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && list.getSelectedValue() != null) {
                FindingPattern selected = list.getSelectedValue();
                if (selected instanceof ConflictPattern cp) {
                    conflictDiagram.setPattern(cp);
                    cards.show(diagramCards, "conflict");
                } else if (selected instanceof RedundancyPattern rp) {
                    redundancyDiagram.setPattern(rp);
                    cards.show(diagramCards, "redundancy");
                }
            }
        });
        list.setSelectedIndex(0);

        JScrollPane listScroll = new JScrollPane(list);
        listScroll.setBorder(BorderFactory.createTitledBorder("Findings (select one)"));
        listScroll.setPreferredSize(new Dimension(380, 380));

        JScrollPane diagramScroll = new JScrollPane(diagramCards);
        diagramScroll.setBorder(BorderFactory.createTitledBorder("Pattern diagram"));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, diagramScroll);
        split.setResizeWeight(0.35);
        return split;
    }

    // -----------------------------------------------------------------
    // Export...
    // -----------------------------------------------------------------

    private void exportPng() {
        // Always exports exactly what's currently drawn in the Visualization panel above (see
        // its title, and setVisualizationStage()) - this is G, G+FP or G_Com depending on which
        // pipeline step was last run.
        String defaultName = switch (currentStage) {
            case G -> "g.png";
            case FP -> "fp.png";
            case GCOM -> "gcom.png";
            case NONE -> "visualization.png";
        };
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export the current visualization as PNG (" + currentStageLabel + ")");
        chooser.setSelectedFile(new File(defaultName));
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        // Always the whole graph at 100%, whatever the zoom level on screen.
        BufferedImage image = graphPanel.toImage();
        try {
            ImageIO.write(image, "png", file);
            setStatus("Exported to " + file.getAbsolutePath() + " (" + currentStageLabel + ")", StatusKind.SUCCESS);
            JOptionPane.showMessageDialog(frame, "Saved to " + file.getAbsolutePath()
                    + "\n\nThis image shows: " + currentStageLabel + ".");
        } catch (Exception ex) {
            showError("Export failed: " + ex.getMessage());
        }
    }

    private void exportJson() {
        // Unlike Export PNG, this always exports the full G_Com (assignment + association + FP
        // edges) regardless of which step is currently on screen - it's only enabled once step 2
        // (Compute & Visualize G_Com) has run at least once. See the gcom.json schema in README.md.
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export G_Com (the full Complemented Graph, " + gCom.size() + " edges) as JSON");
        chooser.setSelectedFile(new File("gcom.json"));
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            writeGComJson(chooser.getSelectedFile().toPath());
            setStatus("G_Com (" + gCom.size() + " edges) exported to " + chooser.getSelectedFile().getAbsolutePath(),
                    StatusKind.SUCCESS);
            JOptionPane.showMessageDialog(frame, "Saved to " + chooser.getSelectedFile().getAbsolutePath()
                    + "\n\nThis file contains G_Com: " + g.size() + " assignment/association edge(s) + "
                    + fp.size() + " FP edge(s) = " + gCom.size() + " total.");
        } catch (Exception ex) {
            showError("Export failed: " + ex.getMessage());
        }
    }

    private void writeGComJson(Path outputFile) throws Exception {
        JSONObject root = new JSONObject();
        JSONArray assignmentEdges = new JSONArray();
        JSONArray associationEdges = new JSONArray();
        JSONArray fpEdges = new JSONArray();

        for (Algorithm1_2_ComplementedGraphBuilder.Edge e : g) {
            JSONObject edge = new JSONObject();
            edge.put("source", nameOf(e.source()));
            edge.put("target", nameOf(e.target()));
            if (e.kind() == Algorithm1_2_ComplementedGraphBuilder.EdgeKind.ASSOCIATION && e.rights() != null) {
                edge.put("operations", new JSONArray(e.rights()));
            }
            if (e.kind() == Algorithm1_2_ComplementedGraphBuilder.EdgeKind.ASSIGNMENT) {
                assignmentEdges.put(edge);
            } else {
                associationEdges.put(edge);
            }
        }
        for (Algorithm1_2_ComplementedGraphBuilder.Edge e : fp) {
            JSONObject edge = new JSONObject();
            edge.put("source", nameOf(e.source()));
            edge.put("target", nameOf(e.target()));
            if (e.rights() != null) {
                edge.put("operations", new JSONArray(e.rights()));
            }
            fpEdges.put(edge);
        }

        root.put("assignment_edges", assignmentEdges);
        root.put("association_edges", associationEdges);
        root.put("fp_edges", fpEdges);
        Files.writeString(outputFile, root.toString(2));
    }

    private String nameOf(long id) {
        try {
            Node n = pap.query().graph().getNodeById(id);
            return n.getName();
        } catch (Exception e) {
            return String.valueOf(id);
        }
    }

    private void showError(String message) {
        setStatus(message, StatusKind.ERROR);
        JOptionPane.showMessageDialog(frame, message, "Error", JOptionPane.ERROR_MESSAGE);
    }
}
