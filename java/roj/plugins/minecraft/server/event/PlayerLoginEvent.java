package roj.plugins.minecraft.server.event;

import roj.asmx.event.Cancellable;
import roj.asmx.event.Event;
import roj.net.MyChannel;
import roj.plugins.minecraft.server.data.PlayerEntity;
import roj.plugins.minecraft.server.network.PlayerConnection;
import roj.ui.Text;

/**
 * @author Roj234
 * @since 2024/3/21 11:50
 */
public class PlayerLoginEvent extends Event {
	private final PlayerConnection connection;
	public PlayerLoginEvent(PlayerConnection connection) {
		this.connection = connection;
	}

	public PlayerConnection getConnection() { return connection; }

	@Cancellable
	public static class Pre extends PlayerLoginEvent {
		private final MyChannel channel;
		public Pre(PlayerConnection connection, MyChannel channel) {
			super(connection);
			this.channel = channel;
		}

		public MyChannel getChannel() { return channel; }

		private Text message;

		public void cancelWithMessage(Text message) {
			cancel();
			this.message = message;
		}

		public Text getMessage() { return message; }
	}

	public static class Post extends PlayerLoginEvent {
		private PlayerEntity playerEntity;
		public Post(PlayerConnection connection, PlayerEntity entity) {
			super(connection);
			this.playerEntity = entity;
		}

		public PlayerEntity getPlayerEntity() { return playerEntity; }
		public void setPlayerEntity(PlayerEntity entity) { playerEntity = entity; }
	}

	public static class Disconnect extends PlayerLoginEvent {
		private int reasonEnum;
		public Disconnect(PlayerConnection connection, int reasonEnum) {
			super(connection);
			this.reasonEnum = reasonEnum;
		}

		public int getReasonEnum() { return reasonEnum; }
	}
}