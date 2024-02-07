package roj.math;

import java.util.List;

import static roj.math.MathUtils.EPS_2;

/**
 * @author Roj233
 * @since 2022/5/14 16:57
 */
public class PolygonUtil {
	public static double area(List<? extends Vector> points) {
		int N = points.size();
		if (N < 3) return 0;

		double area = points.get(0).y() * (points.get(N - 1).x() - points.get(1).x());
		for (int i = 1; i < N; ++i)
			area += points.get(i).y() * (points.get(i - 1).x() - points.get((i + 1) % N).x());

		return Math.abs(area / 2);
	}

	/**
	 * "双倍多边形面积" <br>
	 * 实际用途：通过符号可以判断多边形的顶点排列顺序
	 */
	public static double signedDoubleArea(List<? extends Vector> points) {
		if (points.size() < 3) return 0;
		double area = 0;

		for (int i = 0; i < points.size(); ) {
			Vector point = points.get(i);
			Vector next = points.get(++i % points.size());

			area += point.x() * next.y() - next.x() * point.y();
		}

		return area;
	}

	/**
	 * 判断多边形的顶点排列顺序
	 * <br> -1: 逆时针, 1: 顺时针, 0: 未知
	 */
	public static int winding(List<? extends Vector> points) {
		double d = signedDoubleArea(points);
		if (d < 0) {return -1;} else if (d > 0) return 1;
		return 0;
	}

	/**
	 * 判断点是否在折线上
	 */
	public static boolean isOnBorder(Vector p, List<? extends Vector> points) {
		//判断点是否在线段上，设点为Q，线段为P1P2 ，
		//判断点Q在该线段上的依据是：( Q - P1 ) × ( P2 - P1 ) = 0，且 Q 在以 P1，P2为对角顶点的矩形内

		int N = points.size() - 1;
		for (int i = 0; i < N; i++) {
			Vector a = points.get(i);
			Vector b = points.get(i + 1);

			// 判断点是否在线段的外包矩形内
			if (p.y() >= Math.min(a.y(), b.y()) && p.y() <= Math.max(a.y(), b.y()) && p.x() >= Math.min(a.x(), b.x()) && p.x() <= Math.max(a.x(), b.x())) {
				//判断点是否在直线上公式
				double precision = (a.y() - p.y()) * (b.x() - p.x()) - (b.y() - p.y()) * (a.x() - p.x());
				if (Math.abs(precision) < EPS_2) return true;
			}
		}

		return false;
	}

	/**
	 * 判断点是否多边形内
	 *
	 * @param canOn 点位于多边形的顶点或边上，也算做点在多边形内吗
	 */
	public static boolean inPolygon(Vec2d point, Polygon polygon, boolean canOn) {
		Rect2d bounds = polygon.getBounds();
		if (bounds.contains(point)) {
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
		for (int i = 1; i <= N; ++i) {//check all rays
			if (point.equals(p1)) {
				return canOn;//p is an vertex
			}

			p2 = pts.get(i % N);//right vertex
			if (point.x < Math.min(p1.x, p2.x) || point.x > Math.max(p1.x, p2.x)) {//ray is outside of our interests
				p1 = p2;
				continue;//next ray left point
			}

			if (point.x > Math.min(p1.x, p2.x) && point.x < Math.max(p1.x, p2.x)) {//ray is crossing over by the algorithm (common part of)
				if (point.y <= Math.max(p1.y, p2.y)) {//x is before of ray
					if (p1.x == p2.x && point.y >= Math.min(p1.y, p2.y)) {//overlies on a horizontal ray
						return canOn;
					}

					if (p1.y == p2.y) {//ray is vertical
						if (p1.y == point.y) {//overlies on a vertical ray
							return canOn;
						} else {//before ray
							++intersects;
						}
					} else {//cross point on the left side
						double xinters = (point.x - p1.x) * (p2.y - p1.y) / (p2.x - p1.x) + p1.y;//cross point of y
						if (Math.abs(point.y - xinters) < EPS_2) {//overlies on a ray
							return canOn;
						}

						if (point.y < xinters) {//before ray
							++intersects;
						}
					}
				}
			} else {//special case when ray is crossing through the vertex
				if (point.x == p2.x && point.y <= p2.y) {//p crossing over p2
					Vec2d p3 = pts.get((i + 1) % N); //next vertex
					if (point.x >= Math.min(p1.x, p3.x) && point.x <= Math.max(p1.x, p3.x)) {//p.x lies between p1.x & p3.x
						++intersects;
					} else {
						intersects += 2;
					}
				}
			}
			p1 = p2;//next ray left point
		}

		//偶数在多边形外, 奇数在多边形内
		return (intersects & 1) != 0;
	}

	public static boolean inPolygon2(Vector p, List<? extends Vector> points) {
		if (points.size() < 3) return false;

		Vec2d tmp1 = new Vec2d();
		Vec2d tmp2 = new Vec2d();

		double sum = 0;
		for (int i = 0; i < points.size()-1; i++) {
			Vector a = points.get(i);
			Vector b = points.get(i + 1);

			double angle = Math.acos(tmp1.set(a.x()-p.x(), a.y()-p.y()).angle(tmp2.set(b.x()-p.x(), b.y()-p.y())));

			if (tmp1.cross2(tmp2) > 0) sum += angle;
			else sum -= angle;
		}

		return Math.abs(sum) > Math.PI;
	}

	public static boolean isConvex(List<? extends Vector> points) {
		if (points.size() < 3) return false;

		Vec2d tmp1 = new Vec2d();
		Vec2d tmp2 = new Vec2d();

		Vector a = points.get(0);
		Vector b = points.get(1);
		Vector c = points.get(2);

		boolean state = tmp1.set(b.x()-a.x(), b.y()-a.y()).cross2(tmp2.set(c.x()-a.x(), c.y()-a.y())) > 0;
		for (int i = 1; i < points.size()-1; i++) {
			a = points.get(i);
			b = points.get(i + 1);
			c = points.get(i + 2);

			if (tmp1.set(b.x()-a.x(), b.y()-a.y()).cross2(tmp2.set(c.x()-a.x(), c.y()-a.y())) > 0 != state) {
				return false;
			}
		}

		return true;
	}

	/**
	 * 计算折线或者点数组的长度
	 */
	public static double length(List<? extends Vector> points) {
		int N = points.size()-1;
		if (N < 1) return 0;

		double len = 0;
		for (int i = 0; i < N; i++) {
			Vector a = points.get(i);
			Vector b = points.get(i + 1);

			double x = a.x()-b.x();
			double y = a.y()-b.y();
			len += Math.sqrt(x * x + y * y);
		}

		return len;
	}
}
