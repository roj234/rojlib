package lac.server;

import net.minecraft.util.ResourceLocation;

import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.RegistryManager;

import java.lang.ref.SoftReference;
import java.util.Map;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2021/7/9 0:44
 */
public final class SharedSnapshot {
    static SoftReference<Map<ResourceLocation, ForgeRegistry.Snapshot>> tmp0 = new SoftReference<>(null);

    public static Map<ResourceLocation, ForgeRegistry.Snapshot> getSnapshot() {
        Map<ResourceLocation, ForgeRegistry.Snapshot> snapshot = tmp0.get();
        if(tmp0.get() == null) {
            snapshot = RegistryManager.ACTIVE.takeSnapshot(false);
            tmp0 = new SoftReference<>(snapshot);
        }
        return snapshot;
    }
}
