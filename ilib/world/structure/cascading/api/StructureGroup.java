package ilib.world.structure.cascading.api;

import ilib.world.structure.cascading.GenerateContext;

import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface StructureGroup {
	/**
	 * 获取随机的一个结构
	 */
	@Nullable
	IStructureData getStructures(@Nonnull World world, @Nonnull GenerateContext context);

	/**
	 * 获得结构类别
	 *
	 * @return name
	 */
	@Nonnull
	String getName();
}