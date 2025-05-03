package roj.math;

/**
 * @author Roj234
 * @since 2025/3/23 14:33
 */
public interface Equation {
	double evaluate(double[] variables);
	double[] derivation(double[] variables);
}
