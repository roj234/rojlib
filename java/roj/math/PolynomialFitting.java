package roj.math;

import java.util.Arrays;

/**
 * @author Roj234
 * @since 2025/2/6
 */
public class PolynomialFitting {
    public static void main(String[] args) {
        // 示例数据
        double[] x = {8, 16, 24, 32};
        double[] y = {103, 130, 158, 191};
        int n = 1; // 多项式的次数

        // 拟合多项式
        double[] coefficients = fit(x, y, n);

        // 输出多项式系数
        System.out.println("拟合的 " + n + " 次多项式系数: " + Arrays.toString(coefficients));
        System.out.println("R^2="+calculateR2(x, y, coefficients));
        for (int i = 0; i < x.length; i++) {
            System.out.println("f("+x[i]+") = "+eval(x[i], coefficients));
        }
    }

    // 使用最小二乘法拟合n次多项式
    public static double[] fit(double[] x, double[] y, int n) {
        int m = x.length;
        // 创建正规方程的系数矩阵 A 和常数向量 b
        double[][] A = new double[n + 1][n + 1];
        double[] b = new double[n + 1];

        // 填充系数矩阵 A 和常数向量 b
        for (int i = 0; i <= n; i++) {
            for (int j = 0; j <= n; j++) {
                for (int k = 0; k < m; k++) {
                    A[i][j] += Math.pow(x[k], i + j);
                }
            }
            for (int k = 0; k < m; k++) {
                b[i] += Math.pow(x[k], i) * y[k];
            }
        }

        // 求解正规方程 Ax = b，得到多项式系数
        return gaussianElimination(A, b);
    }
    // 高斯消元法求解线性方程组 Ax = b
    private static double[] gaussianElimination(double[][] A, double[] b) {
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
    public static double calculateR2(double[] x, double[] y, double[] coefficients) {
        int m = x.length;
        // 计算 y 的均值
        double yMean = 0.0;
        for (double val : y) {
            yMean += val;
        }
        yMean /= m;

        // 计算总离差平方和 SST
        double sst = 0.0;
        for (double val : y) {
            sst += Math.pow(val - yMean, 2);
        }

        // 计算残差平方和 SSE
        double sse = 0.0;
        for (int i = 0; i < m; i++) {
            double predicted = eval(x[i], coefficients);
            sse += Math.pow(y[i] - predicted, 2);
        }

        // 计算 R^2 值
        return 1 - (sse / sst);
    }

    public static double eval(double input, double[] coeff) {
        double mul = 1;
        double result = 0;
        for (int i = 0; i < coeff.length; i++) {
            result += mul * coeff[i];
            mul *= input;
        }
        return result;
    }
}