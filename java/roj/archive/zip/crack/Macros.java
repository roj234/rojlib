package roj.archive.zip.crack;

/**
 * @author Roj234
 * @since 2022/11/12 0012 17:26
 */
interface Macros {
	int MASK_0_16  = 0x0000ffff,
		MASK_0_24  = 0x00ffffff,
		MASK_0_26  = 0x03ffffff,
		MASK_0_32  = 0xffffffff,
		MASK_26_32 = 0xfc000000,
		MASK_24_32 = 0xff000000,
		MASK_10_32 = 0xfffffc00,
		MASK_8_32  = 0xffffff00,
		MASK_2_32  = 0xfffffffc;

	/// \brief Maximum difference between 32-bits integers A and B[x,32)
	/// knowing that A = B + b and b is a byte.
	///
	/// The following equations show how the difference is bounded by the given constants:
	///
	///     A = B + b
	///     A = B[0,x) + B[x,32) + b
	///     A - B[x,32) = B[0,x) + b
	///     A - B[x,32) <= 0xffffffff[0,x) + 0xff
	int MAXDIFF_0_24 = MASK_0_24 + 0xff,
		MAXDIFF_0_26 = MASK_0_26 + 0xff;
}
