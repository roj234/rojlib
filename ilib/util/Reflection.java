package ilib.util;

import roj.asm.type.Type;
import roj.reflect.DirectAccessor;

import net.minecraft.block.BlockRedstoneWire;
import net.minecraft.util.math.Vec3d;

import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;

import java.util.List;

/**
 * @author Roj233
 * @since 2021/8/25 1:06
 */
// 去除xyz的final
//!!AT [ "net.minecraft.util.math.Vec3d", ["field_72450_a", "field_72448_b", "field_72449_c"]]
public interface Reflection {
	Reflection HELPER = preloadNecessaryClassesBeforeDefine(DirectAccessor.builder(Reflection.class))
		.access(Vec3d.class, new String[] {"field_72450_a", "field_72448_b", "field_72449_c"}, null, new String[] {"setVecX", "setVecY", "setVecZ"})
		//.delegate(EnumSet.class, "addAll")
		.i_access("java/util/Collections$UnmodifiableList", "list", new Type("java/util/List"), "getModifiableList", null, false)
		.access(BlockRedstoneWire.class, "field_150181_a", null, "setRedstoneProvidePower")
		.access(Event.class, "phase", null, "setEventPhase")
		.delegate(Event.class, "setup", "resetEvent")
		.build();

	static DirectAccessor<Reflection> preloadNecessaryClassesBeforeDefine(DirectAccessor<Reflection> builder) {
		return builder;
	}

	static Vec3d setVec(Vec3d v, double x, double y, double z) {
		Reflection i = HELPER;
		i.setVecX(v, x);
		i.setVecY(v, y);
		i.setVecZ(v, z);
		return v;
	}

	// region Field
	void setVecX(Vec3d vector, double x);

	void setVecY(Vec3d vector, double y);

	void setVecZ(Vec3d vector, double z);

	<T> List<T> getModifiableList(List<T> unmodifiableList);

	void setRedstoneProvidePower(BlockRedstoneWire block, boolean redstoneProvidePower);

	void setEventPhase(Event event, EventPriority value);

	// endregion
	// region Method
	void resetEvent(Event event);
	// endregion
	// region Construct
	// endregion

}
