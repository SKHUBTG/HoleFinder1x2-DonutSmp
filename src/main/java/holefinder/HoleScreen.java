package holefinder;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class HoleScreen extends Screen {
    private static final int PW = 300, ROW_H = 22, GAP = 4, PAD = 12, TITLE_H = 56;
    private static final int KEY_INSERT = 260; // GLFW_KEY_INSERT

    private final List<Row> holeRows = new ArrayList<>();
    private final List<Row> macroRows = new ArrayList<>();
    private int tab = 0; // 0 = hole finder, 1 = auto-tp
    private Row active;
    private EditBox commandBox;

    public HoleScreen() {
        super(Component.literal("Hole Finder"));
        buildHoleRows();
        buildMacroRows();
    }

    private void buildHoleRows() {
        Config c = Config.I;
        holeRows.add(new Toggle("Enabled", () -> c.enabled, v -> c.enabled = v));
        holeRows.add(new Cycle("Direction", new String[]{"Horizontal", "Vertical", "Both"}, () -> c.mode, v -> c.mode = v));
        holeRows.add(new Cycle("Length", new String[]{"1x2", "1x3", "1x2 + 1x3"}, () -> c.length, v -> c.length = v));
        holeRows.add(new Cycle("Pockets", new String[]{"Air + breakable", "Air only", "Filled only"}, () -> c.cellMode, v -> c.cellMode = v));
        holeRows.add(new Toggle("Bedrock walls (air only)", () -> c.onlyBedrock, v -> c.onlyBedrock = v));
        holeRows.add(new Slider("Radius", 16, 256, () -> c.radius, v -> c.radius = v));
        holeRows.add(new Slider("Max holes", 1, 100, () -> c.maxHoles, v -> c.maxHoles = v));
        holeRows.add(new Slider("Min Y", -64, 320, () -> c.minY, v -> c.minY = v));
        holeRows.add(new Slider("Max Y", -64, 320, () -> c.maxY, v -> c.maxY = v));
        holeRows.add(new Slider("Scan speed", 1, 16, () -> c.chunksPerTick, v -> c.chunksPerTick = v));
        holeRows.add(new Slider("Red", 0, 255, () -> c.r, v -> c.r = v));
        holeRows.add(new Slider("Green", 0, 255, () -> c.g, v -> c.g = v));
        holeRows.add(new Slider("Blue", 0, 255, () -> c.b, v -> c.b = v));
        holeRows.add(new Slider("Opacity", 20, 255, () -> c.alpha, v -> c.alpha = v));
    }

    private void buildMacroRows() {
        Config c = Config.I;
        macroRows.add(new Toggle("Auto-TP enabled", () -> c.macroEnabled, v -> c.macroEnabled = v));
        macroRows.add(new Slider("Cooldown (sec)", 1, 120, () -> c.macroCooldownSeconds, v -> c.macroCooldownSeconds = v));
        macroRows.add(new Note("Command: type below, e.g. /tpa Nick"));
        macroRows.add(new Note("Trigger key: set in Controls -> Hole Finder"));
        for (int i = 0; i < 3; i++) {
            int idx = i;
            while (c.macroSteps.size() <= idx) c.macroSteps.add(new Config.MacroStep("ENTER", 250));
            macroRows.add(new Cycle("Step " + (i + 1) + " key", MacroRunner.KEY_NAMES.toArray(new String[0]),
                    () -> MacroRunner.KEY_NAMES.indexOf(c.macroSteps.get(idx).key),
                    v -> c.macroSteps.get(idx).key = MacroRunner.KEY_NAMES.get(v)));
            macroRows.add(new Slider("Step " + (i + 1) + " delay (ms)", 0, 3000,
                    () -> c.macroSteps.get(idx).delayMs, v -> c.macroSteps.get(idx).delayMs = v));
        }
    }

    @Override
    protected void init() {
        Config c = Config.I;
        int px = (width - PW) / 2;
        commandBox = new EditBox(font, px + PAD, 0, PW - PAD * 2, 18, Component.literal("command"));
        commandBox.setMaxLength(200);
        commandBox.setValue(c.macroCommand);
        commandBox.setResponder(v -> c.macroCommand = v);
        addRenderableWidget(commandBox);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        Config.save();
    }

    private List<Row> rows() {
        return tab == 0 ? holeRows : macroRows;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        Config c = Config.I;
        List<Row> rows = rows();
        boolean showCommandBox = tab == 1;
        int extra = showCommandBox ? 24 : 0;
        int ph = TITLE_H + rows.size() * (ROW_H + GAP) + extra + PAD;
        int px = (width - PW) / 2;
        int py = Math.max(4, (height - ph) / 2);

        rounded(g, px, py, PW, ph, 10, 0xE6121218);
        g.drawString(font, "Hole Finder", px + PAD, py + 12, 0xFFFFFFFF);
        rounded(g, px + PW - PAD - 30, py + 8, 30, 16, 6, 0xFF000000 | (c.r << 16) | (c.g << 8) | c.b);

        // tabs
        int tabY = py + 28, tabH = 20, tabW = (PW - PAD * 2 - GAP) / 2;
        drawTab(g, px + PAD, tabY, tabW, tabH, "Finder", tab == 0, mx, my);
        drawTab(g, px + PAD + tabW + GAP, tabY, tabW, tabH, "Auto-TP", tab == 1, mx, my);

        int y = py + TITLE_H;
        if (showCommandBox) {
            commandBox.setX(px + PAD);
            commandBox.setY(y + 2);
            commandBox.setWidth(PW - PAD * 2);
            commandBox.render(g, mx, my, delta);
            y += 24;
        }
        commandBox.visible = showCommandBox;

        for (Row r : rows) {
            r.x = px + PAD;
            r.y = y;
            r.w = PW - PAD * 2;
            r.draw(g, font, mx, my);
            y += ROW_H + GAP;
        }

        if (tab == 1) {
            String cd = MacroRunner.remainingCooldown();
            String status = cd == null ? "Ready" : "Cooldown: " + cd + "s";
            g.drawString(font, status, px + PAD, py + ph - 10, cd == null ? 0xFF7CFC7C : 0xFFFF8C69);
        }
    }

    private void drawTab(GuiGraphics g, int x, int y, int w, int h, String label, boolean sel, int mx, int my) {
        boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + h;
        rounded(g, x, y, w, h, 6, sel ? accentColor() : (hover ? 0xFF2E2E3A : 0xFF23232D));
        int tw = font.width(label);
        g.drawString(font, label, x + (w - tw) / 2, y + 6, sel ? 0xFF101014 : 0xFFDDDDE5);
        lastTabX1 = x;
        lastTabX2 = x + w;
    }

    private int lastTabX1, lastTabX2;
    private int accentColor() {
        Config c = Config.I;
        return 0xFF000000 | (c.r << 16) | (c.g << 8) | c.b;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        Config c = Config.I;
        int px = (width - PW) / 2;
        int tabY = topY() + 28, tabH = 20, tabW = (PW - PAD * 2 - GAP) / 2;
        if (event.y() >= tabY && event.y() <= tabY + tabH) {
            if (event.x() >= px + PAD && event.x() <= px + PAD + tabW) { tab = 0; return true; }
            if (event.x() >= px + PAD + tabW + GAP && event.x() <= px + PAD + tabW + GAP + tabW) { tab = 1; return true; }
        }
        if (tab == 1 && commandBox.isMouseOver(event.x(), event.y())) {
            return commandBox.mouseClicked(event, doubled);
        }
        for (Row r : rows()) {
            if (r.hit(event.x(), event.y())) {
                active = r;
                r.press(event.x());
                return true;
            }
        }
        return super.mouseClicked(event, doubled);
    }

    private int topY() {
        List<Row> rows = rows();
        boolean showCommandBox = tab == 1;
        int extra = showCommandBox ? 24 : 0;
        int ph = TITLE_H + rows.size() * (ROW_H + GAP) + extra + PAD;
        return Math.max(4, (height - ph) / 2);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (active != null) {
            active.drag(event.x());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        active = null;
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (tab == 1 && commandBox.isFocused() && commandBox.keyPressed(event)) return true;
        if (event.key() == KEY_INSERT) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        if (tab == 1 && commandBox.isFocused()) return commandBox.charTyped(event);
        return super.charTyped(event);
    }

    static void rounded(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        r = Math.min(r, Math.min(w, h) / 2);
        for (int i = 0; i < h; i++) {
            int inset = 0;
            int d = i < r ? r - i : (i >= h - r ? i - (h - r) + 1 : 0);
            if (d > 0) {
                double dy = d - 0.5;
                inset = r - (int) Math.round(Math.sqrt(Math.max(0, r * r - dy * dy)));
            }
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
        }
    }

    abstract static class Row {
        int x, y, w;

        boolean hit(double mx, double my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + ROW_H;
        }

        void press(double mx) {
        }

        void drag(double mx) {
        }

        abstract void draw(GuiGraphics g, Font f, int mx, int my);

        void base(GuiGraphics g, int mx, int my) {
            rounded(g, x, y, w, ROW_H, 7, hit(mx, my) ? 0xFF2E2E3A : 0xFF23232D);
        }

        int accent() {
            Config c = Config.I;
            return 0xFF000000 | (c.r << 16) | (c.g << 8) | c.b;
        }
    }

    static class Note extends Row {
        final String text;

        Note(String text) {
            this.text = text;
        }

        @Override
        boolean hit(double mx, double my) {
            return false;
        }

        @Override
        void draw(GuiGraphics g, Font f, int mx, int my) {
            g.drawString(f, text, x + 2, y + 7, 0xFF9A9AA8);
        }
    }

    static class Toggle extends Row {
        final String label;
        final BooleanSupplier get;
        final Consumer<Boolean> set;

        Toggle(String label, BooleanSupplier get, Consumer<Boolean> set) {
            this.label = label;
            this.get = get;
            this.set = set;
        }

        @Override
        void press(double mx) {
            set.accept(!get.getAsBoolean());
        }

        @Override
        void draw(GuiGraphics g, Font f, int mx, int my) {
            base(g, mx, my);
            g.drawString(f, label, x + 8, y + 7, 0xFFE8E8F0);
            boolean on = get.getAsBoolean();
            int sx = x + w - 34, sy = y + 5;
            rounded(g, sx, sy, 26, 12, 6, on ? accent() : 0xFF3A3A48);
            rounded(g, on ? sx + 15 : sx + 1, sy + 1, 10, 10, 5, 0xFFFFFFFF);
        }
    }

    static class Cycle extends Row {
        final String label;
        final String[] options;
        final IntSupplier get;
        final IntConsumer set;

        Cycle(String label, String[] options, IntSupplier get, IntConsumer set) {
            this.label = label;
            this.options = options;
            this.get = get;
            this.set = set;
        }

        @Override
        void press(double mx) {
            set.accept((get.getAsInt() + 1) % options.length);
        }

        @Override
        void draw(GuiGraphics g, Font f, int mx, int my) {
            base(g, mx, my);
            g.drawString(f, label, x + 8, y + 7, 0xFFE8E8F0);
            int i = Math.max(0, Math.min(options.length - 1, get.getAsInt()));
            String v = options[i];
            g.drawString(f, v, x + w - 8 - f.width(v), y + 7, accent());
        }
    }

    static class Slider extends Row {
        final String label;
        final int min, max;
        final IntSupplier get;
        final IntConsumer set;

        Slider(String label, int min, int max, IntSupplier get, IntConsumer set) {
            this.label = label;
            this.min = min;
            this.max = max;
            this.get = get;
            this.set = set;
        }

        @Override
        void press(double mx) {
            drag(mx);
        }

        @Override
        void drag(double mx) {
            double t = Math.max(0, Math.min(1, (mx - x) / w));
            set.accept((int) Math.round(min + t * (max - min)));
        }

        @Override
        void draw(GuiGraphics g, Font f, int mx, int my) {
            base(g, mx, my);
            double t = (get.getAsInt() - min) / (double) (max - min);
            int fw = Math.max(ROW_H / 2, (int) (w * t));
            rounded(g, x, y, fw, ROW_H, 7, (accent() & 0x00FFFFFF) | 0x66000000);
            g.drawString(f, label, x + 8, y + 7, 0xFFE8E8F0);
            String v = String.valueOf(get.getAsInt());
            g.drawString(f, v, x + w - 8 - f.width(v), y + 7, 0xFFFFFFFF);
        }
    }
}
