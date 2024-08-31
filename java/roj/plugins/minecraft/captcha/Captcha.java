package roj.plugins.minecraft.captcha;

import org.jetbrains.annotations.NotNull;
import roj.collect.MyBitSet;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.concurrent.timing.ScheduleTask;
import roj.config.data.CList;
import roj.config.data.CMap;
import roj.config.data.CShort;
import roj.io.IOUtil;
import roj.math.Vec4d;
import roj.net.ChannelCtx;
import roj.net.ChannelHandler;
import roj.plugins.minecraft.server.MinecraftServer;
import roj.plugins.minecraft.server.data.Enums;
import roj.plugins.minecraft.server.data.Item;
import roj.plugins.minecraft.server.data.ItemStack;
import roj.plugins.minecraft.server.data.PlayerEntity;
import roj.plugins.minecraft.server.data.world.World;
import roj.plugins.minecraft.server.network.CommandTree;
import roj.plugins.minecraft.server.network.ConstantPacket;
import roj.plugins.minecraft.server.network.Packet;
import roj.plugins.minecraft.server.network.PlayerConnection;
import roj.text.CharList;
import roj.text.LineReader;
import roj.text.Template;
import roj.text.TextUtil;
import roj.ui.AnsiString;
import roj.ui.Terminal;
import roj.ui.terminal.Argument;
import roj.ui.terminal.CommandNode;
import roj.ui.terminal.SimpleCliParser;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.net.InetSocketAddress;

import static roj.ui.terminal.CommandNode.argument;
import static roj.ui.terminal.CommandNode.literal;

/**
 * @author Roj234
 * @since 2024/3/19 0019 16:50
 */
public final class Captcha implements ChannelHandler {
	private static final ConstantPacket MY_COMMAND_TREE = new ConstantPacket("CommandTree", buf -> {
		CommandTree tree = new CommandTree();
		tree.then(literal("mycaptcha:mcadmin").then(argument("command", Argument.string())).executes(SimpleCliParser.nullImpl()));
		tree.then(literal("mcadmin").then(argument("command", Argument.string())).executes(SimpleCliParser.nullImpl()));
		tree.then(literal("mycaptcha:verify").then(argument("command", Argument.string())).executes(SimpleCliParser.nullImpl()));
		tree.then(literal("verify").then(argument("captcha", Argument.string())).executes(SimpleCliParser.nullImpl()));

		String CMD = "? about bukkit:?||? bukkit:about||about bukkit:help||help bukkit:pl||plugins bukkit:plugins||plugins bukkit:ver||version bukkit:version||version help icanhasbukkit me|action minecraft:help minecraft:me||me minecraft:msg||msg minecraft:teammsg||teammsg minecraft:tell||tell minecraft:tm|tm minecraft:trigger|trigger minecraft:w||w msg|message pl||plugins plugins teammsg|message tell|player|message tm||teammsg trigger|action ver||version version w";
		for (String str : TextUtil.split(new SimpleList<>(), CMD, ' ')) {
			int pos = str.indexOf('|');
			String name = pos >= 0 ? str.substring(0, pos) : str;
			String alias = str.charAt(pos+1) == '|' ? str.substring(pos+2) : null;
			String arg1 = alias == null ? str.substring(pos+1) : null;

			CommandNode base = literal(name);
			if (arg1 != null) base = base.then(argument(arg1, Argument.string()));
			tree.then(base.executes(SimpleCliParser.nullImpl()));
		}

		tree.write(buf);
	});

	private final String text;
	private final MyBitSet[] choices;
	private final World world;

	private Vec4d[] matrix;
	private long haveSeen;

	public Captcha(String text, MyBitSet[] choices, World world) {
		this.text = text;
		this.choices = choices;
		this.world = world;
	}

	private ScheduleTask timeoutTask;
	int time = 0;

