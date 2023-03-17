package ilib.capabilities;

import ilib.api.energy.MEItem;
import ilib.api.energy.METile;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;

import java.util.concurrent.Callable;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class Capabilities {
	@CapabilityInject(EntitySize.class)
	public static Capability<EntitySize> RENDERING_SIZE;
	@CapabilityInject(TmpData.class)
	public static Capability<TmpData> TEMP_STORAGE;
	@CapabilityInject(MEItem.class)
	public static Capability<MEItem> MENERGY;
	@CapabilityInject(METile.class)
	public static Capability<METile> MENERGY_TILE;

	public static final NilStorage<?> NIL_STORAGE = new NilStorage<>();
	public static final INBTStorage<?> NBT_STORAGE = new INBTStorage<>();
	public static final Callable<?> NIL_FACTORY = () -> null;

	@SuppressWarnings("unchecked")
	public static void init() {
		CapabilityManager.INSTANCE.register(METile.class, (NilStorage<METile>) NIL_STORAGE, (Callable<METile>) NIL_FACTORY);
		CapabilityManager.INSTANCE.register(MEItem.class, (INBTStorage<MEItem>) NBT_STORAGE, MEImpl::new);
		CapabilityManager.INSTANCE.register(TmpData.class, (NilStorage<TmpData>) NIL_STORAGE, (Callable<TmpData>) NIL_FACTORY);
		CapabilityManager.INSTANCE.register(EntitySize.class, (NilStorage<EntitySize>) NIL_STORAGE, (Callable<EntitySize>) NIL_FACTORY);
	}
}
