package roj.math;

import roj.text.CharList;

/**
 * @author Roj234
 * @since 2025/3/23 0023 14:18
 */
public class Polynomial implements Equation {
	public String[] varName;
	public double[][] coeffMatrix;
	public double zeroVal;

	public Polynomial() {}
	public Polynomial(double[] coeff) {
		varName = new String[]{"x"};
		coeffMatrix = new double[1][coeff.length-1];
		for (int i = 1; i < coeff.length; i++) {
			coeffMatrix[0][i-1] = coeff[i];
		}
		zeroVal = coeff[0];
	}
	public Polynomial(String equation) {
		// TODO
	}

	@Override
	public String toString() {
		var sb = new CharList();
		for (int i = 0; i < varName.length; i++) {
			var name = varName[i];
			double[] coeff = coeffMatrix[i];
			for (int j = coeff.length - 1; j >= 0; j--) {
				if (coeff[j] != 0) {
					sb.append(coeff[j]).append(" * ").append(name);
					if (j > 0) sb.append('^').append(j+1);
					sb.append(" + ");
				}
			}
		}
		return sb.append(zeroVal).toStringAndFree();
	}

	@Override
	public double evaluate(double[] variables) {
		var output = zeroVal;
		for (int i = 0; i < coeffMatrix.length; i++) {
			output += polyEval2(variables[i], coeffMatrix[i]);
		}
		return output;
	}
	public static double polyEval2(double input, double[] coeff) {
		double mul = input; // start from x^2
		double result = 0;
		for (int i = 0; i < coeff.length; i++) {
			result += mul * coeff[i];
			mul *= input;
		}
		return result;
	}

	@Override
	public double[] derivation(double[] variables) {
		var output = new double[coeffMatrix.length];
		for (int i = 0; i < coeffMatrix.length; i++) {
			double[] matrix = coeffMatrix[i];
			double coeffOut = 0;
			for (int j = 0; j < matrix.length;j++) {
				coeffOut += matrix[j] * (j+1) * Math.pow(variables[i], j);
			}
			output[i] = coeffOut;
		}
		return output;
	}
}
