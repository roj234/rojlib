package roj.util;

/**
 * @author Roj234
 * @since 2023/3/11 0011 22:40
 */
public interface ArrayGetter {
	ArrayGetter
		BG = (a,p) -> ((byte[])a)[p],
		CG = (a, p) -> ((char[])a)[p],
		IG = (a, p) -> ((int[])a)[p];

	int get(Object arr, int pos);
}
