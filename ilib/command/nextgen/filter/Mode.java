package ilib.command.nextgen.filter;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.GameType;

/**
 * @author Roj234
 * @since 2023/2/28 0028 23:53
 */
public final class Mode extends Filter {
	private boolean negate;
	private final GameType type;

	public Mode(GameType type) {
		this.type = type;
	}

	@Override
	public void accept(Entity entity) {
		if (((EntityPlayerMP) entity).interactionManager.getGameType() == type != negate) {
			successCount++;
			next.accept(entity);
		}
	}

	@Override
	public int relativeDifficulty() {
		return 100;
	}

	@Override
	public boolean playerOnly() {
		return true;
	}

	@Override
	public void negate() {
		negate = true;
	}
}
