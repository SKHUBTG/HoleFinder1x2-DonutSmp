package holefinder;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * A small bottom strip, not the full menu: lets you flip the pocket shape
 * with one click. The world keeps rendering and ticking behind it.
 */
public class QuickPanelScreen extends Screen {
    private static final String[] LABELS = {"1x2", "1x3", "1x2+1x3", "L-shape"};
    private static final int KEY_INSERT = 260;

    private float openAnim = 0f;
    private int[] cellX;
    private int barY, barH, cellW;

    public QuickPanelScreen() {
        super(Component.literal("Quick Shape"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        openAnim = Math.min(1f, openAnim + delta * 0.3f);
        float ease = 1f - (1f - openAnim) * (1f - openAnim);

        int items = LABELS.length;
        int pad = 8, gap = 6, h = 26;
        int w = 240;
        int fullH = h + pad * 2;
        barH = fullH;
        int x = (width - w) / 2;
        int restY = height - fullH - 24;
        barY = height - (int) (fullH * ease) - (int) (24 * ease);

        HoleScreen.rounded(g, x, barY, w, fullH, 12, 0xE20A0A0E);

        cellW = (w - pad * 2 - gap * (items - 1)) / items;
        cellX = new int[items];
        Config c = Config.I;
        int accent = 0xFF000000 | (c.r << 16) | (c.g << 8) | c.b;
        for (int i = 0; i < items; i++) {
            int cx = x + pad + i * (cellW + gap);
            cellX[i] = cx;
            boolean sel = c.shape == i;
            boolean hover = mx >= cx && mx <= cx + cellW && my >= barY + pad && my <= barY + pad + h;
            HoleScreen.rounded(g, cx, barY + pad, cellW, h, 8, sel ? accent : (hover ? 0xFF232329 : 0xFF17171C));
            String label = LABELS[i];
            int tw = font.width(label);
            g.drawString(font, label, cx + (cellW - tw) / 2, barY + pad + 9, sel ? 0xFF0A0A0E : 0xFFCCCCD6);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (cellX != null) {
            for (int i = 0; i < cellX.length; i++) {
                if (event.x() >= cellX[i] && event.x() <= cellX[i] + cellW
                        && event.y() >= barY + 8 && event.y() <= barY + barH - 8) {
                    Config.I.shape = i;
                    Config.save();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == KEY_INSERT) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }
}