	@Override
	public void channelOpened(ChannelCtx ctx) throws IOException {
		int range = 9;
		for (int xx = -range; xx < range; xx++) {
			for (int zz = -range; zz < range; zz++) {
				ByteList packet = world.getChunk(xx, zz).createPacket();
				ctx.channel().fireChannelWrite(new Packet("ChunkData", packet));
				packet._free();
			}
		}

		PlayerConnection pc = ctx.attachment(MinecraftServer.PLAYER);
		pc.getEntity().setWorld(world);
		pc.teleport(0, 24, -32, 0, 0);

		CaptchaPlugin pl = CaptchaPlugin.INSTANCE;

		CMap cfg = pl.getCfg();
		int timeout = cfg.getInteger("timeout");

		Template template = Template.compile(cfg.getString("messages.intro"));
		MyHashMap<String, Object> map = new MyHashMap<>();
		map.put("reset", cfg.getInteger("timeout_reset"));
		map.put("length", choices.length);
		map.put("user", pc.getName());
		map.put("ip", ((InetSocketAddress)ctx.remoteAddress()).getAddress().getHostAddress());

		for (String line : LineReader.create(template.format(map, new CharList()).toString())) {
			pc.sendMessage(new AnsiString(line).color16(Terminal.PURPLE + Terminal.HIGHLIGHT), false);
		}

		timeoutTask = pl.getScheduler().loop(() -> {
			if (time >= timeout) {
				pc.disconnect("超过时间！");
				return;
			}

			int remain = timeout - time;
			// float barProgress
			// varInt experience, experienceLevel
			ByteList buf = IOUtil.getSharedByteBuf();
			pc.sendPacket(new Packet("ExperienceBarUpdate", buf
				.putFloat((float) remain / timeout)
				.putVarInt(remain)
				.putVarInt(0)
			));

			if (remain == 90) {
				pc.playGlobalSound("item.goat_horn.sound.0", Enums.BLOCKS, 1);
			} else if (remain == 60) {
				pc.playGlobalSound("entity.ender_dragon.death", Enums.BLOCKS, 1);
			} else if (remain == 30) {
				pc.playGlobalSound("block.anvil.land", Enums.BLOCKS, 0);
				pc.sendMessage(new AnsiString("还有30秒！"), true);
			} else if (remain < 10) {
				pc.playGlobalSound("block.note_block.pling", Enums.BLOCKS, remain&3);
				pc.sendMessage(new AnsiString("还有"+remain+"秒！"), true);
			}

			time++;
		}, 1000, timeout+1);

		/*int[] counter = new int[1];
		Scheduler.getDefaultScheduler().loop(() -> {
			// byte syncId
			// varInt revision
			// short slot
			// ItemStack stack
			ByteList buf1 = IOUtil.getSharedByteBuf();
			buf1.put(0).putVarInt(0).putShort(37);
			ItemStack stack = new ItemStack(Item.getItem("minecraft:white_wool"), ++counter[0]);
			stack.toMinecraftPacket(buf1);
			ctx.channel().fireChannelWrite(new Packet("ScreenHandlerSlotUpdate", buf1));
		}, 1000, 100);*/

		if (cfg.getBool("debug")) {
			CharList buf = IOUtil.getSharedCharBuf();
			for (MyBitSet choice : choices) buf.append((char) choice.first());
			System.out.println(buf);
			pc.sendMessage(new AnsiString(buf.toString()), true);
		}

		ctx.channelWrite(MY_COMMAND_TREE);

		PlayerEntity player = pc.getEntity();
		player.inventory[36] = getMyPickaxe("minecraft:diamond_pickaxe", new AnsiString("神奇").clear().colorRGB(0xFFEEEE).append(new AnsiString("稿子").colorRGB(0xC876BE)));
		player.inventory[37] = getMyPickaxe("minecraft:diamond_axe", new AnsiString("神奇").clear().colorRGB(0xFFEEEE).append(new AnsiString("斧子").colorRGB(0xECBB5A)));
		player.inventory[38] = getMyPickaxe("minecraft:diamond_shovel", new AnsiString("神奇").clear().colorRGB(0xFFEEEE).append(new AnsiString("铲子").colorRGB(0xDC6432)));
		pc.syncInventory();
	}

	@NotNull
	private static ItemStack getMyPickaxe(String id, AnsiString name) {
		ItemStack _pickaxe = new ItemStack(Item.getItem(id), 1);
		CMap tag = _pickaxe.tag();
		tag.put("Unbreakable", true);
		tag.put("Damage", 1560);
		tag.getOrCreateMap("display").put("Name", name.toMinecraftJson());
		CList enchantment = tag.getOrCreateList("Enchantments");
		CMap eff = new CMap();
		eff.put("id", "minecraft:efficiency");
		eff.put("lvl", new CShort((short) 5));
		enchantment.add(eff);
		return _pickaxe;
	}

	@Override
	public void channelClosed(ChannelCtx ctx) {
		timeoutTask.cancel();
	}

	private int verifyState = -1;
	private int prevTabId;
	public float getCommandScore() {
		return verifyState < 0 ? -3 : verifyState == 0 ? 0 : 3;
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		Packet p = ((Packet) msg);
		DynByteBuf in = p.getData();
		switch (p.name) {
			case "UpdatePlayerAbilities" -> {
				ctx.channelWrite(new Packet("PlayerAbilities", IOUtil.getSharedByteBuf().put(0xF).putFloat(0.2f).putFloat(0.05f)));
				in.readByte();
			}
			case "RequestCommandCompletions" -> {
				int i = in.readVarInt();
				if (i < prevTabId) return;
				prevTabId = i;

				String command = in.readVarIntUTF(256);
				if (!command.startsWith("/verify ")) {
					verifyState = -1;
				} else if (command.equals("/verify ")) {
					verifyState = command.length();
				} else if (verifyState > 0) {
					if (verifyState > command.length()) {
						verifyState = command.length();
					} else if (verifyState+1 != command.length()) {
						if (verifyState != command.length()) {
							verifyState = 0;
						}
					} else {
						verifyState++;
					}
				}

				if (verifyState > 0) {
					System.out.println("good: maybe hand-written");
				} else if (verifyState == 0) {
					System.out.println("norm: must copy-paste");
				} else {
					System.out.println("bad: error");
				}
			}
			default -> {
				System.out.println("Recv->"+p.name+" => "+in.dump());
			}
		}
	}

	public String text() {return text;}
	public MyBitSet[] choices() {return choices;}
	public World world() {return world;}
}