package ilib.command.nextgen.filter;

import ilib.command.nextgen.SkipThisWorld;
import ilib.command.nextgen.XEntitySelector;

import net.minecraft.entity.Entity;
import net.minecraft.scoreboard.ScorePlayerTeam;

import java.util.Collection;

/**
 * @author Roj234
 * @since 2023/2/28 0028 23:53
 */
public final class Team extends StringNegatable {
	private Collection<String> actualTeam;

	public Team(String team) {
		super(team);
	}

	@Override
	public void reset(XEntitySelector owner) {
		super.reset(owner);

		ScorePlayerTeam t = owner.f_world.getScoreboard().getTeam(s);
		if (t == null || t.getMembershipCollection().isEmpty()) throw SkipThisWorld.INSTANCE;
		actualTeam = t.getMembershipCollection();
	}

	@Override
	boolean matches(Entity entity) {
		return actualTeam.contains(nameOrUUID(entity));
	}

	@Override
	public int relativeDifficulty() {
		return 600;
	}
}
