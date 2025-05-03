package roj.plugins.frp;

import org.jetbrains.annotations.NotNull;
import roj.net.http.h2.H2Connection;
import roj.net.http.h2.H2FlowControlSimple;
import roj.net.http.h2.H2Setting;
import roj.net.http.h2.H2Stream;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2024/9/15 0:17
 */
final class FrpFlowControl extends H2FlowControlSimple {
	public FrpFlowControl() {this(65535, 0.75f);}
	public FrpFlowControl(int windowSize, float ratio) {this.windowSize = windowSize;this.ratio = ratio;}

	private final int windowSize;
	private final float ratio;

	@Override
	public void initSetting(H2Setting setting) {setting.initial_window_size = windowSize;}

	@Override
	public void dataReceived(H2Connection connection, @NotNull H2Stream stream, int bytes) throws IOException {
		// send data when remain < 512 or window >= 512
		//    anti small increment attack
		int size = connection.getLocalSetting().initial_window_size;
		int stWindow = stream.getReceiveWindow();

		if ((float) stWindow / size < ratio) connection.sendWindowUpdate(stream, size-stWindow);

		int conWindow = connection.getReceiveWindow();
		if ((float) conWindow / size < ratio) connection.sendWindowUpdate(null, size-conWindow);
	}
}
