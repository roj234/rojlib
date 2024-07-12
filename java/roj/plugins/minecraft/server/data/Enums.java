package roj.plugins.minecraft.server.data;

/**
 * @author Roj234
 * @since 2024/3/19 0019 23:35
 */
public class Enums {
	public static final byte DIFFICULTY_PEACEFUL = 0;
	public static final byte GAMEMODE_SURVIVAL = 0, GAMEMODE_CREATIVE = 1, GAMEMODE_ADVENTURE = 2, GAMEMODE_SPECTATOR = 3;
	public static final byte
		START_DESTROY_BLOCK = 0,
		ABORT_DESTROY_BLOCK = 1,
		STOP_DESTROY_BLOCK = 2,
		DROP_ALL_ITEMS = 3,
		DROP_ITEM = 4,
		RELEASE_USE_ITEM = 5,
		SWAP_ITEM_WITH_OFFHAND = 6;
	public static final byte
	MASTER = 0,
	MUSIC = 1,
	RECORDS = 2,
	WEATHER = 3,
	BLOCKS = 4,
	HOSTILE = 5,
	NEUTRAL = 6,
	PLAYERS = 7,
	AMBIENT = 8,
	VOICE = 9;
}