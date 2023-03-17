package ilib.command.nextgen.filter;

import ilib.command.nextgen.SkipThisWorld;
import ilib.command.nextgen.XEntitySelector;

import net.minecraft.entity.Entity;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;

/**
 * @author Roj234
 * @since 2023/2/28 0028 23:53
 */
public final class Score extends Filter {
	private final String objId;
	private ScoreObjective actualObj;
	private int min, max;

	public Score(String objId) {
		this.objId = objId;
	}

	@Override
	public void reset(XEntitySelector owner) {
		super.reset(owner);

		actualObj = owner.f_world.getScoreboard().getObjective(objId);
		if (actualObj == null) throw SkipThisWorld.INSTANCE;
	}

	@Override
	public void accept(Entity entity) {
		Scoreboard sb = actualObj.getScoreboard();
		String name = nameOrUUID(entity);
		if (!sb.entityHasObjective(name, actualObj)) return;

		int score = sb.getOrCreateScore(name, actualObj).getScorePoints();
		if (score >= min && score <= max) {
			successCount++;
			next.accept(entity);
		}
	}

	@Override
	public int relativeDifficulty() {
		return 600;
	}
}
