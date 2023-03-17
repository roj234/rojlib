package ilib.grid;

/**
 * @author Roj233
 * @since 2022/5/13 16:17
 */
public interface IConductor extends GridEntry {
	default void onEnergyFlow(float U, double I) {}
}
