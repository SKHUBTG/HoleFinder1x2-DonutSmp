package holefinder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Config {
    public static Config I = new Config();

    // ---- Hole finder ----
    public boolean enabled = true;
    public boolean onlyBedrock = false;
    public int mode = 0;       // 0 horizontal, 1 vertical, 2 both (ignored for L-shape)
    public int cellMode = 0;   // 0 air+breakable, 1 air only, 2 filled only
    public int shape = 0;      // 0 = 1x2, 1 = 1x3, 2 = 1x2+1x3, 3 = L-shape (3 vertical + 1 side, for spawner+enderchest)
    public int radius = 96;
    public int maxHoles = 20;
    public int minY = -64;
    public int maxY = -50;
    public int chunksPerTick = 4;
    public int rescanTicks = 60;
    public int r = 90, g = 170, b = 255, alpha = 110;
    public int theme = 0; // index into HoleScreen presets, 0 = custom (uses r/g/b above)

    // ---- Macro / auto-tp ----
    public boolean macroEnabled = false;
    public String macroCommand = "/tpa Steve";
    public int macroCooldownSeconds = 5;
    public List<MacroStep> macroSteps = new ArrayList<>(List.of(
            new MacroStep("DOWN", 250),
            new MacroStep("RIGHT", 250),
            new MacroStep("ENTER", 250)
    ));

    public static class MacroStep {
        public String key;
        public int delayMs;

        public MacroStep() {
        }

        public MacroStep(String key, int delayMs) {
            this.key = key;
            this.delayMs = delayMs;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("holefinder.json");
    }

    public static void load() {
        try {
            Path p = file();
            if (Files.exists(p)) {
                Config c = GSON.fromJson(Files.readString(p), Config.class);
                if (c != null) I = c;
            }
        } catch (Exception ignored) {
        }
        if (I.macroSteps == null) I.macroSteps = new ArrayList<>();
    }

    public static void save() {
        try {
            Files.writeString(file(), GSON.toJson(I));
        } catch (Exception ignored) {
        }
    }
}
