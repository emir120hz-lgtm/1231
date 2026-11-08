import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.StandardProtocolFamily;
import java.net.URI;
import java.net.UnixDomainSocketAddress;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

/** Kuro Launcher - Vanilla / Fabric / Quilt, Modrinth mod installer, offline accounts. */
public class Kuro {
    // ---------- theme ----------
    static final Color PANEL = new Color(20, 20, 28), PANEL2 = new Color(30, 30, 42), BORDER = new Color(48, 46, 64),
            FG = new Color(236, 236, 244), MUTED = new Color(140, 140, 160), ACC = new Color(150, 90, 255),
            ACC2 = new Color(105, 70, 235), RED = new Color(255, 100, 115);
    static final Path ROOT = Paths.get(System.getProperty("user.home"), ".kuro");
    static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
    static final String OS = System.getProperty("os.name").toLowerCase().contains("win") ? "windows"
            : System.getProperty("os.name").toLowerCase().contains("mac") ? "osx" : "linux";
    static final String[] LOADERS = {"Vanilla", "Fabric", "Quilt"};

    static Color mix(Color a, Color b, float t) {
        t = Math.max(0, Math.min(1, t));
        return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * t), (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t), (int) (a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }
    static Graphics2D aa(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g2;
    }
    static Font font(int style, float size) { return new Font("Segoe UI", style, 12).deriveFont(size); }
    static JLabel lbl(String t, float size, boolean bold, Color c) {
        JLabel l = new JLabel(t); l.setFont(font(bold ? Font.BOLD : Font.PLAIN, size)); l.setForeground(c); return l;
    }
    static <T extends JComponent> T fixed(T c, int w, int h) {
        Dimension d = new Dimension(w, h);
        c.setPreferredSize(d); c.setMinimumSize(d); c.setMaximumSize(d); c.setAlignmentX(0.5f);
        return c;
    }
    static void drawCentered(Graphics2D g, String s, int w, int h) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, (w - fm.stringWidth(s)) / 2, (h + fm.getAscent() - fm.getDescent()) / 2);
    }
    static void darkScroll(JScrollPane sp) {
        sp.setOpaque(false); sp.getViewport().setOpaque(false); sp.setBorder(null);
        sp.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
        sp.getVerticalScrollBar().setUnitIncrement(18);
        sp.getVerticalScrollBar().setOpaque(false);
        sp.getVerticalScrollBar().setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            protected void configureScrollBarColors() { thumbColor = new Color(90, 90, 112); trackColor = new Color(0, 0, 0, 0); }
            protected JButton createDecreaseButton(int o) { return zero(); }
            protected JButton createIncreaseButton(int o) { return zero(); }
            JButton zero() { JButton b = new JButton(); b.setPreferredSize(new Dimension(0, 0)); return b; }
            protected void paintTrack(Graphics g, JComponent c, Rectangle r) { }
            protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
                Graphics2D g2 = aa(g); g2.setColor(thumbColor); g2.fillRoundRect(r.x + 1, r.y, r.width - 2, r.height, 6, 6);
            }
        });
    }
    static JPanel vbox() { JPanel p = new JPanel(); p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS)); p.setOpaque(false); return p; }
    static JScrollPane listScroll(JPanel list) {
        JPanel wrap = new JPanel(new BorderLayout()); wrap.setOpaque(false); wrap.add(list, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(wrap); darkScroll(sp); return sp;
    }

    // ---------- animation ----------
    static class Anim {
        float v, t; final Timer tm;
        Anim(Runnable r) {
            tm = new Timer(15, e -> {
                v += (t - v) * 0.2f;
                if (Math.abs(t - v) < 0.003f) { v = t; ((Timer) e.getSource()).stop(); }
                r.run();
            });
        }
        void to(float x) { t = x; if (!tm.isRunning()) tm.start(); }
        void set(float x) { v = t = x; }
    }

    // ---------- components ----------
    static class KButton extends JComponent {
        String text; boolean accent, down; Runnable click; final Anim hov = new Anim(this::repaint);
        KButton(String t, boolean accent, Runnable r) {
            text = t; this.accent = accent; click = r;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(130, 40));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { if (isEnabled()) hov.to(1); }
                public void mouseExited(MouseEvent e) { hov.to(0); down = false; repaint(); }
                public void mousePressed(MouseEvent e) { if (isEnabled()) { down = true; repaint(); } }
                public void mouseReleased(MouseEvent e) {
                    boolean in = down && contains(e.getPoint()); down = false; repaint();
                    if (in && isEnabled() && click != null) click.run();
                }
            });
        }
        public void setEnabled(boolean b) {
            super.setEnabled(b);
            setCursor(Cursor.getPredefinedCursor(b ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR)); repaint();
        }
        protected void paintComponent(Graphics g0) {
            Graphics2D g = aa(g0); int w = getWidth(), h = getHeight(); float sc = down ? 0.97f : 1f;
            g.translate(w * (1 - sc) / 2, h * (1 - sc) / 2); g.scale(sc, sc);
            Shape s = new RoundRectangle2D.Float(0, 0, w, h, 14, 14);
            if (!isEnabled()) { g.setColor(new Color(34, 34, 44)); g.fill(s); }
            else if (accent) {
                g.setPaint(new GradientPaint(0, 0, mix(ACC, new Color(190, 135, 255), hov.v), w, h, mix(ACC2, new Color(140, 100, 255), hov.v)));
                g.fill(s);
            } else {
                g.setColor(mix(PANEL2, new Color(48, 46, 66), hov.v)); g.fill(s);
                g.setColor(BORDER); g.setStroke(new BasicStroke(1f)); g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, 14, 14));
            }
            g.setColor(!isEnabled() ? MUTED : accent ? Color.WHITE : FG);
            g.setFont(font(Font.BOLD, 14)); drawCentered(g, text, w, h);
        }
    }

    static class KField extends JTextField {
        final String hint; final Anim foc = new Anim(this::repaint);
        KField(String hint) {
            this.hint = hint; setOpaque(false); setForeground(FG); setCaretColor(ACC);
            setSelectionColor(new Color(150, 90, 255, 110)); setFont(font(Font.PLAIN, 14));
            setBorder(new EmptyBorder(0, 14, 0, 14)); setPreferredSize(new Dimension(200, 42));
            addFocusListener(new FocusAdapter() {
                public void focusGained(FocusEvent e) { foc.to(1); }
                public void focusLost(FocusEvent e) { foc.to(0); }
            });
        }
        protected void paintComponent(Graphics g0) {
            Graphics2D g = aa(g0); Shape s = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 14, 14);
            g.setColor(PANEL2); g.fill(s);
            g.setColor(mix(BORDER, ACC, foc.v)); g.setStroke(new BasicStroke(1.4f)); g.draw(s);
            super.paintComponent(g0);
            if (getText().isEmpty()) {
                g.setColor(MUTED); g.setFont(getFont()); FontMetrics fm = g.getFontMetrics();
                g.drawString(hint, 14, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            }
        }
    }

    static class KPick extends JComponent {
        String text = "Yukleniyor..."; final List<String> items = new ArrayList<>(); java.util.function.Consumer<String> on;
        final Anim hov = new Anim(this::repaint);
        KPick() {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hov.to(1); }
                public void mouseExited(MouseEvent e) { hov.to(0); }
                public void mousePressed(MouseEvent e) { popup(); }
            });
        }
        void popup() {
            if (items.isEmpty()) return;
            JList<String> list = new JList<>(items.toArray(new String[0]));
            list.setBackground(PANEL2); list.setForeground(FG); list.setSelectionBackground(ACC2); list.setSelectionForeground(Color.WHITE);
            list.setFixedCellHeight(30); list.setFont(font(Font.PLAIN, 14));
            list.setCellRenderer(new DefaultListCellRenderer() {
                public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean f) {
                    JLabel c = (JLabel) super.getListCellRendererComponent(l, v, i, sel, f);
                    c.setBorder(new EmptyBorder(0, 12, 0, 12)); return c;
                }
            });
            list.setSelectedValue(text, true);
            JScrollPane sp = new JScrollPane(list); darkScroll(sp); sp.setOpaque(true); sp.getViewport().setOpaque(true);
            sp.getViewport().setBackground(PANEL2);
            sp.setPreferredSize(new Dimension(getWidth(), Math.min(280, items.size() * 30 + 4)));
            JPopupMenu pm = new JPopupMenu(); pm.setBorder(BorderFactory.createLineBorder(BORDER)); pm.setBackground(PANEL2); pm.add(sp);
            list.addMouseListener(new MouseAdapter() {
                public void mouseReleased(MouseEvent e) {
                    String v = list.getSelectedValue(); pm.setVisible(false);
                    if (v != null) { text = v; repaint(); if (on != null) on.accept(v); }
                }
            });
            pm.show(this, 0, getHeight() + 4);
        }
        protected void paintComponent(Graphics g0) {
            Graphics2D g = aa(g0); int w = getWidth(), h = getHeight();
            g.setColor(mix(PANEL2, new Color(42, 40, 58), hov.v)); g.fill(new RoundRectangle2D.Float(0, 0, w, h, 14, 14));
            g.setColor(BORDER); g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, 14, 14));
            g.setColor(FG); g.setFont(font(Font.PLAIN, 14)); g.drawString(text, 14, (h + g.getFontMetrics().getAscent() - g.getFontMetrics().getDescent()) / 2);
            g.setColor(MUTED); g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawPolyline(new int[]{w - 26, w - 20, w - 14}, new int[]{h / 2 - 2, h / 2 + 3, h / 2 - 2}, 3);
        }
    }

    static class Seg extends JComponent {
        final String[] opts; int sel; IntConsumer on; final Anim pos = new Anim(this::repaint);
        Seg(String... o) {
            opts = o; setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() { public void mousePressed(MouseEvent e) { set(Math.min(opts.length - 1, e.getX() * opts.length / Math.max(1, getWidth())), true); } });
        }
        void set(int i, boolean fire) { sel = i; pos.to(i); if (fire && on != null) on.accept(i); }
        void init(int i) { sel = i; pos.set(i); repaint(); }
        protected void paintComponent(Graphics g0) {
            Graphics2D g = aa(g0); int w = getWidth(), h = getHeight(); float sw = w / (float) opts.length;
            g.setColor(PANEL2); g.fill(new RoundRectangle2D.Float(0, 0, w, h, 14, 14));
            g.setPaint(new GradientPaint(0, 0, ACC, w, h, ACC2));
            g.fill(new RoundRectangle2D.Float(4 + pos.v * sw, 4, sw - 8, h - 8, 11, 11));
            g.setFont(font(Font.BOLD, 14));
            for (int i = 0; i < opts.length; i++) {
                float d = Math.min(1f, Math.abs(pos.v - i));
                g.setColor(mix(Color.WHITE, MUTED, d));
                FontMetrics fm = g.getFontMetrics();
                g.drawString(opts[i], i * sw + (sw - fm.stringWidth(opts[i])) / 2, (h + fm.getAscent() - fm.getDescent()) / 2);
            }
        }
    }

    static class KProgress extends JComponent {
        final Anim a = new Anim(this::repaint);
        KProgress() { setPreferredSize(new Dimension(100, 8)); }
        void set(float v) { a.to(v); }
        protected void paintComponent(Graphics g0) {
            Graphics2D g = aa(g0); int w = getWidth(), h = getHeight();
            g.setColor(PANEL2); g.fill(new RoundRectangle2D.Float(0, 0, w, h, h, h));
            if (a.v > 0.001f) {
                g.setPaint(new GradientPaint(0, 0, ACC2, w, 0, new Color(200, 140, 255)));
                g.fill(new RoundRectangle2D.Float(0, 0, Math.max(h, w * a.v), h, h, h));
            }
        }
    }

    static class KSlider extends JComponent {
        final int min, max; int val; IntConsumer on;
        KSlider(int min, int max, int val) {
            this.min = min; this.max = max; this.val = val; setPreferredSize(new Dimension(360, 30));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            MouseAdapter m = new MouseAdapter() {
                public void mousePressed(MouseEvent e) { fromX(e.getX()); }
                public void mouseDragged(MouseEvent e) { fromX(e.getX()); }
            };
            addMouseListener(m); addMouseMotionListener(m);
        }
        void fromX(int x) {
            double f = Math.max(0, Math.min(1, (x - 10) / (double) Math.max(1, getWidth() - 20)));
            int nv = min + (int) Math.round(f * (max - min));
            if (nv != val) { val = nv; repaint(); if (on != null) on.accept(val); }
        }
        protected void paintComponent(Graphics g0) {
            Graphics2D g = aa(g0); int w = getWidth(), h = getHeight();
            float x = 10 + (w - 20) * (val - min) / (float) (max - min);
            g.setColor(PANEL2); g.fill(new RoundRectangle2D.Float(10, h / 2f - 3, w - 20, 6, 6, 6));
            g.setPaint(new GradientPaint(0, 0, ACC2, w, 0, ACC)); g.fill(new RoundRectangle2D.Float(10, h / 2f - 3, x - 10, 6, 6, 6));
            g.setColor(Color.WHITE); g.fill(new Ellipse2D.Float(x - 9, h / 2f - 9, 18, 18));
            g.setColor(ACC); g.setStroke(new BasicStroke(2.5f)); g.draw(new Ellipse2D.Float(x - 9, h / 2f - 9, 18, 18));
        }
    }

    static class Row extends JPanel {
        final Anim hov = new Anim(this::repaint);
        Row(String title, String sub, JComponent... right) {
            super(new BorderLayout(10, 0)); setOpaque(false); setBorder(new EmptyBorder(6, 14, 6, 10));
            setPreferredSize(new Dimension(10, 58)); setMaximumSize(new Dimension(Integer.MAX_VALUE, 58)); setAlignmentX(0f);
            JPanel l = new JPanel(new GridLayout(sub == null ? 1 : 2, 1)); l.setOpaque(false);
            l.add(lbl(title, 14, true, FG)); if (sub != null) l.add(lbl(sub, 12, false, MUTED));
            add(l, BorderLayout.CENTER);
            if (right.length > 0) {
                JPanel r = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6)); r.setOpaque(false);
                for (JComponent c : right) r.add(c);
                add(r, BorderLayout.EAST);
            }
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hov.to(1); }
                public void mouseExited(MouseEvent e) { if (!contains(e.getPoint())) hov.to(0); }
            });
        }
        protected void paintComponent(Graphics g0) {
            Graphics2D g = aa(g0); g.setColor(mix(PANEL, PANEL2, hov.v));
            g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 14, 14));
        }
    }

    static class Pager extends JPanel {
        BufferedImage snap; final Anim fade = new Anim(this::repaint);
        Pager() { super(new BorderLayout()); setOpaque(false); }
        void show(JComponent c) {
            if (getWidth() > 0 && getHeight() > 0 && getComponentCount() > 0) {
                snap = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = snap.createGraphics(); paintChildren(g); g.dispose();
                fade.set(1); fade.to(0);
            }
            removeAll(); add(c, BorderLayout.CENTER); revalidate(); repaint();
        }
        public void paint(Graphics g0) {
            super.paint(g0);
            if (snap != null) {
                if (fade.v > 0.01f) {
                    Graphics2D g = (Graphics2D) g0.create();
                    g.setComposite(AlphaComposite.SrcOver.derive(Math.min(1f, Math.max(0f, fade.v))));
                    g.drawImage(snap, 0, 0, null); g.dispose();
                } else snap = null;
            }
        }
    }

    static class Bg extends JPanel {
        final long t0 = System.nanoTime();
        Bg() { super(new BorderLayout()); setOpaque(true); new Timer(33, e -> repaint()).start(); }
        void blob(Graphics2D g, double x, double y, float r, Color c) {
            g.setPaint(new RadialGradientPaint(new Point2D.Double(x, y), r, new float[]{0f, 1f},
                    new Color[]{c, new Color(c.getRed(), c.getGreen(), c.getBlue(), 0)}));
            g.fill(new Rectangle2D.Double(x - r, y - r, 2 * r, 2 * r));
        }
        protected void paintComponent(Graphics g0) {
            Graphics2D g = aa(g0); int w = getWidth(), h = getHeight(); double t = (System.nanoTime() - t0) / 1e9;
            g.setPaint(new GradientPaint(0, 0, new Color(11, 10, 17), 0, h, new Color(6, 6, 10))); g.fillRect(0, 0, w, h);
            blob(g, w * (0.22 + 0.12 * Math.sin(t * 0.4)), h * (0.18 + 0.1 * Math.cos(t * 0.3)), 460, new Color(130, 75, 255, 62));
            blob(g, w * (0.82 + 0.1 * Math.cos(t * 0.35)), h * (0.88 + 0.08 * Math.sin(t * 0.5)), 520, new Color(70, 60, 210, 48));
        }
    }

    static class TitleBar extends JComponent {
        final JFrame f; int hov = -1; Point press;
        TitleBar(JFrame f) {
            this.f = f; setPreferredSize(new Dimension(0, 40));
            MouseAdapter m = new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    press = e.getPoint(); int b = btn(e.getX());
                    if (b == 1) f.setState(Frame.ICONIFIED); else if (b == 2) System.exit(0);
                }
                public void mouseDragged(MouseEvent e) {
                    if (press != null && btn(press.x) < 0) { Point p = e.getLocationOnScreen(); f.setLocation(p.x - press.x, p.y - press.y); }
                }
                public void mouseMoved(MouseEvent e) { hov = btn(e.getX()); repaint(); }
                public void mouseExited(MouseEvent e) { hov = -1; repaint(); }
            };
            addMouseListener(m); addMouseMotionListener(m);
        }
        int btn(int x) { int w = getWidth(); return x >= w - 46 ? 2 : x >= w - 92 ? 1 : -1; }
        protected void paintComponent(Graphics g0) {
            Graphics2D g = aa(g0); int w = getWidth(), h = getHeight();
            g.setColor(MUTED); g.setFont(font(Font.BOLD, 12)); g.drawString("KURO CLIENT  •  KC", 18, 25);
            if (hov == 2) { g.setColor(new Color(255, 80, 90, 200)); g.fillRect(w - 46, 0, 46, h); }
            else if (hov == 1) { g.setColor(new Color(255, 255, 255, 25)); g.fillRect(w - 92, 0, 46, h); }
            g.setColor(FG); g.setStroke(new BasicStroke(1.4f));
            g.drawLine(w - 76, 20, w - 62, 20);
            g.drawLine(w - 30, 14, w - 16, 26); g.drawLine(w - 30, 26, w - 16, 14);
        }
    }

    static class Sidebar extends JComponent {
        final String[] items = {"Ana Sayfa", "Oyna", "Modlar", "Ayarlar"}; int sel = 0, hover = -1; String user = "";
        final Anim pos = new Anim(this::repaint); IntConsumer on; Runnable logout;
        Sidebar() {
            setPreferredSize(new Dimension(230, 0)); setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    int i = idx(e.getY());
                    if (i >= 0) select(i); else if (e.getY() > getHeight() - 84 && logout != null) logout.run();
                }
                public void mouseExited(MouseEvent e) { hover = -1; repaint(); }
            });
            addMouseMotionListener(new MouseMotionAdapter() { public void mouseMoved(MouseEvent e) { hover = idx(e.getY()); repaint(); } });
        }
        int idx(int y) { int i = (y - 110) / 52; return (y >= 110 && i < items.length && (y - 110) % 52 < 46) ? i : -1; }
        void select(int i) { sel = i; pos.to(i); if (on != null) on.accept(i); }
        protected void paintComponent(Graphics g0) {
            Graphics2D g = aa(g0); int w = getWidth(), h = getHeight();
            g.setColor(new Color(255, 255, 255, 8)); g.fillRect(0, 0, w, h);
            g.setColor(new Color(255, 255, 255, 14)); g.fillRect(w - 1, 0, 1, h);
            g.setFont(font(Font.BOLD, 34)); g.setPaint(new GradientPaint(0, 30, new Color(200, 150, 255), 120, 70, ACC)); g.drawString("KURO", 28, 66);
            g.setFont(font(Font.BOLD, 10)); g.setColor(MUTED); g.drawString("L A U N C H E R", 30, 84);
            g.setColor(new Color(150, 90, 255, 55)); g.fill(new RoundRectangle2D.Float(142, 42, 34, 20, 8, 8));
            g.setColor(new Color(205, 160, 255)); g.setFont(font(Font.BOLD, 12)); g.drawString("KC", 152, 56);
            g.setPaint(new GradientPaint(16, 0, new Color(150, 90, 255, 70), w - 16, 0, new Color(105, 70, 235, 25)));
            g.fill(new RoundRectangle2D.Float(16, 110 + pos.v * 52, w - 32, 46, 14, 14));
            g.setColor(ACC); g.fill(new RoundRectangle2D.Float(16, 120 + pos.v * 52, 4, 26, 4, 4));
            g.setFont(font(Font.BOLD, 15));
            for (int i = 0; i < items.length; i++) {
                float d = Math.min(1f, Math.abs(pos.v - i));
                Color c = mix(FG, hover == i ? new Color(200, 200, 220) : MUTED, d);
                g.setColor(c); g.drawString(items[i], 44, 110 + i * 52 + 29);
            }
            g.setColor(new Color(255, 255, 255, 14)); g.fillRect(16, h - 84, w - 32, 1);
            g.setPaint(new GradientPaint(0, 0, ACC, 40, 40, ACC2)); g.fill(new Ellipse2D.Float(20, h - 66, 40, 40));
            g.setColor(Color.WHITE); g.setFont(font(Font.BOLD, 17));
            String ini = user.isEmpty() ? "?" : user.substring(0, 1).toUpperCase();
            FontMetrics fm = g.getFontMetrics(); g.drawString(ini, 20 + (40 - fm.stringWidth(ini)) / 2, h - 66 + (40 + fm.getAscent() - fm.getDescent()) / 2);
            g.setColor(FG); g.setFont(font(Font.BOLD, 14)); g.drawString(user, 70, h - 49);
            g.setColor(MUTED); g.setFont(font(Font.PLAIN, 12)); g.drawString("Cikis yap", 70, h - 32);
        }
    }

    static class Splash extends JComponent {
        final long t0 = System.nanoTime(); final Timer tm; boolean fired;
        Splash(Runnable done) {
            tm = new Timer(16, e -> {
                repaint();
                if (!fired && (System.nanoTime() - t0) / 1e9 > 2.4) { fired = true; ((Timer) e.getSource()).stop(); done.run(); }
            });
            tm.start();
        }
        protected void paintComponent(Graphics g0) {
            Graphics2D g = aa(g0); int w = getWidth(), h = getHeight(); double t = (System.nanoTime() - t0) / 1e9;
            float in = (float) Math.min(1, t / 0.9), ease = 1 - (1 - in) * (1 - in) * (1 - in);
            int cx = w / 2, cy = h / 2 - 30;
            g.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(150, 90, 255, (int) (200 * ease)));
            g.draw(new Arc2D.Double(cx - 84, cy - 84, 168, 168, -t * 230, 110 + 60 * Math.sin(t * 3), Arc2D.OPEN));
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(200, 150, 255, (int) (120 * ease)));
            g.draw(new Arc2D.Double(cx - 70, cy - 70, 140, 140, t * 150, 80, Arc2D.OPEN));
            Graphics2D g2 = (Graphics2D) g.create(); float sc = 0.85f + 0.15f * ease;
            g2.translate(cx, cy); g2.scale(sc, sc); g2.setFont(font(Font.BOLD, 54));
            g2.setColor(new Color(240, 230, 255, (int) (255 * ease)));
            FontMetrics fm = g2.getFontMetrics(); g2.drawString("KURO", -fm.stringWidth("KURO") / 2, fm.getAscent() / 2 - 4);
            g2.dispose();
            float sub = (float) Math.max(0, Math.min(1, (t - 0.5) / 0.6));
            g.setFont(font(Font.BOLD, 11)); g.setColor(new Color(150, 150, 175, (int) (255 * sub)));
            String ls = "L A U N C H E R"; g.drawString(ls, cx - g.getFontMetrics().stringWidth(ls) / 2, cy + 36);
            int bw = 260; float pr = (float) Math.min(1, t / 2.2);
            g.setColor(PANEL2); g.fill(new RoundRectangle2D.Float(cx - bw / 2f, cy + 130, bw, 6, 6, 6));
            g.setPaint(new GradientPaint(cx - bw / 2f, 0, ACC2, cx + bw / 2f, 0, new Color(200, 140, 255)));
            g.fill(new RoundRectangle2D.Float(cx - bw / 2f, cy + 130, Math.max(6, bw * pr), 6, 6, 6));
            String st = t < 0.8 ? "Baslatiliyor" : t < 1.6 ? "Hesaplar yukleniyor" : "Hazir";
            g.setFont(font(Font.PLAIN, 12)); g.setColor(MUTED); g.drawString(st, cx - g.getFontMetrics().stringWidth(st) / 2, cy + 158);
        }
    }

    static class Tile extends JComponent {
        final String title, value;
        Tile(String t, String v) { title = t; value = v; }
        protected void paintComponent(Graphics g0) {
            Graphics2D g = aa(g0); int w = getWidth(), h = getHeight();
            g.setColor(new Color(24, 24, 34, 220)); g.fill(new RoundRectangle2D.Float(0, 0, w, h, 16, 16));
            g.setColor(new Color(255, 255, 255, 18)); g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, 16, 16));
            g.setColor(MUTED); g.setFont(font(Font.BOLD, 11)); g.drawString(title, 18, 28);
            g.setColor(FG); g.setFont(font(Font.BOLD, 22)); g.drawString(value, 18, 56);
        }
    }

    class Hero extends JPanel {
        final long t0 = System.nanoTime();
        Hero() {
            super(new BorderLayout()); setOpaque(false); setPreferredSize(new Dimension(10, 200));
            JPanel bt = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0)); bt.setOpaque(false); bt.setBorder(new EmptyBorder(0, 0, 22, 26));
            KButton go = new KButton("HIZLI BASLAT", true, () -> { side.select(1); new Thread(Kuro.this::launch).start(); });
            go.setPreferredSize(new Dimension(220, 52)); bt.add(go); add(bt, BorderLayout.SOUTH);
        }
        protected void paintComponent(Graphics g0) {
            Graphics2D g = aa(g0); int w = getWidth(), h = getHeight(); double t = (System.nanoTime() - t0) / 1e9;
            Shape s = new RoundRectangle2D.Float(0, 0, w, h, 26, 26);
            g.setPaint(new GradientPaint(0, 0, new Color(95, 55, 210), w, h, new Color(20, 14, 50))); g.fill(s);
            Shape old = g.getClip(); g.setClip(s);
            for (int i = 0; i < 26; i++) {
                double sx = (i * 977 % 1000) / 1000.0, sp = 0.03 + ((i * 463) % 100) / 100.0 * 0.05, y = (t * sp + i * 0.137) % 1.0;
                float x = (float) (w * sx + Math.sin(t * 0.6 + i) * 14), r = 2 + (i % 4);
                g.setColor(new Color(255, 255, 255, (int) (80 * Math.sin(Math.PI * y)))); g.fill(new Ellipse2D.Float(x, (float) (h * (1 - y)), r, r));
            }
            g.setFont(font(Font.BOLD, 150)); g.setColor(new Color(255, 255, 255, 18)); g.drawString("KURO", w - 450, h - 22);
            g.setClip(old);
            g.setColor(new Color(255, 255, 255, 40)); g.setStroke(new BasicStroke(1f)); g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, 26, 26));
            g.setColor(Color.WHITE); g.setFont(font(Font.BOLD, 34)); g.drawString("Hos geldin, " + (account == null ? "" : account), 34, 78);
            g.setColor(new Color(255, 255, 255, 175)); g.setFont(font(Font.PLAIN, 15));
            g.drawString("Son oynanan: " + selVer + "  -  " + LOADERS[selLoader], 34, 108);
        }
    }

    // ---------- browser components ----------
    static class Hit {
        String id, slug, title = "", author = "", desc = "", type, iconUrl;
        long downloads; final List<String> cats = new ArrayList<>();
        volatile BufferedImage icon; boolean iconTried;
    }

    static final ExecutorService ICONS = Executors.newFixedThreadPool(4, r -> { Thread t = new Thread(r, "icons"); t.setDaemon(true); return t; });

    static void loadIcon(Hit h, Runnable repaint) {
        if (h.iconUrl == null || h.iconTried) return;
        h.iconTried = true;
        ICONS.submit(() -> {
            try {
                byte[] b = HTTP.send(HttpRequest.newBuilder(URI.create(h.iconUrl)).header("User-Agent", "Kuro-Launcher/1.0").build(), HttpResponse.BodyHandlers.ofByteArray()).body();
                BufferedImage im = javax.imageio.ImageIO.read(new ByteArrayInputStream(b));
                if (im != null) {
                    BufferedImage o = new BufferedImage(104, 104, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = o.createGraphics();
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    g.drawImage(im, 0, 0, 104, 104, null); g.dispose();
                    h.icon = o;
                    SwingUtilities.invokeLater(repaint);
                }
            } catch (Exception e) { }
        });
    }

    static void drawIcon(Graphics2D g, Hit h, int x, int y, int s) {
        Shape old = g.getClip();
        g.setClip(new RoundRectangle2D.Float(x, y, s, s, 14, 14));
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        if (h.icon != null) g.drawImage(h.icon, x, y, s, s, null);
        else {
            g.setColor(PANEL2); g.fillRect(x, y, s, s);
            g.setColor(MUTED); g.setFont(font(Font.BOLD, s * 0.45f));
            String l = h.title.isEmpty() ? "?" : h.title.substring(0, 1).toUpperCase();
            FontMetrics fm = g.getFontMetrics();
            g.drawString(l, x + (s - fm.stringWidth(l)) / 2, y + (s + fm.getAscent() - fm.getDescent()) / 2);
        }
        g.setClip(old);
    }

    static String ellipsize(FontMetrics fm, String s, int max) {
        if (fm.stringWidth(s) <= max) return s;
        while (s.length() > 1 && fm.stringWidth(s + "...") > max) s = s.substring(0, s.length() - 1);
        return s + "...";
    }
    static int textW(String s, float size, boolean bold) { return new JLabel().getFontMetrics(font(bold ? Font.BOLD : Font.PLAIN, size)).stringWidth(s); }
    static String fmt(long n) {
        if (n >= 1_000_000_000L) return String.format(Locale.ROOT, "%.1fB", n / 1e9);
        if (n >= 1_000_000L) return String.format(Locale.ROOT, "%.1fM", n / 1e6);
        if (n >= 1_000L) return String.format(Locale.ROOT, "%.1fK", n / 1e3);
        return String.valueOf(n);
    }

    static class Badge extends JComponent {
        protected void paintComponent(Graphics g0) {
            Graphics2D g = aa(g0); int w = getWidth(), h = getHeight();
            g.setPaint(new GradientPaint(0, 0, ACC, w, h, ACC2)); g.fill(new RoundRectangle2D.Float(0, 0, w, h, 14, 14));
            g.setColor(Color.WHITE); g.setFont(font(Font.BOLD, 17)); drawCentered(g, "KC", w, h);
        }
    }

    static class IconBox extends JComponent {
        final Hit hit; IconBox(Hit h) { hit = h; }
        protected void paintComponent(Graphics g0) { drawIcon(aa(g0), hit, 0, 0, Math.min(getWidth(), getHeight())); }
    }

    static class Tag extends JComponent {
        final String text;
        Tag(String t) { text = t; setPreferredSize(new Dimension(textW(t, 12, false) + 18, 22)); }
        protected void paintComponent(Graphics g0) {
            Graphics2D g = aa(g0); int w = getWidth(), h = getHeight();
            g.setColor(new Color(255, 255, 255, 12)); g.fill(new RoundRectangle2D.Float(0, 0, w, h, h, h));
            g.setColor(new Color(255, 255, 255, 40)); g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, h, h));
            g.setColor(MUTED); g.setFont(font(Font.PLAIN, 12)); drawCentered(g, text, w, h);
        }
    }

    static class KTab extends JComponent {
        final String text; final Runnable click; final Anim hov = new Anim(this::repaint), on = new Anim(this::repaint);
        KTab(String t, boolean sel, Runnable r) {
            text = t; click = r; on.set(sel ? 1 : 0);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(textW(t, 14, true) + 36, 36));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hov.to(1); }
                public void mouseExited(MouseEvent e) { hov.to(0); }
                public void mouseReleased(MouseEvent e) { if (contains(e.getPoint()) && click != null) click.run(); }
            });
        }
        void select(boolean s) { on.to(s ? 1 : 0); }
        protected void paintComponent(Graphics g0) {
            Graphics2D g = aa(g0); int w = getWidth(), h = getHeight();
            g.setColor(new Color(255, 255, 255, (int) (8 + 20 * on.v + 10 * hov.v))); g.fill(new RoundRectangle2D.Float(0, 0, w, h, 12, 12));
            if (on.v > 0.02f) { g.setColor(new Color(150, 90, 255, (int) (170 * on.v))); g.setStroke(new BasicStroke(1.3f)); g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, 12, 12)); }
            g.setColor(mix(MUTED, FG, Math.max(on.v, hov.v * 0.6f))); g.setFont(font(Font.BOLD, 14)); drawCentered(g, text, w, h);
        }
    }

    static class Card extends JComponent {
        final Hit hit; final boolean installed; boolean selected; Runnable click; final Anim hov = new Anim(this::repaint);
        Card(Hit h, boolean inst) {
            hit = h; installed = inst;
            setPreferredSize(new Dimension(10, 92)); setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hov.to(1); }
                public void mouseExited(MouseEvent e) { hov.to(0); }
                public void mouseReleased(MouseEvent e) { if (contains(e.getPoint()) && click != null) click.run(); }
            });
        }
        protected void paintComponent(Graphics g0) {
            Graphics2D g = aa(g0); int w = getWidth(), h = getHeight();
            g.setColor(mix(new Color(22, 22, 30), new Color(36, 36, 50), hov.v)); g.fill(new RoundRectangle2D.Float(0, 0, w, h, 16, 16));
            g.setColor(selected ? ACC : new Color(255, 255, 255, 18)); g.setStroke(new BasicStroke(selected ? 1.6f : 1f));
            g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, 16, 16));
            int is = 52, ix = 14, iy = (h - is) / 2, tx = ix + is + 14;
            drawIcon(g, hit, ix, iy, is);
            g.setFont(font(Font.BOLD, 15)); FontMetrics fm = g.getFontMetrics();
            String t = ellipsize(fm, hit.title, w - tx - 14 - (installed ? 74 : 0));
            g.setColor(FG); g.drawString(t, tx, iy + 14);
            if (installed) {
                int bx = tx + fm.stringWidth(t) + 8;
                g.setColor(new Color(74, 222, 128, 36)); g.fill(new RoundRectangle2D.Float(bx, iy + 1, 62, 18, 9, 9));
                g.setColor(new Color(74, 222, 128, 150)); g.setStroke(new BasicStroke(1f)); g.draw(new RoundRectangle2D.Float(bx + 0.5f, iy + 1.5f, 61, 17, 9, 9));
                g.setColor(new Color(110, 235, 160)); g.setFont(font(Font.BOLD, 11)); g.drawString("Kurulu", bx + 12, iy + 14);
            }
            g.setFont(font(Font.PLAIN, 13)); g.setColor(MUTED);
            g.drawString(ellipsize(g.getFontMetrics(), hit.author, w - tx - 14), tx, iy + 33);
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(tx + 5, iy + 41, tx + 5, iy + 50); g.drawLine(tx + 2, iy + 47, tx + 5, iy + 50); g.drawLine(tx + 8, iy + 47, tx + 5, iy + 50); g.drawLine(tx + 1, iy + 53, tx + 9, iy + 53);
            g.drawString(fmt(hit.downloads), tx + 16, iy + 52);
        }
    }

    /** Discord Rich Presence over the local IPC pipe/socket (no libraries). */
    static class Discord {
        static final ExecutorService EX = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "discord-rpc"); t.setDaemon(true); return t; });
        static volatile String appId = "";
        static InputStream in; static OutputStream out; static Closeable conn; static boolean ready;
        static final long START = System.currentTimeMillis() / 1000;

        static void configure(String id) { appId = id == null ? "" : id.trim(); EX.submit(Discord::close); idle(); }
        static void idle() { set("Kuro Client", "KC  |  Launcher'da", START); }
        static void playing(String ver, String loader) { set("Kuro Client oynuyor", "KC  |  " + ver + " - " + loader, System.currentTimeMillis() / 1000); }

        static void set(String details, String state, long start) {
            EX.submit(() -> {
                if (appId.isEmpty()) return;
                try {
                    if (!ready && !connect()) return;
                    Map<String, Object> act = new LinkedHashMap<>(); act.put("details", details); act.put("state", state);
                    Map<String, Object> ts = new LinkedHashMap<>(); ts.put("start", start); act.put("timestamps", ts);
                    Map<String, Object> as = new LinkedHashMap<>(); as.put("large_image", "kc"); as.put("large_text", "Kuro Client (KC)"); act.put("assets", as);
                    Map<String, Object> args = new LinkedHashMap<>(); args.put("pid", ProcessHandle.current().pid()); args.put("activity", act);
                    Map<String, Object> pl = new LinkedHashMap<>(); pl.put("cmd", "SET_ACTIVITY"); pl.put("args", args); pl.put("nonce", UUID.randomUUID().toString());
                    write(1, Json.str(pl)); read();
                } catch (Exception e) { close(); }
            });
        }

        static boolean connect() {
            for (int i = 0; i < 10; i++) {
                try {
                    if (OS.equals("windows")) {
                        RandomAccessFile f = new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw");
                        conn = f; in = new FileInputStream(f.getFD()); out = new FileOutputStream(f.getFD());
                    } else {
                        String dir = System.getenv("XDG_RUNTIME_DIR");
                        if (dir == null) dir = System.getenv("TMPDIR");
                        if (dir == null) dir = "/tmp";
                        SocketChannel ch = SocketChannel.open(StandardProtocolFamily.UNIX);
                        ch.connect(UnixDomainSocketAddress.of(Paths.get(dir, "discord-ipc-" + i)));
                        conn = ch; in = Channels.newInputStream(ch); out = Channels.newOutputStream(ch);
                    }
                    write(0, "{\"v\":1,\"client_id\":\"" + appId + "\"}");
                    read();
                    ready = true;
                    return true;
                } catch (Exception e) { close(); }
            }
            return false;
        }

        static void write(int op, String json) throws IOException {
            byte[] b = json.getBytes(StandardCharsets.UTF_8);
            ByteBuffer bb = ByteBuffer.allocate(8 + b.length).order(ByteOrder.LITTLE_ENDIAN);
            bb.putInt(op).putInt(b.length).put(b);
            out.write(bb.array()); out.flush();
        }
        static String read() throws IOException {
            byte[] h = in.readNBytes(8);
            if (h.length < 8) throw new EOFException();
            ByteBuffer bb = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN);
            bb.getInt(); int len = bb.getInt();
            return new String(in.readNBytes(len), StandardCharsets.UTF_8);
        }
        static void close() {
            ready = false;
            try { if (conn != null) conn.close(); } catch (Exception e) { }
            conn = null; in = null; out = null;
        }
    }

    // ---------- state ----------
    static Map<String, Object> cfg = new LinkedHashMap<>();
    JFrame frame; Pager screen = new Pager(), pages = new Pager(); Sidebar side = new Sidebar();
    JPanel app, playPage, setPage; JLabel status = lbl(" ", 13, false, MUTED), subtitle = lbl(" ", 14, false, MUTED);
    KProgress progress = new KProgress(); KButton play; JTextArea log = new JTextArea();
    KPick verPick = new KPick(); Seg loaderSeg = new Seg(LOADERS);
    JPanel installedList = vbox(), resultList = vbox(); JLabel modInfo = lbl(" ", 13, false, MUTED);
    Map<String, String> versionUrls = new LinkedHashMap<>();
    String account, selVer; int selLoader;

    static void loadCfg() {
        try { cfg = Json.obj(Files.readString(ROOT.resolve("settings.json"))); } catch (Exception e) { cfg = new LinkedHashMap<>(); }
    }
    static void saveCfg() {
        try { Files.createDirectories(ROOT); Files.writeString(ROOT.resolve("settings.json"), Json.str(cfg)); } catch (Exception e) { }
    }
    static String cs(String k, String d) { Object o = cfg.get(k); return o == null ? d : o.toString(); }
    static int ci(String k, int d) { Object o = cfg.get(k); return o instanceof Number ? ((Number) o).intValue() : d; }
    static List<String> accounts() {
        List<String> l = new ArrayList<>(); Object o = cfg.get("accounts");
        if (o instanceof List) for (Object x : (List<?>) o) l.add(x.toString());
        return l;
    }
    static void setAccounts(List<String> l) { cfg.put("accounts", new ArrayList<Object>(l)); saveCfg(); }

    public static void main(String[] a) {
        loadCfg();
        SwingUtilities.invokeLater(() -> new Kuro().start());
    }

    Path instDir() { return ROOT.resolve("instances").resolve(selVer + "-" + LOADERS[selLoader].toLowerCase()); }

    void start() {
        frame = new JFrame("Kuro Client (KC)");
        frame.setUndecorated(true); frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1180, 720); frame.setLocationRelativeTo(null);
        try { frame.setShape(new RoundRectangle2D.Double(0, 0, 1180, 720, 26, 26)); } catch (Exception e) { }
        Bg bg = new Bg(); bg.add(new TitleBar(frame), BorderLayout.NORTH);
        screen.setBorder(new EmptyBorder(0, 0, 0, 0)); bg.add(screen, BorderLayout.CENTER);
        frame.setContentPane(bg);

        selLoader = Math.max(0, Arrays.asList(LOADERS).indexOf(cs("loader", "Fabric")));
        selVer = cs("version", "1.21.4");
        loaderSeg.init(selLoader);
        loaderSeg.on = i -> { selLoader = i; cfg.put("loader", LOADERS[i]); saveCfg(); updateInfo(); };
        verPick.text = selVer;
        verPick.on = v -> { selVer = v; cfg.put("version", v); saveCfg(); updateInfo(); };

        Discord.appId = cs("discord", ""); Discord.idle();
        account = cs("last", null);
        if (account != null) side.user = account;
        buildApp(); updateInfo();
        screen.add(new Splash(() -> screen.show(account != null ? app : buildLogin())), BorderLayout.CENTER);
        frame.setVisible(true);
        new Thread(this::loadVersions).start();
    }

    void updateInfo() {
        SwingUtilities.invokeLater(() -> {
            subtitle.setText("Hazir misin, " + (account == null ? "" : account) + "?");
            modInfo.setText(selVer + "  -  " + LOADERS[selLoader]);
        });
    }

    // ---------- login ----------
    JComponent buildLogin() {
        JPanel center = new JPanel(new GridBagLayout()); center.setOpaque(false);
        JPanel card = new JPanel() {
            protected void paintComponent(Graphics g0) {
                Graphics2D g = aa(g0); g.setColor(new Color(20, 20, 30, 215)); g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 26, 26));
                g.setColor(new Color(255, 255, 255, 22)); g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 26, 26));
            }
        };
        card.setOpaque(false); card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS)); card.setBorder(new EmptyBorder(30, 30, 30, 30));
        JLabel logo = lbl("KURO", 46, true, ACC); logo.setAlignmentX(0.5f);
        JLabel sub = lbl("Hesabini sec ya da yeni olustur", 13, false, MUTED); sub.setAlignmentX(0.5f);
        card.add(logo); card.add(sub); card.add(Box.createVerticalStrut(18));

        JPanel accs = vbox(); accs.setAlignmentX(0.5f);
        for (String n : accounts()) {
            KButton x = new KButton("x", false, null); x.setPreferredSize(new Dimension(32, 30));
            Row r = new Row(n, "Cevrimdisi hesap", x);
            x.click = () -> { List<String> l = accounts(); l.remove(n); setAccounts(l); screen.show(buildLogin()); };
            r.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            r.addMouseListener(new MouseAdapter() { public void mouseReleased(MouseEvent e) { if (r.contains(e.getPoint())) login(n); } });
            accs.add(r); accs.add(Box.createVerticalStrut(6));
        }
        card.add(accs);

        KField name = fixed(new KField("Kullanici adi (3-16, harf/rakam/_)"), 340, 44);
        JLabel err = lbl(" ", 12, false, RED); err.setAlignmentX(0.5f);
        Runnable go = () -> {
            String n = name.getText().trim();
            if (!n.matches("[A-Za-z0-9_]{3,16}")) { err.setText("Gecersiz kullanici adi (3-16 karakter, harf/rakam/_)"); return; }
            login(n);
        };
        name.addActionListener(e -> go.run());
        KButton enter = fixed(new KButton("Giris Yap", true, go), 340, 44);
        KButton ms = fixed(new KButton("Microsoft ile giris (yakinda)", false, null), 340, 44); ms.setEnabled(false);
        card.add(Box.createVerticalStrut(8)); card.add(name); card.add(Box.createVerticalStrut(4)); card.add(err);
        card.add(Box.createVerticalStrut(6)); card.add(enter); card.add(Box.createVerticalStrut(10)); card.add(ms);
        center.add(card);
        return center;
    }

    void login(String n) {
        List<String> l = accounts(); if (!l.contains(n)) l.add(n); setAccounts(l);
        account = n; cfg.put("last", n); saveCfg(); side.user = n; updateInfo();
        side.select(0);
        screen.show(app);
    }

    void logout() {
        account = null; cfg.remove("last"); saveCfg(); side.user = ""; screen.show(buildLogin());
    }

    // ---------- app ----------
    void buildApp() {
        app = new JPanel(new BorderLayout()); app.setOpaque(false);
        side.on = i -> { if (i == 0) pages.show(buildHome()); else if (i == 1) pages.show(playPage); else if (i == 2) pages.show(buildMods()); else pages.show(setPage); };
        side.logout = this::logout;
        app.add(side, BorderLayout.WEST);
        playPage = buildPlay(); setPage = buildSettings();
        pages.add(buildHome(), BorderLayout.CENTER);
        app.add(pages, BorderLayout.CENTER);
    }

    JPanel pageBase() {
        JPanel p = new JPanel(new BorderLayout(0, 16)); p.setOpaque(false); p.setBorder(new EmptyBorder(8, 34, 28, 34)); return p;
    }

    JPanel buildPlay() {
        JPanel p = pageBase();
        JPanel head = vbox();
        head.add(lbl("Oyna", 30, true, FG)); head.add(subtitle); head.add(Box.createVerticalStrut(22));
        head.add(lbl("SURUM", 11, true, MUTED)); head.add(Box.createVerticalStrut(6));
        JPanel vr = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); vr.setOpaque(false); vr.setAlignmentX(0f);
        verPick.setPreferredSize(new Dimension(260, 44)); vr.add(verPick); head.add(vr); head.add(Box.createVerticalStrut(18));
        head.add(lbl("MOD YUKLEYICI", 11, true, MUTED)); head.add(Box.createVerticalStrut(6));
        JPanel sr = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); sr.setOpaque(false); sr.setAlignmentX(0f);
        loaderSeg.setPreferredSize(new Dimension(380, 44)); sr.add(loaderSeg); head.add(sr);
        p.add(head, BorderLayout.NORTH);

        log.setEditable(false); log.setOpaque(false); log.setLineWrap(true); log.setForeground(new Color(170, 170, 190)); log.setFont(new Font("Consolas", Font.PLAIN, 12));
        JScrollPane sp = new JScrollPane(log); darkScroll(sp); sp.setBorder(new EmptyBorder(8, 12, 8, 4));
        JPanel logBox = new JPanel(new BorderLayout()) {
            protected void paintComponent(Graphics g0) {
                Graphics2D g = aa(g0); g.setColor(new Color(12, 12, 18, 200)); g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 14, 14));
            }
        };
        logBox.setOpaque(false); logBox.add(sp);
        p.add(logBox, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout(16, 0)); south.setOpaque(false);
        JPanel pr = new JPanel(new GridLayout(2, 1, 0, 8)); pr.setOpaque(false);
        pr.add(status); pr.add(progress);
        south.add(pr, BorderLayout.CENTER);
        play = new KButton("OYNA", true, () -> new Thread(this::launch).start());
        play.setPreferredSize(new Dimension(200, 54)); south.add(play, BorderLayout.EAST);
        p.add(south, BorderLayout.SOUTH);
        return p;
    }

    JPanel buildSettings() {
        JPanel p = pageBase(); JPanel b = vbox();
        b.add(lbl("Ayarlar", 30, true, FG)); b.add(Box.createVerticalStrut(24));
        int ram = ci("ram", 4);
        JLabel rl = lbl("BELLEK (RAM): " + ram + " GB", 11, true, MUTED); b.add(rl); b.add(Box.createVerticalStrut(8));
        KSlider sl = new KSlider(1, 16, ram); sl.setAlignmentX(0f); sl.setMaximumSize(new Dimension(420, 30));
        sl.on = v -> { cfg.put("ram", (long) v); saveCfg(); rl.setText("BELLEK (RAM): " + v + " GB"); };
        b.add(sl); b.add(Box.createVerticalStrut(22));
        b.add(lbl("EK JVM ARGUMANLARI", 11, true, MUTED)); b.add(Box.createVerticalStrut(8));
        KField jvm = new KField("ornek: -XX:+UseG1GC"); jvm.setText(cs("jvm", "")); jvm.setAlignmentX(0f); jvm.setMaximumSize(new Dimension(520, 44));
        jvm.addKeyListener(new KeyAdapter() { public void keyReleased(KeyEvent e) { cfg.put("jvm", jvm.getText()); saveCfg(); } });
        b.add(jvm); b.add(Box.createVerticalStrut(22));
        b.add(lbl("DISCORD UYGULAMA ID (Rich Presence)", 11, true, MUTED)); b.add(Box.createVerticalStrut(8));
        KField dc = new KField("Developer Portal'daki Application ID"); dc.setText(cs("discord", "")); dc.setAlignmentX(0f); dc.setMaximumSize(new Dimension(520, 44));
        dc.addKeyListener(new KeyAdapter() { public void keyReleased(KeyEvent e) { cfg.put("discord", dc.getText().trim()); saveCfg(); } });
        dc.addFocusListener(new FocusAdapter() { public void focusLost(FocusEvent e) { Discord.configure(cs("discord", "")); } });
        b.add(dc); b.add(Box.createVerticalStrut(26));
        JPanel br = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); br.setOpaque(false); br.setAlignmentX(0f);
        KButton o1 = new KButton("Oyun klasorunu ac", false, () -> open(ROOT.resolve("instances"))); o1.setPreferredSize(new Dimension(190, 42));
        br.add(o1); b.add(br);
        p.add(b, BorderLayout.NORTH);
        return p;
    }

    static void open(Path d) {
        try { Files.createDirectories(d); Desktop.getDesktop().open(d.toFile()); } catch (Exception e) { }
    }

    // ---------- home ----------
    JPanel buildHome() {
        JPanel p = new JPanel(new BorderLayout(0, 18)); p.setOpaque(false); p.setBorder(new EmptyBorder(8, 34, 24, 34));
        p.add(new Hero(), BorderLayout.NORTH);
        JPanel mid = vbox();
        JPanel tiles = new JPanel(new GridLayout(1, 4, 14, 0)); tiles.setOpaque(false); tiles.setAlignmentX(0f);
        tiles.setPreferredSize(new Dimension(10, 76)); tiles.setMaximumSize(new Dimension(Integer.MAX_VALUE, 76));
        tiles.add(new Tile("SURUM", String.valueOf(selVer))); tiles.add(new Tile("YUKLEYICI", LOADERS[selLoader]));
        tiles.add(new Tile("BELLEK", ci("ram", 4) + " GB")); tiles.add(new Tile("MODLAR", modCount()));
        mid.add(tiles); mid.add(Box.createVerticalStrut(18));
        JLabel pl = lbl("POPULER MODLAR", 11, true, MUTED); pl.setAlignmentX(0f); mid.add(pl); mid.add(Box.createVerticalStrut(8));
        JPanel pop = vbox(); pop.setAlignmentX(0f); mid.add(pop);
        p.add(mid, BorderLayout.CENTER);
        loadPopular(pop);
        return p;
    }

    String modCount() {
        if (selLoader == 0 || selVer == null) return "-";
        Path dir = instDir().resolve("mods");
        try (var st = Files.exists(dir) ? Files.list(dir) : java.util.stream.Stream.<Path>empty()) {
            return String.valueOf(st.filter(f -> f.toString().endsWith(".jar")).count());
        } catch (Exception e) { return "0"; }
    }

    void loadPopular(JPanel pop) {
        if (selLoader == 0) { pop.add(new Row("Mod yukleyici sec", "Fabric veya Quilt secince populer modlar burada gorunur", new JComponent[0])); return; }
        pop.add(new Row("Yukleniyor...", null, new JComponent[0]));
        String mc = selVer, ld = LOADERS[selLoader].toLowerCase();
        new Thread(() -> {
            try {
                String facets = "[[\"versions:" + mc + "\"],[\"categories:" + ld + "\"],[\"project_type:mod\"]]";
                Map<String, Object> r = Json.obj(get("https://api.modrinth.com/v2/search?limit=3&index=downloads&facets=" + enc(facets)));
                List<Object> hits = Json.arr(r.get("hits"));
                SwingUtilities.invokeLater(() -> {
                    pop.removeAll();
                    for (Object h : hits) {
                        Map<String, Object> m = Json.obj(h);
                        String title = String.valueOf(m.get("title")), desc = String.valueOf(m.get("description")), id = String.valueOf(m.get("project_id"));
                        KButton ins = new KButton("Yukle", true, null); ins.setPreferredSize(new Dimension(74, 34));
                        ins.click = () -> installAsync(id, title, ins);
                        pop.add(new Row(title, desc.length() > 60 ? desc.substring(0, 59) + "..." : desc, ins));
                        pop.add(Box.createVerticalStrut(6));
                    }
                    pop.revalidate(); pop.repaint();
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> { pop.removeAll(); pop.add(new Row("Modrinth'e baglanilamadi", null, new JComponent[0])); pop.revalidate(); pop.repaint(); });
            }
        }).start();
    }

    // ---------- mod / pack browser ----------
    static final String[] TYPES = {"mod", "resourcepack", "shader"}, TNAMES = {"Modlar", "Kaynak Paketleri", "Shaderlar"}, DIRS = {"mods", "resourcepacks", "shaderpacks"};
    int browserType = 0;
    final Object idxLock = new Object();

    JPanel buildMods() { return new Browser(); }

    static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
    String loaderFacet() { return selLoader == 2 ? "\"categories:quilt\",\"categories:fabric\"" : "\"categories:" + LOADERS[selLoader].toLowerCase() + "\""; }
    String loaderJson() { return selLoader == 2 ? "[\"quilt\",\"fabric\"]" : "[\"" + LOADERS[selLoader].toLowerCase() + "\"]"; }

    Map<String, Object> readIdx() {
        try { return Json.obj(Files.readString(instDir().resolve("kuro-installed.json"))); } catch (Exception e) { return new LinkedHashMap<>(); }
    }
    @SuppressWarnings("unchecked")
    void record(int t, String id, String file) {
        synchronized (idxLock) {
            try {
                Map<String, Object> root = readIdx();
                Map<String, Object> sec = (Map<String, Object>) root.computeIfAbsent(TYPES[t], k -> new LinkedHashMap<String, Object>());
                sec.put(id, file);
                Files.createDirectories(instDir());
                Files.writeString(instDir().resolve("kuro-installed.json"), Json.str(root));
            } catch (Exception e) { }
        }
    }
    @SuppressWarnings("unchecked")
    String installedFile(int t, String id) {
        Object sec = readIdx().get(TYPES[t]);
        if (!(sec instanceof Map)) return null;
        Object f = ((Map<String, Object>) sec).get(id);
        if (f == null) return null;
        Path d = instDir().resolve(DIRS[t]);
        return Files.exists(d.resolve(f.toString())) || Files.exists(d.resolve(f + ".disabled")) ? f.toString() : null;
    }
    @SuppressWarnings("unchecked")
    void removeProject(int t, String id) {
        synchronized (idxLock) {
            try {
                Map<String, Object> root = readIdx();
                Object sec = root.get(TYPES[t]);
                if (sec instanceof Map) {
                    Object f = ((Map<String, Object>) sec).remove(id);
                    if (f != null) {
                        Path d = instDir().resolve(DIRS[t]);
                        Files.deleteIfExists(d.resolve(f.toString())); Files.deleteIfExists(d.resolve(f + ".disabled"));
                    }
                }
                Files.writeString(instDir().resolve("kuro-installed.json"), Json.str(root));
            } catch (Exception e) { }
        }
    }

    void installProject(String pid, int t, int depth, Set<String> seen) throws Exception {
        if (!seen.add(pid) || depth > 3) return;
        String url = "https://api.modrinth.com/v2/project/" + pid + "/version?game_versions=" + enc("[\"" + selVer + "\"]")
                + (t == 0 ? "&loaders=" + enc(loaderJson()) : "");
        List<Object> vs = Json.arr(Json.parse(get(url)));
        if (vs.isEmpty()) throw new Exception("bu surum icin uyumlu dosya yok");
        Map<String, Object> v = Json.obj(vs.get(0));
        if (t == 0 && v.get("dependencies") != null)
            for (Object o : Json.arr(v.get("dependencies"))) {
                Map<String, Object> d = Json.obj(o);
                if ("required".equals(d.get("dependency_type")) && d.get("project_id") != null)
                    installProject(String.valueOf(d.get("project_id")), 0, depth + 1, seen);
            }
        Map<String, Object> file = null;
        for (Object o : Json.arr(v.get("files"))) { Map<String, Object> f = Json.obj(o); if (file == null || Boolean.TRUE.equals(f.get("primary"))) file = f; }
        if (file == null) throw new Exception("dosya yok");
        String fn = (String) file.get("filename");
        download((String) file.get("url"), instDir().resolve(DIRS[t]).resolve(fn));
        record(t, pid, fn);
        log("Yuklendi: " + fn);
    }

    void installAsync(String id, String title, KButton btn) {
        btn.setEnabled(false); btn.text = "...";
        new Thread(() -> {
            try {
                installProject(id, 0, 0, new HashSet<>());
                SwingUtilities.invokeLater(() -> { btn.text = "Tamam"; btn.repaint(); });
            } catch (Exception e) {
                log("Mod yuklenemedi (" + title + "): " + e);
                SwingUtilities.invokeLater(() -> { btn.text = "Hata"; btn.repaint(); });
            }
        }).start();
    }

    class Browser extends JPanel {
        boolean installedTab, hasMore, loading; String query = ""; int gen;
        final List<Hit> hits = new ArrayList<>(); Hit sel;
        final JPanel body = new JPanel(new BorderLayout(14, 0)), gridWrap = new JPanel(new BorderLayout());
        JScrollPane scroll; KField search; JPanel detail; final KTab[] typeTabs = new KTab[3]; KTab tBrowse, tInst; Timer debounce;

        Browser() {
            super(new BorderLayout(0, 14)); setOpaque(false); setBorder(new EmptyBorder(6, 28, 24, 28));
            debounce = new Timer(350, e -> { query = search.getText().trim(); reload(); }); debounce.setRepeats(false);
            body.setOpaque(false); gridWrap.setOpaque(false);
            scroll = new JScrollPane(gridWrap); darkScroll(scroll); body.add(scroll, BorderLayout.CENTER);

            JPanel head = vbox();
            JPanel r1 = new JPanel(new BorderLayout()); r1.setOpaque(false); r1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60)); r1.setAlignmentX(0f);
            JPanel inst = new JPanel(new BorderLayout(12, 0)); inst.setOpaque(false);
            Badge badge = new Badge(); badge.setPreferredSize(new Dimension(48, 48)); inst.add(badge, BorderLayout.WEST);
            JPanel lb = new JPanel(new GridLayout(2, 1)); lb.setOpaque(false);
            lb.add(lbl(String.valueOf(selVer), 18, true, FG)); lb.add(lbl(selVer + " - " + LOADERS[selLoader], 13, false, MUTED));
            inst.add(lb, BorderLayout.CENTER); r1.add(inst, BorderLayout.WEST);

            JPanel pill = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 4)) {
                protected void paintComponent(Graphics g0) {
                    Graphics2D g = aa(g0); g.setColor(new Color(20, 20, 28, 210)); g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 16, 16));
                    g.setColor(new Color(255, 255, 255, 22)); g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 16, 16));
                }
            };
            pill.setOpaque(false);
            for (int i = 0; i < 3; i++) {
                final int ti = i;
                typeTabs[i] = new KTab(TNAMES[i], i == browserType, () -> setType(ti));
                pill.add(typeTabs[i]);
            }
            JPanel cw = new JPanel(new GridBagLayout()); cw.setOpaque(false); cw.add(pill); r1.add(cw, BorderLayout.CENTER);

            JPanel ew = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10)); ew.setOpaque(false);
            KButton add = new KButton("Dosya ekle", false, this::addFile); add.setPreferredSize(new Dimension(110, 38));
            KButton of = new KButton("Klasoru ac", false, () -> open(instDir().resolve(DIRS[browserType]))); of.setPreferredSize(new Dimension(110, 38));
            ew.add(add); ew.add(of); r1.add(ew, BorderLayout.EAST);
            head.add(r1); head.add(Box.createVerticalStrut(10));

            JPanel r2 = new JPanel(new BorderLayout()); r2.setOpaque(false); r2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46)); r2.setAlignmentX(0f);
            JPanel st = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 5)); st.setOpaque(false);
            tBrowse = new KTab("Gozat", true, () -> setInstalledTab(false)); tInst = new KTab("Kurulu", false, () -> setInstalledTab(true));
            st.add(tBrowse); st.add(tInst); r2.add(st, BorderLayout.WEST);
            JPanel se = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2)); se.setOpaque(false);
            search = new KField("Ara..."); search.setPreferredSize(new Dimension(280, 42));
            search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) { debounce.restart(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { debounce.restart(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { debounce.restart(); }
            });
            KButton rf = new KButton("Yenile", false, this::reload); rf.setPreferredSize(new Dimension(84, 42));
            se.add(search); se.add(rf); r2.add(se, BorderLayout.EAST); head.add(r2);

            add(head, BorderLayout.NORTH); add(body, BorderLayout.CENTER);
            reload();
        }

        void setType(int i) {
            browserType = i;
            for (int k = 0; k < 3; k++) typeTabs[k].select(k == i);
            reload();
        }
        void setInstalledTab(boolean b) { installedTab = b; tBrowse.select(!b); tInst.select(b); reload(); }

        void setContent(JComponent c) {
            gridWrap.removeAll(); gridWrap.add(c, BorderLayout.NORTH); gridWrap.revalidate(); gridWrap.repaint();
        }
        void message(String t, String sub) {
            JPanel v = vbox(); v.add(new Row(t, sub, new JComponent[0])); setContent(v);
        }

        void reload() {
            hits.clear(); sel = null; gen++; loading = false; showDetail();
            if (installedTab) { renderInstalled(); return; }
            if (selLoader == 0 && browserType == 0) { message("Vanilla'da mod calismaz", "Oyna sayfasindan Fabric veya Quilt sec"); return; }
            message("Yukleniyor...", null);
            fetch(false);
        }

        void fetch(boolean more) {
            if (more && loading) return;
            loading = true;
            final int g0 = gen, off = more ? hits.size() : 0;
            String facets = "[[\"versions:" + selVer + "\"],[\"project_type:" + TYPES[browserType] + "\"]"
                    + (browserType == 0 ? ",[" + loaderFacet() + "]" : "") + "]";
            String url = "https://api.modrinth.com/v2/search?limit=30&offset=" + off + "&index=" + (query.isEmpty() ? "downloads" : "relevance")
                    + "&query=" + enc(query) + "&facets=" + enc(facets);
            new Thread(() -> {
                try {
                    Map<String, Object> r = Json.obj(get(url));
                    List<Hit> got = new ArrayList<>();
                    for (Object o : Json.arr(r.get("hits"))) {
                        Map<String, Object> m = Json.obj(o); Hit h = new Hit();
                        h.id = String.valueOf(m.get("project_id")); h.slug = String.valueOf(m.get("slug"));
                        h.title = String.valueOf(m.get("title")); h.author = String.valueOf(m.get("author"));
                        h.desc = String.valueOf(m.get("description")); h.type = String.valueOf(m.get("project_type"));
                        String ic = m.get("icon_url") == null ? "" : String.valueOf(m.get("icon_url"));
                        h.iconUrl = ic.isBlank() ? null : ic;
                        h.downloads = m.get("downloads") instanceof Number ? ((Number) m.get("downloads")).longValue() : 0;
                        Object cs = m.get("display_categories") != null ? m.get("display_categories") : m.get("categories");
                        if (cs instanceof List) for (Object c : (List<?>) cs) h.cats.add(String.valueOf(c));
                        got.add(h);
                    }
                    long total = r.get("total_hits") instanceof Number ? ((Number) r.get("total_hits")).longValue() : 0;
                    SwingUtilities.invokeLater(() -> {
                        if (g0 != gen) return;
                        loading = false; hits.addAll(got); hasMore = hits.size() < total; renderGrid();
                    });
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> { if (g0 == gen) { loading = false; message("Modrinth'e baglanilamadi", String.valueOf(e.getMessage())); } });
                }
            }).start();
        }

        Set<String> installedIds() {
            Set<String> ids = new HashSet<>();
            Object sec = readIdx().get(TYPES[browserType]);
            if (sec instanceof Map) for (Object k : ((Map<?, ?>) sec).keySet()) if (installedFile(browserType, String.valueOf(k)) != null) ids.add(String.valueOf(k));
            return ids;
        }

        void renderGrid() {
            if (hits.isEmpty()) { message("Sonuc yok", "Baska bir arama dene"); return; }
            JPanel grid = new JPanel(new GridLayout(0, sel == null ? 3 : 2, 12, 12)); grid.setOpaque(false);
            Set<String> ids = installedIds();
            for (Hit h : hits) {
                Card c = new Card(h, ids.contains(h.id)); c.selected = h == sel; c.click = () -> select(h);
                loadIcon(h, c::repaint); grid.add(c);
            }
            JPanel wrap = new JPanel(new BorderLayout(0, 12)); wrap.setOpaque(false); wrap.add(grid, BorderLayout.CENTER);
            if (hasMore) {
                JPanel mp = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0)); mp.setOpaque(false);
                KButton more = new KButton("Daha fazla yukle", false, () -> fetch(true)); more.setPreferredSize(new Dimension(170, 40));
                mp.add(more); wrap.add(mp, BorderLayout.SOUTH);
            }
            setContent(wrap);
        }

        void select(Hit h) { sel = h; renderGrid(); showDetail(); }

        void showDetail() {
            if (detail != null) { body.remove(detail); detail = null; }
            if (sel != null) { detail = buildDetail(sel); body.add(detail, BorderLayout.EAST); }
            body.revalidate(); body.repaint();
        }

        JPanel buildDetail(Hit h) {
            JPanel d = new JPanel(new BorderLayout(0, 12)) {
                protected void paintComponent(Graphics g0) {
                    Graphics2D g = aa(g0); g.setColor(new Color(20, 20, 30, 228)); g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 18, 18));
                    g.setColor(new Color(255, 255, 255, 22)); g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 18, 18));
                }
            };
            d.setOpaque(false); d.setBorder(new EmptyBorder(16, 16, 16, 16)); d.setPreferredSize(new Dimension(300, 10));
            JPanel top = new JPanel(new BorderLayout(12, 0)); top.setOpaque(false);
            IconBox ib = new IconBox(h); ib.setPreferredSize(new Dimension(56, 56)); loadIcon(h, ib::repaint); top.add(ib, BorderLayout.WEST);
            JPanel tx = new JPanel(new GridLayout(2, 1)); tx.setOpaque(false);
            tx.add(lbl(h.title, 17, true, FG)); tx.add(lbl(h.author, 13, false, MUTED)); top.add(tx, BorderLayout.CENTER);
            KButton x = new KButton("x", false, () -> { sel = null; renderGrid(); showDetail(); }); x.setPreferredSize(new Dimension(30, 30));
            JPanel xw = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0)); xw.setOpaque(false); xw.add(x); top.add(xw, BorderLayout.EAST);
            d.add(top, BorderLayout.NORTH);

            JTextArea ta = new JTextArea(h.desc); ta.setEditable(false); ta.setLineWrap(true); ta.setWrapStyleWord(true); ta.setOpaque(false);
            ta.setForeground(new Color(205, 205, 220)); ta.setFont(font(Font.PLAIN, 14)); ta.setBorder(null);
            JPanel mid = new JPanel(new BorderLayout(0, 12)); mid.setOpaque(false); mid.add(ta, BorderLayout.NORTH);
            JPanel below = new JPanel(new BorderLayout(0, 10)); below.setOpaque(false);
            JPanel tags = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4)); tags.setOpaque(false);
            for (String c : h.cats) tags.add(new Tag(c));
            below.add(tags, BorderLayout.NORTH);
            KButton link = new KButton("Modrinth'te ac", false, () -> {
                try { Desktop.getDesktop().browse(URI.create("https://modrinth.com/" + h.type + "/" + h.slug)); } catch (Exception e) { }
            });
            link.setPreferredSize(new Dimension(150, 36));
            JPanel lw = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)); lw.setOpaque(false); lw.add(link); below.add(lw, BorderLayout.CENTER);
            mid.add(below, BorderLayout.CENTER); d.add(mid, BorderLayout.CENTER);

            boolean isIn0 = installedFile(browserType, h.id) != null;
            KButton inst = new KButton(isIn0 ? "Kaldir" : "Yukle", !isIn0, null); inst.setPreferredSize(new Dimension(10, 46));
            inst.click = () -> {
                boolean isIn = installedFile(browserType, h.id) != null;
                inst.text = isIn ? "Kaldiriliyor..." : "Yukleniyor..."; inst.setEnabled(false);
                final int t = browserType;
                new Thread(() -> {
                    try {
                        if (isIn) removeProject(t, h.id); else installProject(h.id, t, 0, new HashSet<>());
                        SwingUtilities.invokeLater(() -> {
                            boolean now = installedFile(t, h.id) != null;
                            inst.text = now ? "Kaldir" : "Yukle"; inst.accent = !now; inst.setEnabled(true); inst.repaint();
                            if (t == browserType && !installedTab) renderGrid();
                        });
                    } catch (Exception e) {
                        log("Islem basarisiz (" + h.title + "): " + e.getMessage());
                        SwingUtilities.invokeLater(() -> { inst.text = "Hata - tekrar dene"; inst.setEnabled(true); });
                    }
                }).start();
            };
            d.add(inst, BorderLayout.SOUTH);
            return d;
        }

        void renderInstalled() {
            if (selLoader == 0 && browserType == 0) { message("Vanilla'da mod calismaz", "Oyna sayfasindan Fabric veya Quilt sec"); return; }
            Path dir = instDir().resolve(DIRS[browserType]);
            String ext = browserType == 0 ? ".jar" : ".zip", q = query.toLowerCase();
            JPanel list = vbox();
            try (var s = Files.exists(dir) ? Files.list(dir) : java.util.stream.Stream.<Path>empty()) {
                List<Path> files = new ArrayList<>(); s.forEach(files::add); Collections.sort(files);
                for (Path f : files) {
                    String n = f.getFileName().toString(); boolean dis = n.endsWith(".disabled");
                    String base = dis ? n.substring(0, n.length() - 9) : n;
                    if (!base.endsWith(ext) || (!q.isEmpty() && !base.toLowerCase().contains(q))) continue;
                    KButton tg = new KButton(dis ? "Ac" : "Kapat", false, null); tg.setPreferredSize(new Dimension(66, 34));
                    KButton rm = new KButton("Sil", false, null); rm.setPreferredSize(new Dimension(54, 34));
                    tg.click = () -> { try { Files.move(f, f.resolveSibling(dis ? base : n + ".disabled")); } catch (Exception e) { } renderInstalled(); };
                    rm.click = () -> { try { Files.deleteIfExists(f); } catch (Exception e) { } renderInstalled(); };
                    list.add(new Row(base.length() > 48 ? base.substring(0, 47) + "..." : base, (Files.size(f) / 1024) + " KB" + (dis ? "  -  kapali" : ""), tg, rm));
                    list.add(Box.createVerticalStrut(6));
                }
            } catch (Exception e) { }
            if (list.getComponentCount() == 0) list.add(new Row("Burada henuz bir sey yok", "Gozat sekmesinden yukle ya da Dosya ekle'yi kullan", new JComponent[0]));
            setContent(list);
        }

        void addFile() {
            if (selLoader == 0 && browserType == 0) return;
            JFileChooser fc = new JFileChooser(); fc.setMultiSelectionEnabled(true);
            if (fc.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return;
            try {
                Path dir = instDir().resolve(DIRS[browserType]); Files.createDirectories(dir);
                for (File f : fc.getSelectedFiles()) Files.copy(f.toPath(), dir.resolve(f.getName()), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) { log("Hata: " + e); }
            if (installedTab) renderInstalled();
        }
    }

    // ---------- helpers ----------
    void log(String s) {
        SwingUtilities.invokeLater(() -> { log.append(s + "\n"); log.setCaretPosition(log.getDocument().getLength()); });
    }
    void prog(int v, int max, String t) {
        SwingUtilities.invokeLater(() -> { progress.set(max == 0 ? 0 : v / (float) max); status.setText(t); });
    }
    static String get(String url) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "Kuro-Launcher/1.0").build(), HttpResponse.BodyHandlers.ofString()).body();
    }
    static void download(String url, Path dst) throws Exception { download(url, dst, 0); }
    static void download(String url, Path dst, long size) throws Exception {
        if (Files.exists(dst)) {
            long l = Files.size(dst);
            if (size > 0 ? l == size : l > 0) return;
            Files.delete(dst);
        }
        Files.createDirectories(dst.getParent());
        Path tmp = Paths.get(dst + ".part");
        Exception last = null;
        for (int i = 0; i < 3; i++) {
            try {
                HttpResponse<Path> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "Kuro-Launcher/1.0").build(), HttpResponse.BodyHandlers.ofFile(tmp));
                if (r.statusCode() != 200) throw new IOException("HTTP " + r.statusCode());
                if (size > 0 && Files.size(tmp) != size) throw new IOException("boyut hatasi");
                Files.move(tmp, dst, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (Exception e) { last = e; Files.deleteIfExists(tmp); }
        }
        throw last;
    }

    void runJobs(List<String[]> jobs, String label) throws Exception {
        ExecutorService ex = Executors.newFixedThreadPool(16);
        AtomicInteger done = new AtomicInteger(), fail = new AtomicInteger();
        List<Future<?>> fs = new ArrayList<>();
        for (String[] j : jobs) fs.add(ex.submit(() -> {
            try { download(j[0], Paths.get(j[1]), Long.parseLong(j[2])); }
            catch (Exception e) { fail.incrementAndGet(); log("Hata: " + j[0] + " (" + e.getMessage() + ")"); }
            int d = done.incrementAndGet();
            if (d % 15 == 0 || d == jobs.size()) prog(d, jobs.size(), label + " " + d + " / " + jobs.size());
        }));
        for (Future<?> f : fs) f.get();
        ex.shutdown();
        if (fail.get() > 0) throw new Exception(fail.get() + " dosya indirilemedi, interneti kontrol edip tekrar dene");
    }

    /** Downloads Mojang's official Java runtime for this version (correct Java major), falls back to bundled Java. */
    Path ensureJava(Map<String, Object> meta) {
        Path fallback = Paths.get(System.getProperty("java.home"), "bin", OS.equals("windows") ? "java.exe" : "java");
        try {
            if (meta.get("javaVersion") == null) return fallback;
            String comp = (String) Json.obj(meta.get("javaVersion")).get("component");
            String arch = System.getProperty("os.arch").toLowerCase();
            boolean arm = arch.contains("aarch64") || arch.contains("arm");
            String plat = OS.equals("windows") ? (arm ? "windows-arm64" : "windows-x64") : OS.equals("osx") ? (arm ? "mac-os-arm64" : "mac-os") : "linux";
            Path dir = ROOT.resolve("runtime").resolve(comp);
            Path exe = dir.resolve("bin").resolve(OS.equals("windows") ? "javaw.exe" : "java");
            Path ok = dir.resolve(".ok");
            if (Files.exists(ok) && Files.exists(exe)) return exe;
            log("Java calisma zamani indiriliyor (" + comp + ")...");
            Map<String, Object> all = Json.obj(get("https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json"));
            List<Object> arr = Json.arr(Json.obj(all.get(plat)).get(comp));
            if (arr == null || arr.isEmpty()) return fallback;
            String murl = (String) Json.obj(Json.obj(arr.get(0)).get("manifest")).get("url");
            Map<String, Object> files = Json.obj(Json.obj(get(murl)).get("files"));
            List<String[]> jobs = new ArrayList<>(); List<Path> exes = new ArrayList<>();
            for (Map.Entry<String, Object> en : files.entrySet()) {
                Map<String, Object> f = Json.obj(en.getValue());
                if (!"file".equals(f.get("type"))) continue;
                Map<String, Object> raw = Json.obj(Json.obj(f.get("downloads")).get("raw"));
                Path dst = dir.resolve(en.getKey());
                jobs.add(new String[]{(String) raw.get("url"), dst.toString(), String.valueOf(raw.get("size"))});
                if (Boolean.TRUE.equals(f.get("executable"))) exes.add(dst);
            }
            runJobs(jobs, "Java");
            for (Path x : exes) x.toFile().setExecutable(true);
            Files.writeString(ok, "ok");
            return exe;
        } catch (Exception e) {
            log("Java indirilemedi, yerlesik Java kullaniliyor: " + e);
            return fallback;
        }
    }

    void loadVersions() {
        try {
            Map<String, Object> m = Json.obj(get("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"));
            for (Object o : Json.arr(m.get("versions"))) {
                Map<String, Object> v = Json.obj(o);
                if ("release".equals(v.get("type"))) versionUrls.put((String) v.get("id"), (String) v.get("url"));
            }
            SwingUtilities.invokeLater(() -> {
                verPick.items.addAll(versionUrls.keySet());
                if (!versionUrls.containsKey(selVer) && !verPick.items.isEmpty()) { selVer = verPick.items.get(0); }
                verPick.text = selVer; verPick.repaint(); updateInfo();
            });
        } catch (Exception e) { log("Surum listesi alinamadi: " + e); }
    }

    // ---------- launch ----------
    volatile boolean running;
    void launch() {
        if (running) return;
        running = true;
        SwingUtilities.invokeLater(() -> play.setEnabled(false));
        try {
            String mc = selVer;
            if (mc == null || !versionUrls.containsKey(mc)) throw new Exception("Surum secilmedi");
            String name = account == null ? "Player" : account;
            prog(0, 1, "Surum bilgisi indiriliyor...");
            Map<String, Object> meta = Json.obj(get(versionUrls.get(mc)));
            Path verDir = ROOT.resolve("versions").resolve(mc);
            Files.createDirectories(verDir);

            List<Map<String, Object>> libs = new ArrayList<>();
            for (Object o : Json.arr(meta.get("libraries"))) libs.add(Json.obj(o));
            String mainClass = (String) meta.get("mainClass");
            List<String> jvmExtra = new ArrayList<>(), gameExtra = new ArrayList<>();
            collectArgs(meta, jvmExtra, gameExtra);

            if (selLoader > 0) {
                String base = selLoader == 1 ? "https://meta.fabricmc.net/v2" : "https://meta.quiltmc.org/v3";
                log(LOADERS[selLoader] + " profili indiriliyor...");
                List<Object> loaders = Json.arr(Json.parse(get(base + "/versions/loader/" + mc)));
                if (loaders.isEmpty()) throw new Exception("Bu surum icin " + LOADERS[selLoader] + " yok");
                Map<String, Object> pick = Json.obj(loaders.get(0));
                for (Object o : loaders) { Map<String, Object> l = Json.obj(Json.obj(o).get("loader")); if (Boolean.TRUE.equals(l.get("stable"))) { pick = Json.obj(o); break; } }
                String lv = (String) Json.obj(pick.get("loader")).get("version");
                Map<String, Object> fp = Json.obj(get(base + "/versions/loader/" + mc + "/" + lv + "/profile/json"));
                mainClass = (String) fp.get("mainClass");
                for (Object o : Json.arr(fp.get("libraries"))) libs.add(0, Json.obj(o));
                collectArgs(fp, jvmExtra, gameExtra);
            }

            List<Path> cp = new ArrayList<>();
            List<String[]> jobs = new ArrayList<>();
            Set<String> seenLib = new HashSet<>();
            for (Map<String, Object> lib : libs) {
                if (!allowed(lib.get("rules"))) continue;
                String nm = (String) lib.get("name");
                if (nm != null) { // ayni kutuphanenin farkli surumlerini ele (ilk gelen = Fabric'inki kazanir)
                    String[] c = nm.split(":");
                    String key = c.length >= 2 ? c[0] + ":" + c[1] + (c.length > 3 ? ":" + c[3] : "") : nm;
                    if (!seenLib.add(key)) continue;
                }
                Map<String, Object> dl = lib.get("downloads") == null ? null : Json.obj(lib.get("downloads"));
                String url, rel; long size = 0;
                if (dl != null && dl.get("artifact") != null) {
                    Map<String, Object> a = Json.obj(dl.get("artifact"));
                    url = (String) a.get("url"); rel = (String) a.get("path");
                    if (a.get("size") instanceof Number) size = ((Number) a.get("size")).longValue();
                } else if (lib.get("url") != null) {
                    String[] c = ((String) lib.get("name")).split(":");
                    rel = c[0].replace('.', '/') + "/" + c[1] + "/" + c[2] + "/" + c[1] + "-" + c[2] + ".jar";
                    url = lib.get("url") + rel;
                } else continue;
                Path dst = ROOT.resolve("libraries").resolve(rel);
                jobs.add(new String[]{url, dst.toString(), String.valueOf(size)});
                cp.add(dst);
            }
            Map<String, Object> client = Json.obj(Json.obj(meta.get("downloads")).get("client"));
            Path clientJar = verDir.resolve(mc + ".jar");
            jobs.add(new String[]{(String) client.get("url"), clientJar.toString(), String.valueOf(client.get("size"))});

            Map<String, Object> ai = Json.obj(meta.get("assetIndex"));
            String assetId = (String) ai.get("id");
            Path idx = ROOT.resolve("assets/indexes").resolve(assetId + ".json");
            download((String) ai.get("url"), idx, ((Number) ai.get("size")).longValue());
            Map<String, Object> objs = Json.obj(Json.obj(Json.parse(Files.readString(idx))).get("objects"));
            for (Object o : objs.values()) {
                Map<String, Object> ob = Json.obj(o); String h = (String) ob.get("hash");
                jobs.add(new String[]{"https://resources.download.minecraft.net/" + h.substring(0, 2) + "/" + h,
                        ROOT.resolve("assets/objects").resolve(h.substring(0, 2)).resolve(h).toString(), String.valueOf(ob.get("size"))});
            }
            log("Indiriliyor: " + jobs.size() + " dosya");
            runJobs(jobs, "Indiriliyor");

            cp.add(clientJar);
            for (Path x : cp) if (x.getFileName().toString().startsWith("asm")) log("ASM: " + x.getFileName());
            StringBuilder cps = new StringBuilder();
            for (Path p : new LinkedHashSet<>(cp)) cps.append(p).append(File.pathSeparator);
            Path game = instDir(); Files.createDirectories(game.resolve("mods"));

            Map<String, String> vars = new HashMap<>();
            vars.put("auth_player_name", name); vars.put("version_name", mc);
            vars.put("game_directory", game.toString()); vars.put("assets_root", ROOT.resolve("assets").toString());
            vars.put("assets_index_name", assetId);
            vars.put("auth_uuid", UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes()).toString().replace("-", ""));
            vars.put("auth_access_token", "0"); vars.put("clientid", "0"); vars.put("auth_xuid", "0");
            vars.put("user_type", "legacy"); vars.put("version_type", "Kuro");
            vars.put("natives_directory", ROOT.resolve("natives").toString());
            vars.put("launcher_name", "Kuro"); vars.put("launcher_version", "2.0");
            vars.put("classpath", cps.toString());

            prog(0, 1, "Java hazirlaniyor...");
            Path javaPath = ensureJava(meta);
            List<String> cmd = new ArrayList<>();
            cmd.add(javaPath.toString());
            cmd.add("-Xmx" + ci("ram", 4) + "G");
            for (String s : cs("jvm", "").trim().split("\\s+")) if (!s.isEmpty()) cmd.add(s);
            for (String s : jvmExtra) cmd.add(sub(s, vars));
            if (!jvmExtra.contains("-cp")) { cmd.add("-cp"); cmd.add(cps.toString()); }
            cmd.add(mainClass);
            for (String s : gameExtra) cmd.add(sub(s, vars));

            prog(1, 1, "Oyun calisiyor");
            log("Java: " + javaPath);
            log("Baslatiliyor: " + mc + " (" + LOADERS[selLoader] + ")");
            Process pr = new ProcessBuilder(cmd).directory(game.toFile()).redirectErrorStream(true).start();
            Discord.playing(mc, LOADERS[selLoader]);
            SwingUtilities.invokeLater(() -> frame.setState(Frame.ICONIFIED));
            try (BufferedReader r = new BufferedReader(new InputStreamReader(pr.getInputStream()))) {
                String l; while ((l = r.readLine()) != null) log(l);
            }
            int code = pr.waitFor();
            Discord.idle();
            log("Oyun kapandi (kod " + code + ")");
            prog(code == 0 ? 0 : 1, 1, code == 0 ? "Hazir" : "Oyun cokti (kod " + code + ") - logu incele");
            SwingUtilities.invokeLater(() -> { frame.setState(Frame.NORMAL); frame.toFront(); });
        } catch (Exception e) {
            log("HATA: " + e); prog(0, 1, "Hata olustu, logu kontrol et");
        } finally {
            running = false;
            SwingUtilities.invokeLater(() -> play.setEnabled(true));
        }
    }

    static String sub(String s, Map<String, String> v) {
        for (Map.Entry<String, String> e : v.entrySet()) s = s.replace("${" + e.getKey() + "}", e.getValue());
        return s;
    }

    void collectArgs(Map<String, Object> meta, List<String> jvm, List<String> game) {
        if (meta.get("arguments") == null) return;
        Map<String, Object> a = Json.obj(meta.get("arguments"));
        for (String k : new String[]{"jvm", "game"}) {
            List<String> out = k.equals("jvm") ? jvm : game;
            if (a.get(k) == null) continue;
            for (Object o : Json.arr(a.get(k))) {
                if (o instanceof String) { out.add((String) o); continue; }
                if (k.equals("game")) continue;
                Map<String, Object> m = Json.obj(o);
                if (!allowed(m.get("rules"))) continue;
                Object v = m.get("value");
                if (v instanceof String) out.add((String) v);
                else for (Object x : Json.arr(v)) out.add((String) x);
            }
        }
    }

    static boolean allowed(Object rules) {
        if (rules == null) return true;
        String sys = System.getProperty("os.arch").toLowerCase();
        boolean arm = sys.contains("aarch64") || sys.contains("arm"), x64 = sys.contains("amd64") || sys.contains("x86_64");
        boolean ok = false;
        for (Object o : Json.arr(rules)) {
            Map<String, Object> r = Json.obj(o);
            boolean match = r.get("features") == null;
            if (r.get("os") != null) {
                Map<String, Object> os = Json.obj(r.get("os"));
                if (os.get("name") != null && !os.get("name").equals(OS)) match = false;
                Object ar = os.get("arch");
                if ("x86".equals(ar) && (x64 || arm)) match = false;
                if ("arm64".equals(ar) && !arm) match = false;
            }
            if (match) ok = "allow".equals(r.get("action"));
        }
        return ok;
    }
}

