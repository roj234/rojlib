package roj.math;

/**
 * @author Roj234
 * @since 2024/2/20 0020 0:33
 */
public class EMA {
	private final double beta;
	private final int T;
	private double avg;
	private int count;

	public EMA(double beta) {
		if (!(beta > 0 & beta < 1)) throw new IllegalArgumentException("beta should in (0,1)");

		int approxT = (int) (1 / (1-beta));
		double beta1 = Math.pow(beta, approxT);
		while (beta1 > 1/Math.E) {
			beta1 *= beta;
			approxT++;
		}

		this.beta = beta;
		this.T = approxT;
	}

	public void add(double value) {
		avg = beta*avg + (1-beta) * value;
		count++;
	}
	public double avg() { return count < T ? avg / (1 - Math.pow(beta, count)) : avg; }

	public void clear() {
		count = 0;
		avg = 0;
	}
}