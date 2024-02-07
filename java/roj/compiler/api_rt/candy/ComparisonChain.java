package roj.compiler.api_rt.candy;

import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

/**
 * a test for AST-level candy
 * see google-commons for more details
 * @author Roj234
 * @since 2024/2/20 0020 1:28
 */
public abstract class ComparisonChain {
	private ComparisonChain() {}

	public static ComparisonChain start() { throw new CandyNotResolvedError(); }

	public abstract ComparisonChain compare(Comparable<?> a, Comparable<?> b);
	public abstract <T> ComparisonChain compare(@Nullable T a, @Nullable T b, Comparator<T> cmp);
	public abstract ComparisonChain compare(int a, int b);
	public abstract ComparisonChain compare(long a, long b);
	public abstract ComparisonChain compare(float a, float b);
	public abstract ComparisonChain compare(double a, double b);
	public abstract ComparisonChain compareTrueFirst(boolean a, boolean b);
	public abstract ComparisonChain compareFalseFirst(boolean a, boolean b);
	public abstract int result();
}