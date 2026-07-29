package ssg.legoflow.upnp.demo.mcc;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSlider;
import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import javax.swing.plaf.ColorUIResource;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.tree.DefaultTreeCellRenderer;

/**
 * Centralized dark theme utility for the MCC Swing application.
 *
 * <p>Provides color constants, global UIManager defaults, and factory methods
 * for styled Swing components that match the web variant's CSS dark theme.</p>
 *
 * @since 1.0.0
 */
public final class DarkTheme {

    /**
     * Body / root background color ({@code #0f172a}).
     *
     * @since 1.0.0
     */
    public static final Color BODY_BG = new Color(0x0f172a);

    /**
     * Panel background color ({@code #1e293b}).
     *
     * @since 1.0.0
     */
    public static final Color PANEL_BG = new Color(0x1e293b);

    /**
     * Border color ({@code #334155}).
     *
     * @since 1.0.0
     */
    public static final Color BORDER = new Color(0x334155);

    /**
     * Primary text color ({@code #e2e8f0}).
     *
     * @since 1.0.0
     */
    public static final Color TEXT = new Color(0xe2e8f0);

    /**
     * Secondary text color ({@code #94a3b8}).
     *
     * @since 1.0.0
     */
    public static final Color SECONDARY_TEXT = new Color(0x94a3b8);

    /**
     * Muted text color ({@code #64748b}).
     *
     * @since 1.0.0
     */
    public static final Color MUTED_TEXT = new Color(0x64748b);

    /**
     * Hover background color ({@code #334155}).
     *
     * @since 1.0.0
     */
    public static final Color HOVER_BG = new Color(0x334155);

    /**
     * Selected / active background color ({@code #1e3a5f}).
     *
     * @since 1.0.0
     */
    public static final Color SELECTED_BG = new Color(0x1e3a5f);

    /**
     * Accent blue color ({@code #60a5fa}).
     *
     * @since 1.0.0
     */
    public static final Color ACCENT = new Color(0x60a5fa);

    /**
     * Success green color ({@code #4ade80}).
     *
     * @since 1.0.0
     */
    public static final Color SUCCESS = new Color(0x4ade80);

    /**
     * Warning yellow color ({@code #fbbf24}).
     *
     * @since 1.0.0
     */
    public static final Color WARNING = new Color(0xfbbf24);

    /**
     * Error red color ({@code #ef4444}).
     *
     * @since 1.0.0
     */
    public static final Color ERROR = new Color(0xef4444);

    /**
     * Header background color ({@code #1e293b}), same as {@link #PANEL_BG}.
     *
     * @since 1.0.0
     */
    public static final Color HEADER_BG = new Color(0x1e293b);

    /** Alternating row color for tables (slightly lighter than body). */
    private static final Color ALT_ROW = new Color(0x162032);

    private DarkTheme() {
        // utility class
    }

    // ------------------------------------------------------------------ apply

