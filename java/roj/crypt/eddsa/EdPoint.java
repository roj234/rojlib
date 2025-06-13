package roj.crypt.eddsa;

import roj.collect.ArrayList;

import java.io.Serializable;
import java.util.Arrays;

import static roj.crypt.eddsa.EdInteger.ZERO;

final class EdPoint implements Serializable {
	public static class Format {
		static final byte P2 = 0, P3 = 1, P1P1 = 2, PRECOMP = 3, CACHED = 4;
	}

	static final ThreadLocal<TmpNum> NUMS = ThreadLocal.withInitial(TmpNum::new);
	static final class TmpNum {
		final EdInteger a = ZERO.mutable(), b = ZERO.mutable(), c = ZERO.mutable(), d = ZERO.mutable(), invert_safe = ZERO.mutable();
		final EdPoint pa = new EdPoint(), pb = pa.mutable(), pc = pa.mutable();

		final byte[] ba = new byte[256], bb = new byte[256];

		final ArrayList<EdInteger> tmp1 = new ArrayList<>(10);
		void reserve(EdInteger t) {if (tmp1.size() < 10) tmp1.add(t);}
	}

	private EdCurve curve;
	private byte format;
	private final EdInteger X, Y;
	private EdInteger Z, T;
	// P3 state
	private EdPoint[] singleTab, doubleTab;
	private boolean mutable;

	public static EdPoint p2(EdCurve curve, EdInteger X, EdInteger Y, EdInteger Z) { return new EdPoint(curve, Format.P2, X, Y, Z, null); }
	public static EdPoint p3(EdCurve curve, EdInteger X, EdInteger Y, EdInteger Z, EdInteger T) { return new EdPoint(curve, Format.P3, X, Y, Z, T); }
	public static EdPoint p1p1(EdCurve curve, EdInteger X, EdInteger Y, EdInteger Z, EdInteger T) { return new EdPoint(curve, Format.P1P1, X, Y, Z, T); }
	public static EdPoint precomp(EdCurve curve, EdInteger ypx, EdInteger ymx, EdInteger xy2d) { return new EdPoint(curve, Format.PRECOMP, ypx, ymx, xy2d, null); }
	public static EdPoint cached(EdCurve curve, EdInteger YpX, EdInteger YmX, EdInteger Z, EdInteger T2d) { return new EdPoint(curve, Format.CACHED, YpX, YmX, Z, T2d); }

	public EdPoint(EdCurve curve, byte format, EdInteger X, EdInteger Y, EdInteger Z, EdInteger T) {
		this.curve = curve;
		this.format = format;
		this.X = X.lock();
		this.Y = Y.lock();
		this.Z = Z.lock();
		this.T = T == null ? null : T.lock();
	}

	public EdPoint(EdCurve curve, byte[] s) { this(curve, s, false); }
	public EdPoint(EdCurve curve, byte[] s, boolean precomputeTable) {
		EdInteger y = EdInteger.fromBytes(s);
		EdInteger yy = y.square();
		EdInteger u = yy.sub1();
		EdInteger v = yy.mutable().mul(curve.D).add1();
		EdInteger v3 = v.mutable().square().mul(v);
		EdInteger x = v3.mutable().square().mul(v).mul(u);
		x = x.pow22523();
		x = v3.mul(u).mul(x);
		EdInteger vxx = x.mutable().square().mul(v);
		EdInteger check = vxx.mutable().sub(u);
		if (check.isNonZero()) {
			check = vxx.add(u);
			if (check.isNonZero()) throw new IllegalArgumentException("invalid point");

			x.mul(curve.I);
		}
		if ((x.isNegative() ? 1 : 0) != ((s[31] >> 7) & 1)) x.neg();

		this.curve = curve;
		this.format = Format.P3;
		this.X = x.lock();
		this.Y = y;
		this.Z = EdInteger.ONE;
		this.T = x.mul(y);

		if (precomputeTable) {
			singleTab = computeSingleTable();
			doubleTab = computeDoubleTable();
		}
	}

	private EdPoint() {
		mutable = true;
		X = ZERO.mutable();
		Y = ZERO.mutable();
		Z = ZERO.mutable();
		T = ZERO.mutable();
	}

