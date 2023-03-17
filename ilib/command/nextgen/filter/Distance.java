package ilib.command.nextgen.filter;

import ilib.command.nextgen.XEntitySelector;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * @author Roj234
 * @since 2023/2/28 0028 23:53
 */
public final class Distance extends Filter {
	private final double dstSq;
	private Vec3d center;

	public Distance(int distance) {this.dstSq = distance*distance;}

	@Override
	public void reset(XEntitySelector owner) {
		center = owner.f_sender.getPositionVector();
	}

	@Override
	public void accept(Entity entity) {
		if (entity.getDistanceSq(center.x, center.y, center.z) > dstSq) return;

		successCount++;
		next.accept(entity);
	}

	@Override
	public int relativeDifficulty() {
		return 1000;
	}
}