    /**
     * Applies the dark theme globally by setting Swing {@link UIManager} defaults
     * for all standard component types.
     *
     * <p>This method should be called once at application startup, before any
     * Swing components are created.</p>
     *
     * @since 1.0.0
     */
    public static void apply() {
        ColorUIResource bodyBg = res(BODY_BG);
        ColorUIResource panelBg = res(PANEL_BG);
        ColorUIResource border = res(BORDER);
        ColorUIResource text = res(TEXT);
        ColorUIResource secondaryText = res(SECONDARY_TEXT);
        ColorUIResource selectedBg = res(SELECTED_BG);
        ColorUIResource accent = res(ACCENT);

        Border lineBorder = BorderFactory.createLineBorder(BORDER);

        // --- Panel ---
        UIManager.put("Panel.background", panelBg);
        UIManager.put("Panel.foreground", text);

        // --- Label ---
        UIManager.put("Label.foreground", text);
        UIManager.put("Label.background", panelBg);

        // --- Button ---
        UIManager.put("Button.background", panelBg);
        UIManager.put("Button.foreground", text);
        UIManager.put("Button.border", lineBorder);
        UIManager.put("Button.focus", border);
        UIManager.put("Button.select", selectedBg);

        // --- Table ---
        UIManager.put("Table.background", bodyBg);
        UIManager.put("Table.foreground", text);
        UIManager.put("Table.selectionBackground", selectedBg);
        UIManager.put("Table.selectionForeground", text);
        UIManager.put("Table.gridColor", border);
        UIManager.put("Table.focusCellHighlightBorder", lineBorder);
        UIManager.put("Table.scrollPaneBorder", lineBorder);
        UIManager.put("TableHeader.background", panelBg);
        UIManager.put("TableHeader.foreground", secondaryText);
        UIManager.put("TableHeader.cellBorder", lineBorder);

        // --- Tree ---
        UIManager.put("Tree.background", bodyBg);
        UIManager.put("Tree.foreground", text);
        UIManager.put("Tree.selectionBackground", selectedBg);
        UIManager.put("Tree.selectionForeground", text);
        UIManager.put("Tree.selectionBorderColor", border);
        UIManager.put("Tree.hash", border);
        UIManager.put("Tree.line", border);
        UIManager.put("Tree.textBackground", bodyBg);
        UIManager.put("Tree.textForeground", text);

        // --- List ---
        UIManager.put("List.background", bodyBg);
        UIManager.put("List.foreground", text);
        UIManager.put("List.selectionBackground", selectedBg);
        UIManager.put("List.selectionForeground", text);
        UIManager.put("List.focusCellHighlightBorder", lineBorder);

        // --- TextField / TextArea / TextPane / EditorPane ---
        for (String prefix : new String[]{"TextField", "TextArea", "TextPane", "EditorPane", "FormattedTextField", "PasswordField"}) {
            UIManager.put(prefix + ".background", bodyBg);
            UIManager.put(prefix + ".foreground", text);
            UIManager.put(prefix + ".caretForeground", text);
            UIManager.put(prefix + ".selectionColor", selectedBg);
            UIManager.put(prefix + ".selectionBackground", selectedBg);
            UIManager.put(prefix + ".selectionForeground", text);
            UIManager.put(prefix + ".inactiveForeground", secondaryText);
            UIManager.put(prefix + ".border", lineBorder);
        }

        // --- ComboBox ---
        UIManager.put("ComboBox.background", bodyBg);
        UIManager.put("ComboBox.foreground", text);
        UIManager.put("ComboBox.selectionBackground", selectedBg);
        UIManager.put("ComboBox.selectionForeground", text);
        UIManager.put("ComboBox.buttonBackground", panelBg);
        UIManager.put("ComboBox.buttonDarkShadow", border);
        UIManager.put("ComboBox.buttonHighlight", border);
        UIManager.put("ComboBox.buttonShadow", border);
        UIManager.put("ComboBox.border", lineBorder);

        // --- ScrollPane ---
        UIManager.put("ScrollPane.background", bodyBg);
        UIManager.put("ScrollPane.foreground", text);
        UIManager.put("ScrollPane.border", lineBorder);

        // --- SplitPane ---
        UIManager.put("SplitPane.background", panelBg);
        UIManager.put("SplitPane.foreground", text);
        UIManager.put("SplitPane.border", lineBorder);
        UIManager.put("SplitPane.dividerFocusColor", border);
        UIManager.put("SplitPane.darkShadow", border);
        UIManager.put("SplitPane.highlight", border);
        UIManager.put("SplitPane.shadow", border);
        UIManager.put("SplitPaneDivider.border", lineBorder);

        // --- TabbedPane ---
        UIManager.put("TabbedPane.background", panelBg);
        UIManager.put("TabbedPane.foreground", text);
        UIManager.put("TabbedPane.selected", selectedBg);
        UIManager.put("TabbedPane.selectedForeground", text);
        UIManager.put("TabbedPane.contentAreaColor", panelBg);
        UIManager.put("TabbedPane.tabAreaBackground", bodyBg);
        UIManager.put("TabbedPane.focus", accent);
        UIManager.put("TabbedPane.light", border);
        UIManager.put("TabbedPane.highlight", border);
        UIManager.put("TabbedPane.shadow", border);
        UIManager.put("TabbedPane.darkShadow", border);
        UIManager.put("TabbedPane.borderHightlightColor", border);
        UIManager.put("TabbedPane.border", lineBorder);
        UIManager.put("TabbedPane.unselectedBackground", bodyBg);

        // --- Slider ---
        UIManager.put("Slider.background", panelBg);
        UIManager.put("Slider.foreground", text);
        UIManager.put("Slider.tickColor", border);
        UIManager.put("Slider.highlight", border);
        UIManager.put("Slider.shadow", border);
        UIManager.put("Slider.focus", accent);
        UIManager.put("Slider.thumb", accent);
        UIManager.put("Slider.altTrackColor", bodyBg);
        UIManager.put("Slider.border", lineBorder);

        // --- ProgressBar ---
        UIManager.put("ProgressBar.background", bodyBg);
        UIManager.put("ProgressBar.foreground", accent);
        UIManager.put("ProgressBar.selectionBackground", text);
        UIManager.put("ProgressBar.selectionForeground", bodyBg);
        UIManager.put("ProgressBar.border", lineBorder);

        // --- PopupMenu ---
        UIManager.put("PopupMenu.background", panelBg);
        UIManager.put("PopupMenu.foreground", text);
        UIManager.put("PopupMenu.border", lineBorder);

        // --- MenuItem / CheckBoxMenuItem / RadioButtonMenuItem ---
        for (String prefix : new String[]{"MenuItem", "CheckBoxMenuItem", "RadioButtonMenuItem"}) {
            UIManager.put(prefix + ".background", panelBg);
            UIManager.put(prefix + ".foreground", text);
            UIManager.put(prefix + ".selectionBackground", selectedBg);
            UIManager.put(prefix + ".selectionForeground", text);
            UIManager.put(prefix + ".acceleratorForeground", secondaryText);
            UIManager.put(prefix + ".acceleratorSelectionForeground", text);
            UIManager.put(prefix + ".border", lineBorder);
        }

        // --- MenuBar ---
        UIManager.put("MenuBar.background", panelBg);
        UIManager.put("MenuBar.foreground", text);
        UIManager.put("MenuBar.border", lineBorder);
        UIManager.put("MenuBar.highlight", border);
        UIManager.put("MenuBar.shadow", border);

        // --- Menu ---
        UIManager.put("Menu.background", panelBg);
        UIManager.put("Menu.foreground", text);
        UIManager.put("Menu.selectionBackground", selectedBg);
        UIManager.put("Menu.selectionForeground", text);
        UIManager.put("Menu.border", lineBorder);

        // --- OptionPane ---
        UIManager.put("OptionPane.background", panelBg);
        UIManager.put("OptionPane.foreground", text);
        UIManager.put("OptionPane.messageForeground", text);
        UIManager.put("OptionPane.messageAreaBorder", lineBorder);
        UIManager.put("OptionPane.buttonAreaBorder", lineBorder);
        UIManager.put("OptionPane.errorDialog.titlePane.background", res(ERROR));
        UIManager.put("OptionPane.warningDialog.titlePane.background", res(WARNING));
        UIManager.put("OptionPane.questionDialog.titlePane.background", accent);
        UIManager.put("OptionPane.border", lineBorder);

        // --- ToolTip ---
        UIManager.put("ToolTip.background", panelBg);
        UIManager.put("ToolTip.foreground", text);
        UIManager.put("ToolTip.border", lineBorder);

        // --- ScrollBar ---
        UIManager.put("ScrollBar.background", bodyBg);
        UIManager.put("ScrollBar.foreground", text);
        UIManager.put("ScrollBar.thumb", border);
        UIManager.put("ScrollBar.thumbDarkShadow", border);
        UIManager.put("ScrollBar.thumbHighlight", border);
        UIManager.put("ScrollBar.thumbShadow", border);
        UIManager.put("ScrollBar.track", bodyBg);
        UIManager.put("ScrollBar.trackHighlight", bodyBg);
        UIManager.put("ScrollBar.border", lineBorder);
        UIManager.put("ScrollBar.width", 12);

        // --- Viewport ---
        UIManager.put("Viewport.background", bodyBg);
        UIManager.put("Viewport.foreground", text);

        // --- Separator ---
        UIManager.put("Separator.foreground", border);
        UIManager.put("Separator.background", panelBg);

        // --- Spinner ---
        UIManager.put("Spinner.background", bodyBg);
        UIManager.put("Spinner.foreground", text);
        UIManager.put("Spinner.border", lineBorder);

        // --- CheckBox / RadioButton ---
        for (String prefix : new String[]{"CheckBox", "RadioButton"}) {
            UIManager.put(prefix + ".background", panelBg);
            UIManager.put(prefix + ".foreground", text);
            UIManager.put(prefix + ".focus", accent);
            UIManager.put(prefix + ".border", lineBorder);
        }

        // --- ToolBar ---
        UIManager.put("ToolBar.background", panelBg);
        UIManager.put("ToolBar.foreground", text);
        UIManager.put("ToolBar.border", lineBorder);
        UIManager.put("ToolBar.dockingBackground", panelBg);
        UIManager.put("ToolBar.floatingBackground", panelBg);

        // --- InternalFrame ---
        UIManager.put("InternalFrame.background", panelBg);
        UIManager.put("InternalFrame.foreground", text);
        UIManager.put("InternalFrame.border", lineBorder);
        UIManager.put("InternalFrame.activeTitleBackground", panelBg);
        UIManager.put("InternalFrame.activeTitleForeground", text);
        UIManager.put("InternalFrame.inactiveTitleBackground", bodyBg);
        UIManager.put("InternalFrame.inactiveTitleForeground", secondaryText);

        // --- TitledBorder ---
        UIManager.put("TitledBorder.titleColor", secondaryText);
        UIManager.put("TitledBorder.border", lineBorder);

        // --- FileChooser ---
        UIManager.put("FileChooser.listViewBackground", bodyBg);

        // --- Desktop ---
        UIManager.put("Desktop.background", bodyBg);

        // --- control colors used by many LAFs ---
        UIManager.put("control", panelBg);
        UIManager.put("controlText", text);
        UIManager.put("controlHighlight", border);
        UIManager.put("controlLtHighlight", border);
        UIManager.put("controlShadow", border);
        UIManager.put("controlDkShadow", border);
        UIManager.put("text", text);
        UIManager.put("textText", text);
        UIManager.put("textHighlight", selectedBg);
        UIManager.put("textHighlightText", text);
        UIManager.put("textInactiveText", secondaryText);
        UIManager.put("info", panelBg);
        UIManager.put("infoText", text);
        UIManager.put("window", bodyBg);
        UIManager.put("windowText", text);
        UIManager.put("windowBorder", border);
        UIManager.put("menu", panelBg);
        UIManager.put("menuText", text);
        UIManager.put("activeCaption", panelBg);
        UIManager.put("activeCaptionText", text);
        UIManager.put("inactiveCaption", bodyBg);
        UIManager.put("inactiveCaptionText", secondaryText);
    }