	public byte[] toByteArray() {
		TmpNum tt = NUMS.get();
		switch (format) {
			case Format.P2: case Format.P3: {
				EdInteger recip = tt.invert_safe.set(Z).invert();
				EdInteger x = tt.b.set(X).mul(recip);
				boolean flagneg = x.isNegative();

				byte[] s = x.set(Y).mul(recip).toByteArray();
				if (flagneg) s[31] |= 0x80;
				return s;
			}
		}

		return tt.pa.set(this).toP2().toByteArray();
	}

	public byte[] getU() {
		TmpNum tt = NUMS.get();
		switch (format) {
			case Format.P2: case Format.P3: {
				// 计算 u = (1 + y) / (1 - y)
				EdInteger num = EdInteger.ONE.add(Y);
				EdInteger den = EdInteger.ONE.sub(Y);
				EdInteger u = num.mul(den.invert());
				return u.toByteArray();
			}
		}

		return tt.pa.set(this).toP2().toByteArray();
	}

	public EdPoint toP2() { return toRep(Format.P2); }
	public EdPoint toP3() { return toRep(Format.P3); }
	public EdPoint toCached() { return toRep(Format.CACHED); }

	private EdPoint toRep(byte toRep) {
		if (format == toRep) return this;
		switch (format) {
			case Format.P3:
				switch (toRep) {
					case Format.P2:
						if (mutable) {
							format = Format.P2;
							NUMS.get().reserve(T);
							T = null;
							return this;
						}
						return p2(curve, X, Y, Z);
					case Format.CACHED:
						if (mutable) {
							format = Format.CACHED;
							EdInteger x = NUMS.get().a.set(Y).add(X);
							Y.sub(X);
							X.set(x);
							T.mul(curve.twoD);
							return this;
						}
						return cached(curve, Y.add(X), Y.sub(X), Z, T.mul(curve.twoD));
				}
				break;
			case Format.P1P1:
				switch (toRep) {
					case Format.P2:
						if (mutable) {
							format = Format.P2;
							X.mul(T);
							Y.mul(Z);
							Z.mul(T);
							NUMS.get().reserve(T);
							T = null;
							return this;
						}
						return p2(curve, X.mul(T), Y.mul(Z), Z.mul(T));
					case Format.P3:
						if (mutable) {
							format = Format.P3;
							EdInteger t = NUMS.get().a.set(X).mul(Y);
							X.mul(T);
							Y.mul(Z);
							Z.mul(T);
							T.set(t);
							return this;
						}
						return p3(curve, X.mul(T), Y.mul(Z), Z.mul(T), X.mul(Y));
				}
				break;
		}
		throw new UnsupportedOperationException();
	}

	private EdPoint[] computeSingleTable() {
		if (format != Format.P3) throw new IllegalArgumentException();
		EdPoint[] preval = new EdPoint[32*8];

		TmpNum tt = NUMS.get();

		EdPoint Bi = tt.pa.set(this);
		EdPoint Bij = tt.pb.set(this);
		EdPoint cachedBi = tt.pc.set(this);

		EdInteger x = tt.a;
		EdInteger y = tt.b;
		EdInteger recip = tt.invert_safe;

		for (int i = 0; i < 32; i++) {
			Bij.set(Bi);
			cachedBi.set(Bi).toCached();

			for (int j = 0; j < 8; j++) {
				recip.set(Bij.Z).invert();
				x.set(Bij.X).mul(recip);
				y.set(Bij.Y).mul(recip);

				preval[(i<<3) + j] = precomp(curve, y.mutable().add(x), y.mutable().sub(x), x.mutable().mul(y).mul(curve.twoD));

				Bij.add(cachedBi).toP3();
			}

			for (int j = 0; j < 8; j++) {
				Bi.add(cachedBi.set(Bi).toCached()).toP3();
			}
		}
		return preval;
	}
	private EdPoint[] computeDoubleTable() {
		if (format != Format.P3) throw new IllegalArgumentException();
		EdPoint[] preval2 = new EdPoint[8];

		TmpNum tt = NUMS.get();

		EdPoint Bi = tt.pa.set(this);
		EdPoint tmp = tt.pb.set(this);

		EdInteger x = tt.a;
		EdInteger y = tt.b;
		EdInteger recip = tt.invert_safe;

		for (int i = 0; i < 8; ++i) {
			recip.set(Bi.Z).invert();
			x.set(Bi.X).mul(recip);
			y.set(Bi.Y).mul(recip);

			preval2[i] = precomp(curve, y.mutable().add(x), y.mutable().sub(x), x.mutable().mul(y).mul(curve.twoD));

			tmp.set(this).add(Bi.toCached()).toP3().toCached();
			Bi.set(this).add(tmp).toP3();
		}
		return preval2;
	}

