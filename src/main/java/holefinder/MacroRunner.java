package holefinder;

import net.minecraft.client.Minecraft;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Map;

/**
 * Sends the configured chat command, then simulates the configured key presses
 * with per-step delays. Key presses are done with java.awt.Robot at the OS level,
 * since Minecraft has no public API to "press" an arbitrary key on demand.
 * Everything here is triggered by the player pressing the macro keybind themselves;
 * nothing runs on its own.
 */
public class MacroRunner {
    private static long lastRun = 0;
    private static Robot robot;

    private static final Map<String, Integer> KEYS = Map.ofEntries(
            Map.entry("UP", KeyEvent.VK_UP),
            Map.entry("DOWN", KeyEvent.VK_DOWN),
            Map.entry("LEFT", KeyEvent.VK_LEFT),
            Map.entry("RIGHT", KeyEvent.VK_RIGHT),
            Map.entry("ENTER", KeyEvent.VK_ENTER),
            Map.entry("SPACE", KeyEvent.VK_SPACE),
            Map.entry("TAB", KeyEvent.VK_TAB),
            Map.entry("ESCAPE", KeyEvent.VK_ESCAPE),
            Map.entry("SHIFT", KeyEvent.VK_SHIFT)
    );

    public static final List<String> KEY_NAMES = List.copyOf(KEYS.keySet());

    public static String remainingCooldown() {
        Config c = Config.I;
        long left = (lastRun + c.macroCooldownSeconds * 1000L) - System.currentTimeMillis();
        return left > 0 ? String.format("%.1f", left / 1000.0) : null;
    }

    public static void trigger() {
        Config c = Config.I;
        if (!c.macroEnabled) return;
        if (remainingCooldown() != null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;
        lastRun = System.currentTimeMillis();

        String cmd = c.macroCommand == null ? "" : c.macroCommand.trim();
        if (cmd.isEmpty()) return;
        if (cmd.startsWith("/")) {
            mc.getConnection().sendCommand(cmd.substring(1));
        } else {
            mc.getConnection().sendChat(cmd);
        }

        List<Config.MacroStep> steps = c.macroSteps;
        if (steps == null || steps.isEmpty()) return;
        Thread t = new Thread(() -> runSteps(steps), "holefinder-macro");
        t.setDaemon(true);
        t.start();
    }

    private static void runSteps(List<Config.MacroStep> steps) {
        try {
            if (robot == null) robot = new Robot();
        } catch (AWTException e) {
            return;
        }
        for (Config.MacroStep step : steps) {
            try {
                Thread.sleep(Math.max(0, step.delayMs));
            } catch (InterruptedException e) {
                return;
            }
            Integer code = KEYS.get(step.key);
            if (code == null) continue;
            robot.keyPress(code);
            robot.keyRelease(code);
        }
    }
}
