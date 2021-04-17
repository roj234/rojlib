package roj.math;

import roj.text.TextUtil;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * Math utilities.
 */
public abstract class MathUtils {
    public static final double HALF_PI = Math.PI / 2;
    public static final double TWO_PI = 6.283185307179586476925286766559; // 2 * Pi
    // public static final double E = Math.E;
    // public static final double LN_10 = 2.30258509299404568401799145468;
    public static final double EPS_2 = 0.00000000000001; // 1e-14
    // public static final double EPS_1 = EPS_2 * EPS_2;

    public static final double P5_DEG_MUL = 1.0 / (180.0 * Math.PI);          // 1e-28

    public static final byte[] HEX_MAXS = new byte[8], OCT_MAXS = new byte[16], BIN_MAXS = new byte[32];

    static {
        Arrays.fill(MathUtils.HEX_MAXS, (byte) 'f');
        MathUtils.HEX_MAXS[7]++;
        Arrays.fill(MathUtils.OCT_MAXS, (byte) '7');
        MathUtils.OCT_MAXS[15]++;
        Arrays.fill(MathUtils.BIN_MAXS, (byte) '1');
        MathUtils.BIN_MAXS[31]++;
    }

    //public static final BigDecimal BIG_PI = new BigDecimal("3.14159265358979323846264338327950288419716939937510582097494459230781640628620899862803482534211706798214808651328230664709384460955058223172535940812848111745028410270193852110555964462294895493038964");
    //public static final BigDecimal BIG_E = new BigDecimal("2.7182818284590452353602874713526624977572470936999595749669676277240766303535475945713821785251664274");
    /**
     * @see MathUtils#clamp(double, double, double)
     */
    public static long clamp(long val, long min, long max) {
        return val < min ? min : val > max ? max : val;
    }

    /**
     * Clamps the given value using the specified maximum and minimum.
     *
     * @param val the value to be clamped.
     * @param min the minimum to which the given value should be clamped.
     * @param max the maximum to which the given value should be clamped
     * @return the clamped value, i.e. {@code val} if {@code val} is between {@code minBlock} and {@code maxBlock}, {@code maxBlock }
     * if {@code val} is greater than {@code maxBlock} or {@code minBlock} if {@code val} is smaller than {@code minBlock}.
     */
    public static double clamp(double val, double min, double max) {
        return val < min ? min : val > max ? max : val;
    }

    /**
     * @see MathUtils#clamp(double, double, double)
     */
    public static int clamp(int val, int min, int max) {
        return val < min ? min : val > max ? max : val;
    }

    public static int max(int... ints) {
        int i = 1;
        while (i < ints.length)
            ints[0] = Math.max(ints[0], ints[i++]);
        return ints[0];
    }

    public static int min(int... ints) {
        int i = 1;
        while (i < ints.length)
            ints[0] = Math.min(ints[0], ints[i++]);
        return ints[0];
    }

    /**
     * 多边形面积
     */
    public static double compute_Polygon_Area(MPShape polygon) {
        List<Vec2d> pts = polygon.getPoints();

        int N = pts.size();
        if(N < 3)
            return 0;

        double area = pts.get(0).y * (pts.get(N - 1).x - pts.get(1).x);
        for(int i = 1; i < N; ++i)
            area += pts.get(i).y * (pts.get(i - 1).x - pts.get((i + 1) % N).x);

        return Math.abs(area / 2);
    }