	public EdPoint dbl() {
		switch (format) {
			case Format.P2: case Format.P3:
				TmpNum tt = NUMS.get();

				EdInteger XX = tt.a.set(X).square();
				if (mutable) {
					X.add(Y).square();
					Y.square();
					EdInteger Zn;
					if (T != null) Zn = T.set(Y);
					else {
						Zn = tt.tmp1.pop();
						if (Zn == null) Zn = Y.mutable();
						else Zn.set(Y);
					}
					Zn.sub(XX);

					Y.add(XX);

					T = Z.squareAndDouble().sub(Zn);

					X.sub(Y);
					Z = Zn;

					format = Format.P1P1;
					return this;
				} else {
					EdInteger YY = Y.mutable().square();
					EdInteger AA = X.mutable().add(Y).square();
					EdInteger Yn = YY.mutable().add(XX);
					EdInteger Zn = YY.sub(XX);

					return p1p1(curve, AA.sub(Yn), Yn, Zn, Z.mutable().squareAndDouble().sub(Zn));
				}
		}
		throw new UnsupportedOperationException();
	}

	private EdPoint madd(EdPoint q) {
		if (format != Format.P3) throw new UnsupportedOperationException();
		if (q.format != Format.PRECOMP) throw new IllegalArgumentException();
		if (!mutable) throw new IllegalStateException();

		TmpNum tt = NUMS.get();

		EdInteger C = tt.a.set(q.Z).mul(T);
		EdInteger D = Z.add(Z);
		T.set(D).sub(C);
		D.add(C);

		EdInteger A = tt.a.set(Y).add(X).mul(q.X);
		EdInteger B = tt.b.set(Y).sub(X).mul(q.Y);

		X.set(A).sub(B);
		Y.set(A).add(B);

		format = Format.P1P1;
		return this;
	}
	private EdPoint msub(EdPoint q) {
		if (format != Format.P3) throw new UnsupportedOperationException();
		if (q.format != Format.PRECOMP) throw new IllegalArgumentException();
		if (!mutable) throw new IllegalStateException();

		TmpNum tt = NUMS.get();

		EdInteger C = tt.a.set(q.Z).mul(T);
		EdInteger D = Z.add(Z);
		T.set(D).add(C);
		D.sub(C);

		EdInteger A = tt.a.set(Y).add(X).mul(q.Y);
		EdInteger B = tt.b.set(Y).sub(X).mul(q.X);

		X.set(A).sub(B);
		Y.set(A).add(B);

		format = Format.P1P1;
		return this;
	}

	public EdPoint add(EdPoint q) {
		if (format != Format.P3) throw new UnsupportedOperationException();
		if (q.format != Format.CACHED) throw new IllegalArgumentException();

		TmpNum tt = NUMS.get();

		EdInteger A = tt.a.set(Y).add(X).mul(q.X);
		EdInteger B = tt.b.set(Y).sub(X).mul(q.Y);

		if (mutable) {
			X.set(A).sub(B);
			Y.set(A).add(B);

			EdInteger C = tt.a.set(q.T).mul(T);
			EdInteger D = Z.mul(q.Z);
			D.add(D);

			T.set(D).sub(C);
			D.add(C);

			format = Format.P1P1;
			return this;
		}

		A = A.mutable().sub(B);
		B = tt.a.mutable().add(B);

		EdInteger C = tt.a.set(q.T).mul(T);
		EdInteger D = Z.mutable().mul(q.Z);
		D.add(D);

		return p1p1(curve, A, B, D.mutable().add(C), D.sub(C));
	}
	public EdPoint sub(EdPoint q) {
		if (format != Format.P3) throw new UnsupportedOperationException();
		if (q.format != Format.CACHED) throw new IllegalArgumentException();

		TmpNum tt = NUMS.get();

		EdInteger A = tt.a.set(Y).add(X).mul(q.Y);
		EdInteger B = tt.b.set(Y).sub(X).mul(q.X);

		if (mutable) {
			X.set(A).sub(B);
			Y.set(A).add(B);

			EdInteger C = tt.a.set(q.T).mul(T);
			EdInteger D = Z.mul(q.Z);
			D.add(D);

			T.set(D).add(C);
			D.sub(C);

			format = Format.P1P1;
			return this;
		}

		A = A.mutable().sub(B);
		B = tt.a.mutable().add(B);

		EdInteger C = tt.a.set(q.T).mul(T);
		EdInteger D = Z.mutable().mul(q.Z);
		D.add(D);

		return p1p1(curve, A, B, D.mutable().sub(C), D.add(C));
	}

