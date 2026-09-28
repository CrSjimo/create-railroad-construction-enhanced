package dev.sjimo.rrce.platform;

import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public final class PlatformLoader {
    private static final PlatformLoader INSTANCE = new PlatformLoader();
    public static PlatformLoader getInstance() { return INSTANCE; }
    public boolean isModLoaded(String id) { return FabricLoader.getInstance().isModLoaded(id); }
    public Path getConfigDir() { return FabricLoader.getInstance().getConfigDir(); }
}
