package ilib.asm.nx;

import ilib.asm.Loader;
import ilib.asm.util.ICreator;
import ilib.misc.MCHooks;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.collect.ToIntMap;
import roj.reflect.DirectAccessor;
import roj.util.Helpers;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.function.Function;

/**
 * @author Roj234
 * @since 2020/8/21 21:34
 */
@Nixim("net.minecraftforge.fml.common.registry.EntityEntry")
class FastEntityConst {
	@Shadow("factory")
	Function<World, ? extends Entity> factory;
	@Shadow("cls")
	Class<? extends Entity> cls;

	@Copy(unique = true)
	static ICreator entityCreator;
	@Copy(unique = true)
	static ToIntMap<String> entityCreatorId;
	@Copy(unique = true, staticInitializer = "initEC")
	static RandomAccessFile entityCache;

	static void initEC() {
		try {
			entityCache = new RandomAccessFile("Implib_FEC.bin", "rw");
			ToIntMap<String> map = entityCreatorId = new ToIntMap<>();
			ICreator creator = (ICreator) MCHooks.batchGenerate(entityCache, true, map);
			if (creator != null) {
				entityCreator = creator;
				Loader.logger.info("使用BatchGen节省了 " + map.size() + " 个无用的class");
			}
			entityCache.seek(0);
			entityCache.writeInt(0);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Inject("init")
	protected void init() {
		if (entityCache != null) {
			try {
				MCHooks.batchAdd(entityCache, cls.getName(), cls);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		Object o;
		int i = entityCreatorId.getOrDefault(cls.getName(), -1);
		if (i >= 0) {
			((ICreator) (o = entityCreator.clone())).setId(i);
		} else {
			o = DirectAccessor.builder(Function.class).construct(cls, "apply", World.class).build();
		}

		factory = Helpers.cast(o);
	}
}
