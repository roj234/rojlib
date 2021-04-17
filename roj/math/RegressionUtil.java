package roj.math;

import java.util.List;
import java.util.function.Function;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2021/7/8 0:14
 */
public class RegressionUtil {
    /**
     * 排列数 :
     * C <sup>m</sup><sub>n</sub>
     */
    public static long Cx(int m, int n) {
        if(m > n >> 1)
            return Cx(n - m, n);

        // m <= n
        long uu = 1, dd = 1;
        int i;
        for (i = m; i > 0; i--) { // (n - m)!
            dd *= i;
        }
        for (i = n; i > (n - m); i--) { // n! / m!
            uu *= i;
        }
        return uu / dd;
    }

    /**
     * 组合数 :
     * A <sup>m</sup><sub>n</sub>
     */
    public static long Ax(int m, int n) {
        long sum = 1;
        m = n - m;
        for (int i = n; i > m; i--) {
            sum *= i;
        }
        return sum;
    }

    public static Function<Double, Double> ZhenTai(double u, double o2) {
        double v = 1 / Math.sqrt(Math.PI * 2 * o2);
        double v2 = -1 / (2 * o2);
        return (in) -> {
            in -= u;
            return v * Math.pow(Math.E, in * in * v2);
        };
    }

    /**
     * 线性回归
     * @param points 数据点
     * @return [a, b, algoId]
     */
    public static double[] regression(List<Vec2d> points) {
        double avgX = 0;
        double avgY = 0;

        double top = 0, bottom = 0, rgl = 0;
        for (int i = 0; i < points.size(); i++) {
            Vec2d p = points.get(i);
            double px = p.x - avgX;
            double py = p.y - avgY;
            top += px * py;
            bottom += px * px;
            rgl += py * py;
        }

        double r = top / (Math.sqrt(bottom) * Math.sqrt(rgl));

        return new double[] {
                top / bottom, avgY - top / bottom * avgX, 0
        };
    }
}
