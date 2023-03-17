package ilib.grid;

/**
 * 电源
 *
 * @author Roj233
 * @since 2022/5/13 17:54
 */
public class Power {
	public float U, R;
	// 上次传输的电流
	public double I;
	// 最大电流 0不限
	public double Imax;

	public Power(float U, float R) {
		this.U = U;
		this.R = R;
	}

	public Power(float U, double Imax, float R) {
		this.U = U;
		this.R = R;
		this.Imax = Imax;
	}
}