    // --------------------------------------------------------- circle buttons

    /**
     * Creates a 40x40 round button styled like the web variant's {@code ctrl-btn}.
     *
     * <p>The button renders a filled circle with dark background and lighter border,
     * uses anti-aliased drawing, and changes colour on hover.</p>
     *
     * @param text    the text to render in the centre of the button
     * @param tooltip the tooltip text
     * @return a styled circular {@link JButton}
     * @since 1.0.0
     */
    public static JButton createCircleButton(String text, String tooltip) {
        return createCircleButtonInternal(text, tooltip, 40);
    }

    /**
     * Creates a 32x32 round button, a smaller variant of
     * {@link #createCircleButton(String, String)}.
     *
     * @param text    the text to render in the centre of the button
     * @param tooltip the tooltip text
     * @return a styled circular {@link JButton}
     * @since 1.0.0
     */
    public static JButton createSmallCircleButton(String text, String tooltip) {
        return createCircleButtonInternal(text, tooltip, 32);
    }

    private static JButton createCircleButtonInternal(String label, String tooltip, int size) {
        JButton button = new JButton() {
            private boolean hovered = false;

            {
                setOpaque(false);
                setContentAreaFilled(false);
                setFocusPainted(false);
                setBorderPainted(false);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                Dimension dim = new Dimension(size, size);
                setPreferredSize(dim);
                setMinimumSize(dim);
                setMaximumSize(dim);
                setToolTipText(tooltip);
                setForeground(TEXT);
                setFont(getFont().deriveFont(Font.BOLD, size * 0.4f));

                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        hovered = true;
                        repaint();
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        hovered = false;
                        repaint();
                    }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                int d = Math.min(getWidth(), getHeight());

                // filled background circle
                g2.setColor(hovered ? HOVER_BG : PANEL_BG);
                g2.fillOval(1, 1, d - 2, d - 2);

                // border circle
                g2.setColor(BORDER);
                g2.drawOval(1, 1, d - 3, d - 3);

                // centered text
                g2.setColor(getForeground());
                g2.setFont(getFont());
                var fm = g2.getFontMetrics();
                int tx = (d - fm.stringWidth(label)) / 2;
                int ty = (d - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(label, tx, ty);

                g2.dispose();
            }
        };
        button.setText(label);
        return button;
    }

