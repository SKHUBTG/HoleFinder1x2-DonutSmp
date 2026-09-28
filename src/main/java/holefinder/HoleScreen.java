package holefinder;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
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
    private static final int PW = 280, ROW_H = 22, GAP = 4, PAD = 12, TITLE_H = 32;
    private static final int KEY_INSERT = 260; // GLFW_KEY_INSERT

    private final List<Row> rows = new ArrayList<>();
    private Row active;

    public HoleScreen() {
        super(Component.literal("Hole Finder"));
        Config c = Config.I;
        rows.add(new Toggle("Enabled", () -> c.enabled, v -> c.enabled = v));
        rows.add(new Cycle("Mode", new String[]{"Horizontal 1x2", "Vertical 1x2", "Both"}, () -> c.mode, v -> c.mode = v));
        rows.add(new Toggle("Only bedrock walls", () -> c.onlyBedrock, v -> c.onlyBedrock = v));
        rows.add(new Slider("Radius", 16, 256, () -> c.radius, v -> c.radius = v));
        rows.add(new Slider("Max holes", 1, 100, () -> c.maxHoles, v -> c.maxHoles = v));
        rows.add(new Slider("Min Y", -64, 320, () -> c.minY, v -> c.minY = v));
        rows.add(new Slider("Max Y", -64, 320, () -> c.maxY, v -> c.maxY = v));
        rows.add(new Slider("Scan speed", 1, 16, () -> c.chunksPerTick, v -> c.chunksPerTick = v));
        rows.add(new Slider("Red", 0, 255, () -> c.r, v -> c.r = v));
        rows.add(new Slider("Green", 0, 255, () -> c.g, v -> c.g = v));
        rows.add(new Slider("Blue", 0, 255, () -> c.b, v -> c.b = v));
        rows.add(new Slider("Opacity", 20, 255, () -> c.alpha, v -> c.alpha = v));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        Config.save();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        Config c = Config.I;
        int ph = TITLE_H + rows.size() * (ROW_H + GAP) + PAD;
        int px = (width - PW) / 2;
        int py = Math.max(4, (height - ph) / 2);

        rounded(g, px, py, PW, ph, 10, 0xE6121218);
        g.drawString(font, "Hole Finder", px + PAD, py + 12, 0xFFFFFFFF);
        rounded(g, px + PW - PAD - 30, py + 8, 30, 16, 6, 0xFF000000 | (c.r << 16) | (c.g << 8) | c.b);

        int y = py + TITLE_H;
        for (Row r : rows) {
            r.x = px + PAD;
            r.y = y;
            r.w = PW - PAD * 2;
            r.draw(g, font, mx, my);
            y += ROW_H + GAP;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        for (Row r : rows) {
            if (r.hit(event.x(), event.y())) {
                active = r;
                r.press(event.x());
                return true;
            }
        }
        return super.mouseClicked(event, doubled);
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
        if (event.key() == KEY_INSERT) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    /** Filled rounded rectangle drawn as horizontal scanlines. */
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
            String v = options[get.getAsInt()];
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
