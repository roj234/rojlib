package roj.image.color;

import roj.math.Vec3d;

/**
 * @author Roj234
 * @since 2025/11/11 14:38
 */
public interface ColorSpace<ComponentType> {
	Vec3d toCIEXYZ(ComponentType color);
	ComponentType fromCIEXYZ(Vec3d xyz);
	default void validate(ComponentType color) throws IllegalArgumentException {}
}