    // -------------------------------------------------------------- drop zone

    /**
     * Creates a panel that acts as a drag-and-drop target zone.
     *
     * <p>The panel highlights with a blue "box-shadow-like" border when a drag
     * operation hovers over it, reverting to the standard border when the drag
     * leaves or completes.</p>
     *
     * @return a styled {@link JPanel} configured as a drop zone
     * @since 1.0.0
     */
    public static JPanel createDropZone() {
        JPanel panel = new JPanel();
        panel.setBackground(BODY_BG);
        Border normalBorder = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 2),
                BorderFactory.createEmptyBorder(16, 16, 16, 16));
        Border activeBorder = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ACCENT, 2),
                BorderFactory.createEmptyBorder(16, 16, 16, 16));
        panel.setBorder(normalBorder);

        panel.setDropTarget(new DropTarget(panel, DnDConstants.ACTION_COPY_OR_MOVE, new DropTargetListener() {
            @Override
            public void dragEnter(DropTargetDragEvent dtde) {
                panel.setBorder(activeBorder);
                panel.repaint();
                if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY);
                }
            }

            @Override
            public void dragOver(DropTargetDragEvent dtde) {
                // no-op
            }

            @Override
            public void dropActionChanged(DropTargetDragEvent dtde) {
                // no-op
            }

            @Override
            public void dragExit(DropTargetEvent dte) {
                panel.setBorder(normalBorder);
                panel.repaint();
            }

            @Override
            public void drop(DropTargetDropEvent dtde) {
                panel.setBorder(normalBorder);
                panel.repaint();
                dtde.rejectDrop();
            }
        }, true));

        return panel;
    }

    // ---------------------------------------------------------- panel border

    /**
     * Creates a {@link TitledBorder} styled for the dark theme.
     *
     * <p>The title is rendered in {@link #SECONDARY_TEXT} and the line uses
     * {@link #BORDER}.</p>
     *
     * @param title the border title
     * @return a dark-themed titled {@link Border}
     * @since 1.0.0
     */
    public static Border panelBorder(String title) {
        return BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(BORDER),
                title,
                TitledBorder.LEADING,
                TitledBorder.TOP,
                null,
                SECONDARY_TEXT);
    }

    // ----------------------------------------------------------- style table

    /**
     * Applies the dark theme to the given {@link JTable}, including alternating
     * row colours, header styling, and grid colour.
     *
     * @param table the table to style
     * @since 1.0.0
     */
    public static void styleTable(JTable table) {
        table.setBackground(BODY_BG);
        table.setForeground(TEXT);
        table.setSelectionBackground(SELECTED_BG);
        table.setSelectionForeground(TEXT);
        table.setGridColor(BORDER);
        table.setShowGrid(true);
        table.setIntercellSpacing(new Dimension(1, 1));

        // header
        if (table.getTableHeader() != null) {
            table.getTableHeader().setBackground(HEADER_BG);
            table.getTableHeader().setForeground(SECONDARY_TEXT);
            table.getTableHeader().setBorder(new LineBorder(BORDER));
        }

        // alternating rows
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, column);
                if (isSelected) {
                    c.setBackground(SELECTED_BG);
                    c.setForeground(TEXT);
                } else {
                    c.setBackground(row % 2 == 0 ? BODY_BG : ALT_ROW);
                    c.setForeground(TEXT);
                }
                return c;
            }
        });
    }

    // ------------------------------------------------------------ style tree

    /**
     * Applies the dark theme to the given {@link JTree}, including selection
     * colours and cell renderer colours.
     *
     * @param tree the tree to style
     * @since 1.0.0
     */
    public static void styleTree(JTree tree) {
        tree.setBackground(BODY_BG);
        tree.setForeground(TEXT);

        DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer();
        renderer.setBackgroundNonSelectionColor(BODY_BG);
        renderer.setBackgroundSelectionColor(SELECTED_BG);
        renderer.setTextNonSelectionColor(TEXT);
        renderer.setTextSelectionColor(TEXT);
        renderer.setBorderSelectionColor(BORDER);
        tree.setCellRenderer(renderer);
    }

    // --------------------------------------------------------- progress bar

    /**
     * Creates a dark-themed {@link JProgressBar} with blue fill.
     *
     * @return a styled progress bar
     * @since 1.0.0
     */
    public static JProgressBar createProgressBar() {
        JProgressBar bar = new JProgressBar();
        bar.setBackground(BODY_BG);
        bar.setForeground(ACCENT);
        bar.setBorder(BorderFactory.createLineBorder(BORDER));
        bar.setBorderPainted(true);
        bar.setStringPainted(false);
        return bar;
    }

    // --------------------------------------------------------- volume slider

    /**
     * Creates a dark-themed {@link JSlider} suitable for volume control.
     *
     * <p>The slider ranges from 0 to 100 with an initial value of 50.</p>
     *
     * @return a styled slider
     * @since 1.0.0
     */
    public static JSlider createVolumeSlider() {
        JSlider slider = new JSlider(0, 100, 50);
        slider.setBackground(PANEL_BG);
        slider.setForeground(ACCENT);
        slider.setBorder(BorderFactory.createLineBorder(BORDER));
        slider.setFocusable(false);
        return slider;
    }

    // -------------------------------------------------------- internal utils

    private static ColorUIResource res(Color c) {
        return new ColorUIResource(c);
    }
}