	public EdPoint negate() {
		if (format != Format.P3) throw new UnsupportedOperationException();
		return curve.P3_ZERO.sub(NUMS.get().pa.set(this).toCached()).toP3().lock();
	}

	private EdPoint select(int pos, int b, EdPoint _t) {
		// constant time
		int flagneg = (b >> 8) & 1;
		int abs = b - ((-flagneg & b) << 1);

		// 'precomp' zero
		EdInteger x = _t.X.set(EdInteger.ONE);
		EdInteger y = _t.Y.set(EdInteger.ONE);
		EdInteger z = _t.Z.set(ZERO);

		pos <<= 3;
		for (int i = 0; i < 8;) {
			EdPoint p = singleTab[pos + i++];
			int is = abs == i ? 1 : 0;

			x.cmov(p.X, is);
			y.cmov(p.Y, is);
			z.cmov(p.Z, is);
		}

		TmpNum tt = NUMS.get();

		tt.a.set(x);
		tt.b.set(z);

		x.cmov(y, flagneg);
		y.cmov(tt.a, flagneg);
		z.cmov(tt.b.neg(), flagneg);

		_t.format = Format.PRECOMP;
		return _t;
	}
	private static byte[] toRadix16(byte[] a, byte[] e) {
		for (int i = 0; i < 32; ++i) {
			e[2 * i] = (byte) (a[i] & 0xF);
			e[2 * i + 1] = (byte) (a[i] >> 4 & 0xF);
		}

		int carry = 0;
		for (int i = 0; i < 63; i++) {
			e[i] += carry;
			carry = e[i] + 8;
			e[i] -= (carry >>= 4) << 4;
		}
		e[63] += carry;

		return e;
	}
	public EdPoint scalarMultiply(byte[] a) { return scalarMultiplyShared(a).mutable().lock(); }
	public EdPoint scalarMultiplyShared(byte[] a) {
		if (singleTab == null) singleTab = computeSingleTable();

		TmpNum tt = NUMS.get();

		EdPoint t = tt.pa;
		EdPoint h = tt.pb.set(curve.P3_ZERO);

		int i;
		byte[] e = tt.ba; // use 64 length
		toRadix16(a, e);
		for (i = 1; i < 64; i += 2) {
			select(i / 2, e[i], t);
			h.madd(t).toP3();
		}
		h.dbl().toP2().dbl().toP2().dbl().toP2().dbl().toP3();
		for (i = 0; i < 64; i += 2) {
			select(i / 2, e[i], t);
			h.madd(t).toP3();
		}
		return h;
	}

	private static void slide(byte[] a, byte[] r) {
		int i;
		for (i = 0; i < 256; ++i) r[i] = (byte) (1 & a[i >> 3] >> (i & 7));

		block1:
		for (i = 0; i < 256; ++i) {
			if (r[i] == 0) continue;

			block2:
			for (int b = 1; b <= 6 && i + b < 256; ++b) {
				if (r[i+b] == 0) continue;

				int vvv = r[i] + (r[i + b] << b);
				if (vvv <= 15) {
					r[i] = (byte) vvv;
					r[i + b] = 0;
					continue;
				}

				vvv = r[i] - (r[i + b] << b);
				if (vvv < -15) continue block1;
				r[i] = (byte) vvv;

				for (int k = i + b; k < 256; ++k) {
					if (r[k] == 0) {
						r[k] = 1;
						continue block2;
					}
					r[k] = 0;
				}
			}
		}
	}