    /**
     * 判断点是否在折线上
     */
    public static boolean isOn_PolyLine(Vec2d point, MPShape polyline) {
        Rect2d bounds = polyline.getBounds();
        if(bounds.contains(point)) {
            return false;
        }

        //判断点是否在线段上，设点为Q，线段为P1P2 ，
        //判断点Q在该线段上的依据是：( Q - P1 ) × ( P2 - P1 ) = 0，且 Q 在以 P1，P2为对角顶点的矩形内
        List<Vec2d> pts = polyline.getPoints();//获取多边形点
        int N = pts.size() - 1;
        for(int i = 0; i < N; i++){
            Vec2d curPt = pts.get(i);
            Vec2d nextPt = pts.get(i + 1);
            //首先判断point是否在curPt和nextPt之间，即：此判断该点是否在该线段的外包矩形内
            if (point.y >= Math.min(curPt.y, nextPt.y) && point.y <= Math.max(curPt.y, nextPt.y) &&
                    point.x >= Math.min(curPt.x, nextPt.x) && point.x <= Math.max(curPt.x, nextPt.x)){
                //判断点是否在直线上公式
                double precision = (curPt.y - point.y) * (nextPt.x - point.x) -
                        (nextPt.y - point.y) * (curPt.x - point.x);
                if(Math.abs(precision) < EPS_2) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 判断点是否多边形内
     * @param canOn 点位于多边形的顶点或边上，也算做点在多边形内吗
     */
    public static boolean isIn_Polygon(Vec2d point, MPShape polygon, boolean canOn) {
        Rect2d bounds = polygon.getBounds();
        if(bounds.contains(point)) {
            return false;
        }

        List<Vec2d> pts = polygon.getPoints();

        //下述代码来源：http://paulbourke.net/geometry/insidepoly/，进行了部分修改
        //基本思想是利用射线法，计算射线与多边形各边的交点，如果是偶数，则点在多边形外，否则
        //在多边形内。还会考虑一些特殊情况，如点在多边形顶点上，点在多边形边上等特殊情况。

        int N = pts.size();
        int intersects = 0;//cross points count of x
        Vec2d p1, p2;//neighbour bound vertices

        p1 = pts.get(0);//left vertex
        for(int i = 1; i <= N; ++i){//check all rays
            if(point.equals(p1)){
                return canOn;//p is an vertex
            }

            p2 = pts.get(i % N);//right vertex
            if(point.x < Math.min(p1.x, p2.x) || point.x > Math.max(p1.x, p2.x)){//ray is outside of our interests
                p1 = p2;
                continue;//next ray left point
            }

            if(point.x > Math.min(p1.x, p2.x) && point.x < Math.max(p1.x, p2.x)){//ray is crossing over by the algorithm (common part of)
                if(point.y <= Math.max(p1.y, p2.y)){//x is before of ray
                    if(p1.x == p2.x && point.y >= Math.min(p1.y, p2.y)){//overlies on a horizontal ray
                        return canOn;
                    }

                    if(p1.y == p2.y){//ray is vertical
                        if(p1.y == point.y){//overlies on a vertical ray
                            return canOn;
                        }else{//before ray
                            ++intersects;
                        }
                    }else{//cross point on the left side
                        double xinters = (point.x - p1.x) * (p2.y - p1.y) / (p2.x - p1.x) + p1.y;//cross point of y
                        if(Math.abs(point.y - xinters) < EPS_2) {//overlies on a ray
                            return canOn;
                        }

                        if(point.y < xinters){//before ray
                            ++intersects;
                        }
                    }
                }
            } else {//special case when ray is crossing through the vertex
                if(point.x == p2.x && point.y <= p2.y){//p crossing over p2
                    Vec2d p3 = pts.get((i + 1) % N); //next vertex
                    if(point.x >= Math.min(p1.x, p3.x) && point.x <= Math.max(p1.x, p3.x)){//p.x lies between p1.x & p3.x
                        ++intersects;
                    }else{
                        intersects += 2;
                    }
                }
            }
            p1 = p2;//next ray left point
        }

        //偶数在多边形外, 奇数在多边形内
        return (intersects & 1) != 0;
    }

    /**
     * 计算折线或者点数组的长度
     */
    public static double polyLineLength(MPShape polyline) {
        List<Vec2d> pts = polyline.getPoints();//获取多边形点

        int N = pts.size() - 1;
        if(N < 1) {//小于2个点，返回0
            return 0;
        }

        //遍历所有线段将其相加，计算整条线段的长度
        double totalDis = 0;
        for(int i = 0; i < N; i++){
            Vec2d curPt = pts.get(i);
            Vec2d nextPt = pts.get(i + 1);
            totalDis += curPt.sub(nextPt).len();
            curPt.add(nextPt);
        }

        return totalDis;
    }

    /**
     * @see MathUtils#interpolate(double, double, double)
     */
    public static float interpolate(float in, float inMin, float inMax, float outMin, float outMax) {
        if (inMin > inMax) { // reverse
            float t = inMin;
            inMin = inMax;
            inMax = t;
            t = outMin;
            outMin = outMax;
            outMax = t;
        }

        if (in <= inMin) return outMin;
        if (in >= inMax) return outMax;

        float xFrac = (in - inMin) / (inMax - inMin);
        return outMin + xFrac * (outMax - outMin);
    }

    /**
     * <p>
     * linearly interpolate for y between [inMin, outMin] to [inMax, outMax] using in
     * </p>
     * y = outMin + (outMax - outMin) * (in - inMin) / (inMax - inMin) <br>
     * For example: <br>
     * if [inMin, outMin] is [0, 100], and [inMax,outMax] is [1, 200], <br>
     * then as in increases from 0 to 1, this function will increase from 100 to 200 <br>
     *
     * @return linearly interpolated value.  If in is outside the range, clip it to the nearest end
     */
    public static double interpolate(double in, double inMin, double inMax, double outMin, double outMax) {
        if (inMin > inMax) { // reverse
            double t = inMin;
            inMin = inMax;
            inMax = t;
            t = outMin;
            outMin = outMax;
            outMax = t;
        }

        if (in <= inMin) return outMin;
        if (in >= inMax) return outMax;

        double xFrac = (in - inMin) / (inMax - inMin);
        return outMin + xFrac * (outMax - outMin);
    }

    /**
     * As same as interpolate(delta, 0, 1, old, now)
     */
    public static double interpolate(double old, double now, double delta) {
        return old + delta * (now - old);
    }

    /**
     * As same as interpolate(delta, 0, 1, old, now)
     */
    public static float interpolate(float old, float now, float delta) {
        return old + delta * (now - old);
    }

    /**
     * Implementation detail: The runtime complexity is in O(log2(n)). <br>
     * Negative numbers has the same number of digits as the corresponding positive ones.
     *
     * @param n The given integer
     * @return The number of digits of the given integer n in O(log2(n))
     */
    public static int digitCount(int n) {

        /* overflow if n is negative */
        if (n == Integer.MIN_VALUE)
            return 10;


        /* make positive */
        if (n < 0)
            n = -n;


        if (n < 100000)
            /* 1 <= digit count <= 5 */
            if (n < 100)
                /* 1 <= digit count <= 2 */
                return (n < 10) ? 1 : 2;
            else
                /* 3 <= digit count <= 5 */
                if (n < 1000)
                    return 3;
                else
                    /* 4 <= digit count <= 5 */
                    return (n < 10000) ? 4 : 5;
        else
            /* 6 <= digit count <= 10 */
            if (n < 10000000)
                /* 6 <= digit count <= 7 */
                return (n < 1000000) ? 6 : 7;
            else
                /* 8 <= digit count <= 10 */
                if (n < 100000000)
                    return 8;
                else
                    /* 9 <= digit count <= 10 */
                    return (n < 1000000000) ? 9 : 10;
    }

    static final char[] CHINA_NUMERIC = new char[]{
            '零', '一', '二', '三', '四', '五', '六', '七', '八', '九'
    };
    static final char[] CHINA_NUMERIC_POSITION = new char[]{
            '十', '百', '千'
    };
    static final char[] CHINA_NUMERIC_LEV = new char[]{
            '万', '亿'
    };

    public static StringBuilder toChinaString(StringBuilder list, long number) {
        StringBuilder sb = new StringBuilder();
        if (number < 0) {
            sb.append('负');
            number = -number;
        }
        while (number > 0) {
            int curs = (int) (number % 10);
            number = (number - curs) / 10;
            sb.append(CHINA_NUMERIC[curs]);
        } // Step 1 一二三四五六七八九
        sb.reverse();

        final int firstLength = 3; // 万

        final char C_ZERO = CHINA_NUMERIC[0];

        int j = 0;
        boolean k = false;
        for (int i = sb.length() - 1; i >= 1; i--) {
            char c = sb.charAt(i - 1);
            if (c != C_ZERO) {
                char t = j == firstLength ? CHINA_NUMERIC_LEV[k ? 1 : 0] : CHINA_NUMERIC_POSITION[j];
                sb.insert(i, t);
            } else if (j == firstLength) {
                sb.setCharAt(i, CHINA_NUMERIC_LEV[k ? 1 : 0]);
            }
            if ((j++) == CHINA_NUMERIC_POSITION.length) {
                j = 0;
                k = !k;
            }
        } // Step 2 六万七千八百九十一亿二千三百四十五万六千七百八十九

        int zero = -1;
        int van = 0;
        for (int i = sb.length() - 1; i > 0; i--) {
            char c = sb.charAt(i);
            switch (c) {
                case '零': {
                    if (zero == -1 || zero++ > 0)
                        sb.deleteCharAt(i);
                }
                break;
                // rem 亿万 因为是反过来的
                case '万': {
                    van = 1;
                    if (zero != -1)
                        zero++;
                }
                break;
                case '亿': {
                    if (zero != -1)
                        zero++;
                    if (van == 1) {
                        sb.deleteCharAt(i + 1);
                        van = 0;
                    }
                }
                break;
                default:
                    zero = 0;
                    van = 0;
            }
        }

        if (sb.charAt(0) == CHINA_NUMERIC[1] && sb.charAt(1) == CHINA_NUMERIC_POSITION[0])
            sb.deleteCharAt(0);

        return list.append(sb);
    }

    /**
     * 快速排序
     * <br>
     * 在平均状况下，排序n个项目要O(n * log (n))次比较。在最坏状况下则需要O(n^2) 次比较
     */
    @Nonnull
    public static int[] quickShort(@Nonnull final int[] array) {
        quickSort0(array, 0, array.length - 1);
        return array;
    }

    public static void quickSort0(@Nonnull int[] array, int left, int right) {
        if (left < right) {
            int i = left;
            int j = right;
            int pivot = array[left];
            while (i < j) {
                while (i < j && array[j] >= pivot) {
                    j--;
                }
                if (i < j) {
                    array[i] = array[j];
                    i++;
                }
                while (i < j && array[i] < pivot) {
                    i++;
                }
                if (i < j) {
                    array[j] = array[i];
                    j--;
                }
            }
            array[i] = pivot;

            //递归排序
            quickSort0(array, left, i - 1);
            quickSort0(array, i + 1, right);
        }
    }

    /**
     * Returns an iterator providing a number sequence. This sequence starts with <i>{@code from}</i> (given as
     * parameter) and ends with <i>{@code to}</i> (given as parameter). In between, new values are calculated as
     * sigmoid function e^t(x) / (1 + e^t(x)).
     *
     * @param from  First number in the sequence
     * @param to    Last number in the sequence
     * @param steps The length of the sequence (exclusive <i>{@code from}</i> and <i>{@code to}</i>)
     * @return an iterator providing a number sequence calculated as sigmoid function.
     * {@link Iterator#hasNext() hasNext()} returns false, if the sequence has finished, but
     * {@link Iterator#next() next()} will return <i>{@code to}</i>
     * @throws IllegalArgumentException if <i>{@code from}</i> {@code >=} <i>{@code to}</i>
     * @throws IllegalArgumentException if <i>{@code from}</i> {@code <} <i>{@code 0}</i>
     * @throws IllegalArgumentException if <i>{@code steps}</i> {@code <} <i>{@code 0}</i>
     */
    public static PrimitiveIterator.OfInt createSigmoidSequence(int from, int to, int steps) {

        if (from >= to)
            throw new IllegalArgumentException("from >= to");
        if (from < 0)
            throw new IllegalArgumentException("from < 0");
        if (steps < 0)
            throw new IllegalArgumentException("steps < 0");

        return new PrimitiveIterator.OfInt() {
            // e^t(x) / (1 + e^t(x)) in [0, xmax] for t(x) = 8 * x / xmax - 4

            private int step = 0;
            private final int delta = to - from;
            private final int maxStep = steps + 1;

            @Override
            public boolean hasNext() {
                return step <= maxStep;
            }

            @Override
            public int nextInt() {
                if (step > maxStep)
                    throw new NoSuchElementException();
                int s = step++;

                if (s == maxStep)
                    return to;
                else if (s == 0)
                    return from;
                else {
                    double x = s / (double) maxStep;
                    double tmp = Math.exp(8 * x - 4);
                    return (int) (delta * (tmp / (1 + tmp)) + from);
                }
            }


            // result for (from, to, steps) = (0, 20, 18)

            // |20|  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  | x|
            // |19|  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  | x| x|  |
            // |18|  |  |  |  |  |  |  |  |  |  |  |  |  |  |  | x| x|  |  |  |
            // |17|  |  |  |  |  |  |  |  |  |  |  |  |  |  | x|  |  |  |  |  |
            // |16|  |  |  |  |  |  |  |  |  |  |  |  |  | x|  |  |  |  |  |  |
            // |15|  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
            // |14|  |  |  |  |  |  |  |  |  |  |  |  | x|  |  |  |  |  |  |  |
            // |13|  |  |  |  |  |  |  |  |  |  |  | x|  |  |  |  |  |  |  |  |
            // |12|  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
            // |11|  |  |  |  |  |  |  |  |  |  | x|  |  |  |  |  |  |  |  |  |
            // |10|  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
            // | 9|  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
            // | 8|  |  |  |  |  |  |  |  |  | x|  |  |  |  |  |  |  |  |  |  |
            // | 7|  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
            // | 6|  |  |  |  |  |  |  |  | x|  |  |  |  |  |  |  |  |  |  |  |
            // | 5|  |  |  |  |  |  |  | x|  |  |  |  |  |  |  |  |  |  |  |  |
            // | 4|  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
            // | 3|  |  |  |  |  |  | x|  |  |  |  |  |  |  |  |  |  |  |  |  |
            // | 2|  |  |  |  |  | x|  |  |  |  |  |  |  |  |  |  |  |  |  |  |
            // | 1|  |  |  | x| x|  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
            // | 0| x| x| x|  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |  |
            //    | 0| 1| 2| 3| 4| 5| 6| 7| 8| 9|10|11|12|13|14|15|16|17|18|19|
        };
    }

    public static int getMin2PowerOf(int x) {
        if(x >= 1073741824) return 1073741824;
        x--;
        x |= x >>> 1;
        x |= x >>> 2;
        x |= x >>> 4;
        x |= x >>> 8;
        x |= x >>> 16;
        return (x < 0) ? 1 : x + 1;
    }

    public static double cos(double value) {
        return sin(HALF_PI + value);
    }

    public static float cos(float value) {
        return (float) sin(HALF_PI + value);
    }

    public static float sin(float value) {
        return (float) sin((double) value);
    }

    /**
     * 使用切比雪夫多项式快速计算sin
     * <br>
     * 精度1e-6
     *
     * @param value radian
     * @return sin value
     */
    public static double sin(double value) {
        if (value >= 0) {
            if(value <= HALF_PI) {
                return ((((((-0.000960664 * value + 0.0102697866) * value - 0.00198601997) * value - 0.1656067221) * value - 0.0002715666) * value + 1.000026227) * value);
            } else {
                if (value >= TWO_PI) {
                    value %= TWO_PI;
                }

                if (value >= Math.PI) {
                    return -sin(value - Math.PI);
                }

                if (value > HALF_PI) {
                    value = sin(value - HALF_PI);
                    return sqrt(1 - value * value);
                }

                return sin(value);
            }
        } else {
            return -sin(-value);
        }
    }

    public static int boolArr2int(boolean[] array) {
        int result = 0;
        for (int i = 0; i < array.length; i++) {
            if (array[i])
                result |= 1 << i;
        }
        return result;
    }

    public static int floor(float value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    public static int ceil(float value) {
        int i = (int) value;
        return value > i ? i + 1 : i;
    }

    public static int floor(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    /*public static float invSqrt(float x) {
        float halfX = 0.5f * x;

        int i = Float.floatToRawIntBits(x); // get bits for floating VALUE
        i = 0x5f375a86 - (i >> 1); // gives initial guess y0
        x = Float.intBitsToFloat(i); // convert bits BACK to float

        x = x * (1.5f - halfX * x * x); // Newton step, repeating increases accuracy
        x = x * (1.5f - halfX * x * x);
        x = x * (1.5f - halfX * x * x);

        return x;
    }

    public static double invSqrtd(double x) {
        double halfX = 0.5f * x;

        long i = Double.doubleToRawLongBits(x);
        i = 6910469410427058090L - (i >> 1);
        x = Double.longBitsToDouble(i);

        x = x * (1.5f - halfX * x * x);
        x = x * (1.5f - halfX * x * x);
        x = x * (1.5f - halfX * x * x);
        x = x * (1.5f - halfX * x * x);

        return x;
    }*/

    public static double sqrt(double x) {
        return Math.sqrt(x);
        //if (x < 0) throw new IllegalArgumentException("Must be non-negative");
        //if (x == 0) return 0;
        //return 1 / invSqrtd(x);//(y + x / y) / 2;
    }

    public static float sqrt(float x) {
        return (float) Math.sqrt(x);
        //if (x < 0) throw new IllegalArgumentException("Must be non-negative");
        //if (x == 0) return 0;
        //return 1 / invSqrt(x);//(y + x / y) / 2;
    }

    public static double log10(double x) {
        return Math.log10(x);
        //return logE(x) / LN_10;
    }

    public static double logE(double x) {
        return Math.log(x);
        //if (x <= 0) throw new IllegalArgumentException("Must be positive");
        //if (x == 1) return 0;
        //int k = 0;
        //for (; x > 0.1; k++) x /= 10;
        //for (; x <= 0.01; k--) x *= 10;
        //return k * LN_10 - logNeg(x);
    }

    /*private static double logNeg(double q) { // q in ( 0.01, 0.1 ]
        double r = q, s = q, n = q, q2 = q * q, q1 = q2 * q;
        for (boolean p = true; (n *= q1) > EPS_1; s += n, q1 *= q2)
            r += (p = !p) ? n : -n;
        double u = 1 - 2 * r,
                v = 1 + 2 * s,
                t = u / v;
        double a = 1,
                b = sqrt(1 - t * t * t * t);
        for (; a - b > EPS_2; b = sqrt(a * b), a = t)
            t = (a + b) / 2;
        return TWO_PI / (a + b) / v / v;
    }*/

    public static int average(int[] values) {
        if (values == null || values.length == 0)
            return 0;
        int sum = 0;
        for (int v : values)
            sum += v;
        return sum / values.length;
    }

    public static long average(long[] values) {
        if (values == null || values.length == 0)
            return 0L;
        long sum = 0L;
        for (long v : values)
            sum += v;
        return sum / values.length;
    }

    /**
     * 点在直线上的投影
     *
     * @param a     直线上的点a
     * @param b     直线上的点b
     * @param point 直线外的点p
     * @return 投影点
     */
    public static Vec3d pointProject(Vec3d a, Vec3d b, Vec3d point) {
        //return Vec3d.project(point, Vec3d.sub(b, a).normalize());

        double x1 = a.x;
        double y1 = a.y;
        double z1 = a.z;

        double x2 = b.x;
        double y2 = b.y;
        double z2 = b.z;

        double dx = b.x - a.x;
        double dy = b.y - a.x;
        double dz = b.z - a.x;

        double px = point.x;
        double py = point.y;
        double pz = point.z;

        if(b.equals(a))
            return point;

        Vec3d diff = Vec3d.sub(b, a); // b - a
        double denominator = diff.len2();
        //if (denominator == 0)
        //    return point;

        double dxyz = dx * dy * dz;

        double moleculex = dx * dx * px   +   dxyz * py * pz   +   dz * dy * (x1 * y2 * z2   -   x2 * y1 * z1);
        double moleculey = dy * dy * py   +   dxyz * px * pz   +   dx * dz * (x2 * y1 * z2   -   x1 * y2 * z1);
        double moleculez = dz * dz * pz   +   dxyz * px * py   +   dx * dy * (x2 * y2 * z1   -   x1 * y1 * z2);

        return new Vec3d(moleculex, moleculey, moleculez).mul(1 / denominator);
    }

    /**
     * @param pitch 仰角
     * @param yaw   旋转角
     */
    public static Vec3f rotationVector(float pitch, float yaw) {
        float f = cos(-yaw * 0.017453292F - 3.1415927F);
        float f1 = sin(-yaw * 0.017453292F - 3.1415927F);
        float f2 = -cos(-pitch * 0.017453292F);
        float f3 = sin(-pitch * 0.017453292F);
        return new Vec3f((f1 * f2), f3, (f * f2));
    }

    /**
     * @param pitch 仰角
     * @param yaw   旋转角
     */
    public static Vec3d rotationVectord(double pitch, double yaw) {
        double f = cos(-yaw * P5_DEG_MUL - Math.PI);
        double f1 = sin(-yaw * P5_DEG_MUL - Math.PI);
        double f2 = -cos(-pitch * P5_DEG_MUL);
        double f3 = sin(-pitch * P5_DEG_MUL);
        return new Vec3d((f1 * f2), f3, (f * f2));
    }

    /**
     * 开方算法测试版
     */
    @Deprecated
    public static double slowSqrt(double x) {
        double a = x / 2, da;
        do {
            double b = x / a;
            da = a;
            a = (a + b) / 2;
        } while (Math.abs(a - da) > 10E-6);

        return a;
    }

    public static double[] pdf2cdf(double[] pdf) {
        double[] cdf = Arrays.copyOf(pdf, pdf.length);

        for (int i = 1; i < cdf.length - 1; i++)
            cdf[i] += cdf[i - 1];

        // Force set last cdf to 1, preventing floating-point summing error in the loop.
        cdf[cdf.length - 1] = 1;

        return cdf;
    }

    public static int cdfRandom(Random rand, double[] targetCdf) {
        double x = rand.nextDouble();

        for (int i = 0; i < targetCdf.length; i++) {
            if (x < targetCdf[i])
                return i;
        }
        throw new IllegalArgumentException("targetCdf");
    }

    public static float rad(float angle) {
        return angle * (float) Math.PI / 180;
    }

    public static int randomRange(Random rand, int min, int max) {
        return min + rand.nextInt(max - min + 1);
    }

    public static int parseInt(CharSequence s) throws NumberFormatException {
        return parseInt(false, s, 10);
    }

    public static int parseInt(boolean negative, CharSequence s, int radix) throws NumberFormatException {
        /*int*/
        long result = 0;

        if (s.length() > 0) {
            int i = negative ? 1 : 0, len = s.length();

            byte[] c;
            switch (radix) {
                case 16:
                    c = HEX_MAXS;
                    break;
                case 8:
                    c = OCT_MAXS;
                    break;
                case 10:
                    c = TextUtil.INT_MAXS;
                    break;
                case 2:
                    c = BIN_MAXS;
                    break;
                default:
                    throw new NumberFormatException("Unsupported radix " + radix);
            }
            if (!TextUtil.checkInt(c, s, 0, negative || radix != 10)) {
                throw new NumberFormatException("checkInt() failed : " + s);
            }

            int digit;

            //int prev = 0;

            while (i < len) {
                if ((digit = Character.digit(s.charAt(i++), radix)) < 0)
                    throw new NumberFormatException("Not a number at offset " + (i - 1) + " : " + s);

                result *= radix;
                result += digit;

                /*if (result < prev) {
                    throw new NumberFormatException("Value overflow " + result + " : " + s);
                }

                prev = result;*/
            }
        } else {
            throw new NumberFormatException(s.toString());
        }

        //System.out.println("Result = " + (negative ? -result : result));

        result = negative ? -result : result;

        if (result > 4294967295L || result < Integer.MIN_VALUE)
            throw new NumberFormatException("Value overflow " + result + " : " + s);

        return (int) result;
    }
}