package holefinder;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public class HoleFinderClient implements ClientModInitializer {
    private static KeyMapping openKey;

    @Override
    public void onInitializeClient() {
        Config.load();

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("holefinder", "main"));
        openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.holefinder.open", InputConstants.Type.KEYSYM, InputConstants.KEY_INSERT, category));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openKey.consumeClick()) mc.setScreen(new HoleScreen());
            HoleScanner.tick(mc);
        });

        WorldRenderEvents.BEFORE_TRANSLUCENT.register(HoleRenderer::render);
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> HoleRenderer.close());
    }
}