	/**
	 * Variable time implement
	 */
	public EdPoint doubleScalarMultiply(EdPoint A, byte[] a, byte[] b) { return doubleScalarMultiplyShared(A, a, b).mutable().lock(); }
	public EdPoint doubleScalarMultiplyShared(EdPoint A, byte[] a, byte[] b) {
		if (doubleTab == null) doubleTab = computeDoubleTable();
		if (A.doubleTab == null) A.doubleTab = A.computeDoubleTable();

		TmpNum tt = NUMS.get();

		byte[] aslide = tt.ba;
		byte[] bslide = tt.bb;
		slide(a, aslide);
		slide(b, bslide);

		int i;
		for (i = 255; i >= 0 && aslide[i] == 0 && bslide[i] == 0; --i);

		EdPoint r = tt.pa.set(curve.P2_ZERO);

		while (i >= 0) {
			r.dbl();

			if (aslide[i] != 0) {
				r.toP3();
				if (aslide[i] > 0) r.madd(A.doubleTab[aslide[i] / 2]);
				else r.msub(A.doubleTab[-aslide[i] / 2]);
			}
			if (bslide[i] != 0) {
				r.toP3();
				if (bslide[i] > 0) r.madd(doubleTab[bslide[i] / 2]);
				else r.msub(doubleTab[-bslide[i] / 2]);
			}

			r.toP2();
			i--;
		}

		return r;
	}

	public EdPoint set(EdPoint o) {
		if (!mutable) throw new IllegalStateException();

		curve = o.curve;
		format = o.format;
		X.set(o.X);
		Y.set(o.Y);
		Z.set(o.Z);
		if (o.T == null) T = null;
		else {
			if (T == null) T = ZERO.mutable();
			T.set(o.T);
		}
		singleTab = o.singleTab;
		doubleTab = o.doubleTab;
		return this;
	}
	public EdPoint mutable() { return new EdPoint().set(this); }
	public EdPoint lock() {
		mutable = false;
		X.lock();
		Y.lock();
		Z.lock();
		if (T != null) T.lock();
		return this;
	}

	public boolean isOnCurve() { return isOnCurve(curve); }
	public boolean isOnCurve(EdCurve curve) {
		TmpNum tt = NUMS.get();

		switch (format) {
			case Format.P2:
			case Format.P3: {
				EdInteger recip = tt.invert_safe.set(Z).invert();
				EdInteger xx = tt.b.set(X).mul(recip).square();
				EdInteger yy = tt.c.set(Y).mul(recip).square();
				EdInteger dxxyy = tt.a.set(curve.D).mul(xx).mul(yy);
				return dxxyy.add1().add(xx).equals(yy);
			}
		}
		return tt.pa.set(this).toP2().isOnCurve(curve);
	}

	public int hashCode() { return Arrays.hashCode(toByteArray()); }
	public boolean equals(Object obj) {
		if (obj == this) return true;
		if (!(obj instanceof EdPoint other)) return false;

		if (format != other.format) {
			try {
				other = other.toRep(format);
			} catch (RuntimeException e) {
				return false;
			}
		}

		if (mutable) {
			EdPoint point1 = mutable();
			point1.mutable = false;
			return point1.equals(obj);
		}

		switch (format) {
			case Format.P2:
			case Format.P3: {
				if (Z.equals(other.Z)) return X.equals(other.X) && Y.equals(other.Y);

				EdInteger x1 = X.mul(other.Z);
				EdInteger x2 = other.X.mul(Z);

				EdInteger y1 = Y.mul(other.Z);
				EdInteger y2 = other.Y.mul(Z);
				return x1.equals(x2) && y1.equals(y2);
			}
			case Format.P1P1: return toP2().equals(other);
			case Format.PRECOMP: return X.equals(other.X) && Y.equals(other.Y) && Z.equals(other.Z);
			case Format.CACHED: {
				if (Z.equals(other.Z)) return X.equals(other.X) && Y.equals(other.Y) && T.equals(other.T);

				EdInteger x3 = X.mul(other.Z);
				EdInteger x4 = other.X.mul(Z);

				EdInteger y3 = Y.mul(other.Z);
				EdInteger y4 = other.Y.mul(Z);

				EdInteger t3 = T.mul(other.Z);
				EdInteger t4 = other.T.mul(Z);
				return x3.equals(x4) && y3.equals(y4) && t3.equals(t4);
			}
		}
		return false;
	}

	public String toString() {
		return "[EdPoint\nX=" + this.X + "\nY=" + this.Y + "\nZ=" + this.Z + "\nT=" + this.T + "\n]";
	}
}