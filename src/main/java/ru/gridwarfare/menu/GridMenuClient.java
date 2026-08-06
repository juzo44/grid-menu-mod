package ru.gridwarfare.menu;

import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = GridMenu.MOD_ID, value = Dist.CLIENT)
public final class GridMenuClient {
    private static boolean replacing;

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (replacing || event.getNewScreen() instanceof GridScreen) return;
        if (!(event.getNewScreen() instanceof TitleScreen)) return;
        replacing = true;
        event.setNewScreen(new GridScreen());
        replacing = false;
    }
}
