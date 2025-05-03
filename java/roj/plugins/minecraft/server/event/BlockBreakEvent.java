package roj.plugins.minecraft.server.event;

import roj.asmx.event.Cancellable;
import roj.asmx.event.Event;
import roj.math.Vec3i;
import roj.plugins.minecraft.server.network.PlayerConnection;

/**
 * @author Roj234
 * @since 2024/3/22 03:55
 */
public class BlockBreakEvent extends Event {
	private final PlayerConnection connection;
	private final Vec3i position;
	public BlockBreakEvent(PlayerConnection connection, Vec3i position) {
		this.connection = connection;
		this.position = position;
	}

	public Vec3i getPosition() { return position; }
	public PlayerConnection getConnection() { return connection; }

	public static class Pre extends BlockBreakEvent {
		private int breakTicks;

		public Pre(PlayerConnection connection, Vec3i position) {
			super(connection, position);
		}

		public int getBreakTicks() { return breakTicks; }
		public void setBreakTicks(int breakTicks) { this.breakTicks = breakTicks; }
	}

	@Cancellable
	public static class Post extends BlockBreakEvent {
		public Post(PlayerConnection connection, Vec3i position) {
			super(connection, position);
		}
	}
}