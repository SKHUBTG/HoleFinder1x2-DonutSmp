package holefinder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
    public static Config I = new Config();

    public boolean enabled = true;
    public boolean onlyBedrock = false;
    /** 0 = horizontal 1x2, 1 = vertical 1x2, 2 = both */
    public int mode = 0;
    /** 0 = any (air or breakable blocks inside), 1 = air only, 2 = filled only */
    public int cellMode = 0;
    public int radius = 96;
    public int maxHoles = 20;
    public int minY = -64;
    public int maxY = -50;
    public int chunksPerTick = 4;
    public int rescanTicks = 60;
    public int r = 255, g = 60, b = 60, alpha = 110;

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
    }

    public static void save() {
        try {
            Files.writeString(file(), GSON.toJson(I));
        } catch (Exception ignored) {
        }
    }
}
