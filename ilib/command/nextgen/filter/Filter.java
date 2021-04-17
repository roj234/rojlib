package ilib.command.nextgen.filter;

import ilib.command.nextgen.XEntitySelector;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2023/2/28 0028 23:52
 */
public abstract class Filter implements Consumer<Entity> {
	public static final int TYPE_BIT = 1, COORD_BIT = 2, TAG_BIT = 4;

	protected int prevTick, successCount;
	protected Consumer<Entity> next;

	public void reset(XEntitySelector owner) {
		successCount = 0;
		if (next instanceof Filter) ((Filter) next).reset(owner);
	}
	public abstract void accept(Entity entity);

	public void setNext(Consumer<Entity> consumer) {
		next = consumer;
	}
	public int getSuccessCount() {
		return successCount;
	}

	public abstract int relativeDifficulty();

	static String nameOrUUID(Entity entity) {
		return entity instanceof EntityPlayerMP ? entity.getName() : entity.getCachedUniqueIdString();
	}

	public boolean playerOnly() {
		return false;
	}
	public void negate() {
		throw new UnsupportedOperationException();
	}
}
