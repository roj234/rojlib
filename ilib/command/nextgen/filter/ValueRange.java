package ilib.command.nextgen.filter;

import net.minecraft.entity.Entity;

import java.util.function.ToDoubleFunction;

/**
 * @author Roj234
 * @since 2023/2/28 0028 23:53
 */
public final class ValueRange extends Filter {
	private double min, max;
	private final int diff;
	private final ToDoubleFunction<Entity> retainer;

	public ValueRange(ToDoubleFunction<Entity> retainer, int diff) {
		this.retainer = retainer;
		this.diff = diff;
	}

	private static final ToDoubleFunction<Entity> X = (entity) -> entity.posX;
	private static final ToDoubleFunction<Entity> Y = (entity) -> entity.posY;
	private static final ToDoubleFunction<Entity> Z = (entity) -> entity.posZ;
	private static final ToDoubleFunction<Entity> XROT = (entity) -> entity.rotationPitch;
	private static final ToDoubleFunction<Entity> YROT = (entity) -> entity.rotationYaw % 180f;
	public static ValueRange x() {
		return new ValueRange(X, 600);
	}
	public static ValueRange y() {
		return new ValueRange(Y, 600);
	}
	public static ValueRange z() {
		return new ValueRange(Z, 600);
	}
	public static ValueRange xRot() {
		return new ValueRange(XROT, 100);
	}
	public static ValueRange yRot() {
		return new ValueRange(YROT, 101);
	}

	public void setMin(double min) {
		this.min = min;
	}
	public void setMax(double max) {
		this.max = max;
	}

	public void accept(Entity entity) {
		double v = retainer.applyAsDouble(entity);
		if (/*v==v && */v >= min && v <= max) {
			successCount++;
			next.accept(entity);
		}
	}

	@Override
	public int relativeDifficulty() {
		return diff;
	}
}
