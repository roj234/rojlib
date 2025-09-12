package roj.math;

import roj.annotation.MayMutate;

/**
 * @author Roj234
 * @since 2025/3/23 14:33
 */
public interface Equation {
	double evaluate(double[] variables);
	double[] derivation(double[] variables);

	/**
	 * 牛顿迭代法求解非线性方程组<p>
	 * Example: <pre>{@code
	 * // 定义一个非线性方程组
	 * Polynomial equation1 = new Polynomial();
	 * equation1.varName = new String[] {"x"};
	 * equation1.coeffMatrix = new double[][] {
	 *       {20,19,18,17,16,15,14,13,12,11,10,9,8,7,6,5,4,3,2,1},
	 * };
	 * equation1.zeroVal = -5;
	 *
	 * System.out.println(equation1+" = 0");
	 *
	 * double[] initialGuess = {0.5};
	 * double tolerance = 1e-6;
	 * int maxIterations = 100;
	 * double[] solution = newtonSolve(new Equation[] {equation1}, initialGuess, tolerance, maxIterations);
	 * System.out.println("解: f1("+Arrays.toString(solution)+") = "+equation1.evaluate(solution));
	 * }</pre>
	 */
	public static double[] newtonSolve(Equation[] equations, @MayMutate double[] guess, double tolerance, int maxIterations) {
		double[] fx = new double[equations.length];
		double[][] dm = new double[equations.length][];

		tolerance *= tolerance;

		while (maxIterations-- > 0) {
			for (int i = 0; i < equations.length; i++) {
				fx[i] = equations[i].evaluate(guess);
				dm[i] = equations[i].derivation(guess);
			}

			double[] deltaX = gaussianElimination(dm, fx);
			// 将向量乘以 -1
			for (int i = 0; i < guess.length; i++) {
				guess[i] -= deltaX[i];
			}

			// 向量的长度
			double length = 0;
			for (double value : deltaX) length += value * value;

			if (length < tolerance) return guess;
			// NaN
			if (length != length) break;
		}
		throw new ArithmeticException("无解");
	}

	/**
	 * 最小二乘法拟合n次多项式<p>
	 * <pre>{@code
	 * // 示例数据
	 * double[] x = {8, 16, 24, 32};
	 * double[] y = {103, 130, 158, 191};
	 * int n = 1; // 多项式的次数
	 *
	 * // 拟合多项式
	 * double[] coefficients = fitPolynomial(x, y, n);
	 *
	 * // 输出多项式系数
	 * System.out.println("拟合的"+ n +"次多项式系数: "+Arrays.toString(coefficients));
	 * System.out.println("R^2=" + polyR2(x, y, coefficients));
	 * for (int i = 0; i < x.length; i++) {
	 * 		System.out.println("f("+x[i]+") = "+ polyEval(x[i], coefficients));
	 * }
	 * }</pre>
	 * @param variable 变量x的值
	 * @param result 预期的结果y
	 * @param coeffCount 多项式的次数n
	 * @return 多项式的系数
	 */
	public static double[] fitPolynomial(double[] variable, double[] result, int coeffCount) {
		int m = variable.length;
		// 创建正规方程的系数矩阵 A 和常数向量 b
		double[][] A = new double[coeffCount + 1][coeffCount + 1];
		double[] b = new double[coeffCount + 1];

		// 填充系数矩阵 A 和常数向量 b
		for (int i = 0; i <= coeffCount; i++) {
			for (int j = 0; j <= coeffCount; j++) {
				for (int k = 0; k < m; k++) {
					// x is known
					A[i][j] += Math.pow(variable[k], i + j);
				}
			}
			for (int k = 0; k < m; k++) {
				b[i] += Math.pow(variable[k], i) * result[k];
			}
		}

		// 求解正规方程 Ax = b，得到多项式系数
		return gaussianElimination(A, b);
	}

	/**
	 * 高斯消元法求解线性方程组 Ax = b
	 *
	 * 求解下列方程组使用
	 * 3x + 2y = 114
	 * -1x + 3y = -514
	 * gaussianElimination({{3, 2}, {-1, 3}}, {114,-514});
	 *
	 * @param A 方程组未知数的系数
	 * @param b 方程组的常数项（预期值）
	 * @return 方程组的未知数
	 */
	public static double[] gaussianElimination(double[][] A, double[] b) {
		int n = A.length;
		// 消元过程
		for (int i = 0; i < n; i++) {
			// 选主元
			int maxRow = i;
			for (int k = i + 1; k < n; k++) {
				if (Math.abs(A[k][i]) > Math.abs(A[maxRow][i])) {
					maxRow = k;
				}
			}

			// 交换行
			double[] temp = A[i];
			A[i] = A[maxRow];
			A[maxRow] = temp;
			double t = b[i];
			b[i] = b[maxRow];
			b[maxRow] = t;

			// 消元
			for (int j = i + 1; j < n; j++) {
				double factor = A[j][i] / A[i][i];
				for (int k = i; k < n; k++) {
					A[j][k] -= factor * A[i][k];
				}
				b[j] -= factor * b[i];
			}
		}

		// 回代过程
		double[] x = new double[n];
		for (int i = n - 1; i >= 0; i--) {
			double sum = 0.0;
			for (int j = i + 1; j < n; j++) {
				sum += A[i][j] * x[j];
			}
			x[i] = (b[i] - sum) / A[i][i];
		}

		return x;
	}

	// 计算 R^2 值
	public static double polyR2(double[] variable, double[] result, double[] coeff) {
		int m = variable.length;
		// 计算 results 的均值
		double yMean = 0.0;
		for (double val : result) {
			yMean += val;
		}
		yMean /= m;

		// 计算总离差平方和 SST
		double sst = 0.0;
		for (double val : result) {
			sst += Math.pow(val - yMean, 2);
		}

		// 计算残差平方和 SSE
		double sse = 0.0;
		for (int i = 0; i < m; i++) {
			double predicted = polyEval(variable[i], coeff);
			sse += Math.pow(result[i] - predicted, 2);
		}

		// 计算 R^2 值
		return 1 - (sse / sst);
	}

	public static double polyEval(double input, double[] coeff) {
		double mul = 1;
		double result = 0;
		for (int i = 0; i < coeff.length; i++) {
			result += mul * coeff[i];
			mul *= input;
		}
		return result;
	}
}
