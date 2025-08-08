package roj.plugins.minecraft.server.event;

import roj.asmx.event.Event;
import roj.plugins.minecraft.server.network.PlayerConnection;

/**
 * @author Roj234
 * @since 2024/3/22 3:49
 */
public class CommandEvent extends Event {
	private final PlayerConnection player;
	private final String command;
	private final long time;

	public CommandEvent(PlayerConnection player, String command, long time) {
		this.player = player;
		this.command = command;
		this.time = time;
	}

	public PlayerConnection getPlayer() { return player; }
	public String getCommand() { return command; }
	public long getTime() { return time; }
}