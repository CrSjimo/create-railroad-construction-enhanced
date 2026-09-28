package dev.sjimo.rrce.platform;
import java.nio.file.Path;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
public final class PlatformLoader {
    private static final PlatformLoader INSTANCE = new PlatformLoader();
    public static PlatformLoader getInstance() { return INSTANCE; }
    public boolean isModLoaded(String id) { return FMLLoader.getLoadingModList().getMods().stream().anyMatch(mod -> mod.getModId().equals(id)); }
    public Path getConfigDir() { return FMLPaths.CONFIGDIR.get(); }
}
