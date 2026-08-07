package ru.gridwarfare.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;

/**
 * Даёт доступ к ресурсам мода без прямой зависимости в рендерере.
 */
public final class MinecraftHolder {
    private MinecraftHolder() {}

    public static InputStream getResource(String path) throws IOException {
        var rm = Minecraft.getInstance().getResourceManager();
        var loc = ResourceLocation.fromNamespaceAndPath(GridMenu.MOD_ID, path);
        var opt = rm.getResource(loc);
        if (opt.isEmpty()) throw new IOException("Not found: " + loc);
        return opt.get().get();
    }
}
