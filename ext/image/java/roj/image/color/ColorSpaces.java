package roj.image.color;

import roj.math.Vec3d;

/**
 * @author Roj234
 * @since 2025/11/11 07:40
 */
public class ColorSpaces {
	// https://en.wikipedia.org/wiki/SRGB
	// https://github.com/w3c/csswg-drafts/issues/5922
	// invert matrix:
	//  [0.41239079926595934, 0.357584339383878,   0.1804807884018343,
	//   0.21263900587151027, 0.715168678767756,   0.07219231536073371,
	//   0.01933081871559182, 0.11919477979462598, 0.9505321522496607]
	public static final LinearColorSpace LinearRGB = new LinearColorSpace("RGB",
			 3.2409699419045226,  -1.537383177570094,  -0.4986107602930034,
			-0.9692436362808796,   1.8759675015077202,  0.04155505740717559,
			 0.05563007969699366, -0.20397695888897652, 1.0569715142428786
	);
	public static final SCurveColorSpace sRGB = new SCurveColorSpace("sRGB", LinearRGB);
	public static final SCurveColorSpace Display_P3 = new SCurveColorSpace("Display P3",
			 2.493496911941425,   -0.9313836179191239, -0.40271078445071684,
			 -0.8294889695615747,  1.7626640603183463,  0.023624685841943577,
			 0.03584583024378447, -0.07617238926804182, 0.9568845240076872
	);
	public static final Rec2020ColorSpace Rec2020 = new Rec2020ColorSpace();

	public static final OKLabColorSpace OKLab = new OKLabColorSpace();
	public static final OKLCHColorSpace OKLCH = new OKLCHColorSpace();

	public enum GamutMappingMethod {
		CLIP,
		MIN_DELTAE
	}

	@SuppressWarnings("unchecked")
	public static <FromType, ToType> ToType convertColorSpace(
			ColorSpace<FromType> from, ColorSpace<ToType> to,
			FromType color, GamutMappingMethod gamutMappingMethod
	) {
		if (from.equals(to)) return (ToType) color;

		Vec3d xyz = from.toCIEXYZ(color);
		ToType color1 = to.fromCIEXYZ(xyz);
		// TODO impl gamutMapping
		return color1;
	}
}
