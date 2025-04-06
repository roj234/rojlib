package roj.math;

public class Vec4d extends Vector {
	public double x, y, z, s = 1;

	public Vec4d() {}
	@Override
	public Vector copy() { return new Vec4d(this); }

	public Vec4d(double x, double y, double z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}
	public Vec4d(double x, double y, double z, double s) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.s = s;
	}
	public Vec4d(Vec2d xy, double z, double s) {this(xy.x, xy.y, z, s);}
	public Vec4d(Vec3d xyz, double s) {this(xyz.x, xyz.y, xyz.z, s);}
	public Vec4d(Vec4d xyzw) {this(xyzw.x, xyzw.y, xyzw.z, xyzw.s);}

	public Vec4d set(double x, double y, double z, double w) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.s = w;
		return this;
	}
	public Vec4d set(Vec3d xyz, double w) {
		this.x = xyz.x;
		this.y = xyz.y;
		this.z = xyz.z;
		this.s = w;
		return this;
	}
	public Vec4d set(Vec4d xyzw) {
		this.x = xyzw.x;
		this.y = xyzw.y;
		this.z = xyzw.z;
		this.s = xyzw.s;
		return this;
	}
    public Vec4d set(Vector xyzw) {
        this.x = xyzw.x();
        this.y = xyzw.y();
        this.z = xyzw.z();
        this.s = xyzw.w();
        return this;
    }

	// q*
	public Vec4d star() {
		x = -x;
		y = -y;
		z = -z;
		return this;
	}

	private static void cross(Vec4d a, Vec4d b, Vec4d c) {
		double x1 = a.s*b.x + a.x*b.s + a.y*b.z - a.z*b.y;
		double y1 = a.s*b.y + a.y*b.s + a.z*b.x - a.x*b.z;
		double z1 = a.s*b.z + a.z*b.s + a.x*b.y - a.y*b.x;
		c.s = a.s*b.s - a.x*b.x - a.y*b.y - a.z*b.z;
		c.x = x1;
		c.y = y1;
		c.z = z1;
	}

	// q^-1 = q* / |q|^2
	public Vec4d inverse() {return star().normalize();}

	/**
	 * 对自身应用旋转.
	 * PS: 虽然旋转向量看起来不变，经过了两次取反，并且一定会变成单位向量
	 * @param rot 旋转向量
	 * @return 自身
	 */
	public Vec4d applyRotation(Vec4d rot) {
		cross(rot, this, this);
		cross(rot.inverse());
		rot.star();
		return this;
	}

	/**
	 * 将该向量设置为一个旋转向量
	 * @param angle 旋转的弧度
	 * @param axis 旋转轴
	 * @return 自身
	 */
	public Vec4d makeRotation(double angle, Vector axis) {
		angle /= 2;
		s = Math.cos(angle);
		double sin = Math.sin(angle);
		double len = axis.lengthSquared();
		x = sin*axis.x()/len;
		y = sin*axis.y()/len;
		z = sin*axis.z()/len;
		return this;
	}

	public double x() {return x;}
	public double y() {return y;}
	public double z() {return z;}
	public double w() {return s;}
	public void x(double x) {this.x = x;}
	public void y(double y) {this.y = y;}
	public void z(double z) {this.z = z;}
	public void w(double w) {this.s = w;}
	public int axis() {return 4;}

    public final Vec4d add(Vector v) {
        x += v.x();
        y += v.y();
        z += v.z();
        s += v.w();
        return this;
    }
    public final Vec4d add(double x, double y, double z, double w) {
        this.x += x;
        this.y += y;
        this.z += z;
        this.s += w;
        return this;
    }
    public final Vec4d sub(Vector v) {
        x -= v.x();
        y -= v.y();
        z -= v.z();
        s -= v.w();
        return this;
    }
    public final Vec4d mul(Vector v) {
        x *= v.x();
        y *= v.y();
        z *= v.z();
        s *= v.w();
        return this;
    }
    public final Vec4d mul(double scalar) {
        x *= scalar;
        y *= scalar;
        z *= scalar;
        s *= scalar;
        return this;
    }

    public final double lengthSquared() {return x*x+y*y+z*z+s*s;}
    public final double distanceSq(Vector v) {
        double t,d;
        t = v.x()-x;
        d = t * t;
        t = v.y()-y;
        d += t*t;
        t = v.z()-z;
        d += t*t;
        t = v.w()-s;
        d += t*t;
        return d;
    }

    public final Vec4d normalize() {
        var len = 1/lengthSquared();
        x *= len;
        y *= len;
        z *= len;
        s *= len;
        return this;
    }

    public final double dot(Vector v) {return x*v.x()+y*v.y()+z*v.z()+s*v.w();}
	public final Vec4d cross(Vector b) {
		cross(this, (Vec4d) b, this);
		return this;
	}
}