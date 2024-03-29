package roj.util;


/**
 * HashBuilder using the FNV-1a algorithm.
 *
 * @author Maximilian Luz
 */
public class Hasher {
	private static final int FNV_PRIME = 16777619;
	private static final int FNV_BASE = (int) 2166136261L;

	private int hash;

	public Hasher() {
		this.hash = FNV_BASE;
	}

	public int getHash() {
		return hash;
	}

	public Hasher add(Object obj) {
		hash = ((obj != null ? obj.hashCode() : 0) ^ hash) * FNV_PRIME;
		return this;
	}

	public Hasher add(int obj) {
		hash = ((obj ^ (obj >> 16)) ^ hash) * FNV_PRIME;
		return this;
	}

	public Hasher add(float obj) {
		return add(Float.floatToIntBits(obj));
	}

	public Hasher add(long obj) {
		hash = (int) (((obj ^ (obj >> 32)) ^ hash) * FNV_PRIME);
		return this;
	}

	public Hasher add(double obj) {
		return add(Double.doubleToLongBits(obj));
	}
}