/** Tiny JSON parser/serializer (no external dependencies). */
class Json {
    final String s; int i;
    Json(String s) { this.s = s; }
    static Object parse(String s) { return new Json(s).val(); }
    @SuppressWarnings("unchecked") static Map<String, Object> obj(Object o) { return o instanceof String ? (Map<String, Object>) parse((String) o) : (Map<String, Object>) o; }
    @SuppressWarnings("unchecked") static List<Object> arr(Object o) { return (List<Object>) o; }

    void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
    Object val() {
        ws();
        char c = s.charAt(i);
        if (c == '{') {
            Map<String, Object> m = new LinkedHashMap<>(); i++; ws();
            if (s.charAt(i) == '}') { i++; return m; }
            while (true) { ws(); String k = str(); ws(); i++; m.put(k, val()); ws(); if (s.charAt(i++) == '}') return m; }
        }
        if (c == '[') {
            List<Object> l = new ArrayList<>(); i++; ws();
            if (s.charAt(i) == ']') { i++; return l; }
            while (true) { l.add(val()); ws(); if (s.charAt(i++) == ']') return l; }
        }
        if (c == '"') return str();
        if (s.startsWith("true", i)) { i += 4; return true; }
        if (s.startsWith("false", i)) { i += 5; return false; }
        if (s.startsWith("null", i)) { i += 4; return null; }
        int st = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        String n = s.substring(st, i);
        return n.contains(".") || n.contains("e") || n.contains("E") ? (Object) Double.parseDouble(n) : (Object) Long.parseLong(n);
    }
    String str() {
        StringBuilder b = new StringBuilder(); i++;
        while (true) {
            char c = s.charAt(i++);
            if (c == '"') return b.toString();
            if (c == '\\') {
                char e = s.charAt(i++);
                switch (e) {
                    case 'n': b.append('\n'); break; case 't': b.append('\t'); break;
                    case 'r': b.append('\r'); break; case 'b': b.append('\b'); break;
                    case 'f': b.append('\f'); break;
                    case 'u': b.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                    default: b.append(e);
                }
            } else b.append(c);
        }
    }
    @SuppressWarnings("unchecked")
    static String str(Object o) {
        if (o == null) return "null";
        if (o instanceof String) return "\"" + ((String) o).replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
        if (o instanceof Map) {
            StringJoiner j = new StringJoiner(",", "{", "}");
            for (Map.Entry<String, Object> e : ((Map<String, Object>) o).entrySet()) j.add(str(e.getKey()) + ":" + str(e.getValue()));
            return j.toString();
        }
        if (o instanceof List) {
            StringJoiner j = new StringJoiner(",", "[", "]");
            for (Object x : (List<Object>) o) j.add(str(x));
            return j.toString();
        }
        return o.toString();
    }
}
