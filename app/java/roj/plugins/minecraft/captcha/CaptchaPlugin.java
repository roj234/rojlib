package roj.plugins.minecraft.captcha;

import roj.asmx.event.Subscribe;
import roj.collect.BitSet;
import roj.collect.RingBuffer;
import roj.config.data.CMap;
import roj.io.IOUtil;
import roj.net.ChannelCtx;
import roj.plugin.Plugin;
import roj.plugins.minecraft.server.MinecraftServer;
import roj.plugins.minecraft.server.data.Block;
import roj.plugins.minecraft.server.data.BlockSet;
import roj.plugins.minecraft.server.data.Enums;
import roj.plugins.minecraft.server.event.BlockBreakEvent;
import roj.plugins.minecraft.server.event.CommandEvent;
import roj.plugins.minecraft.server.event.PlayerLoginEvent;
import roj.plugins.minecraft.server.event.PlayerMoveEvent;
import roj.plugins.minecraft.server.network.Packet;
import roj.plugins.minecraft.server.network.PlayerConnection;
import roj.ui.Text;

import java.util.Random;

/**
 * @author Roj234
 * @since 2024/3/21 11:47
 */
public class CaptchaPlugin extends Plugin {
	private BlockSet[] blockSets;
	private CaptchaFont[] captchaFonts;
	private final RingBuffer<Captcha> captchas = RingBuffer.lazy(100);
	private Random random;

	public static CaptchaPlugin INSTANCE;
	public CMap getCfg() { return getConfig(); }

	@Override
	protected void onEnable() {
		INSTANCE = this;

		CMap config = getConfig();
		config.dot(true);
		captchaFonts = CaptchaFont.load(config);

		BlockSet set = new BlockSet();
		for (Block value : Block.STATE_ID.VALUES) {
			if ((value.flags&2) != 0)
				set.addBlock(value);
		}
		blockSets = new BlockSet[] {set};

		MinecraftServer server = (MinecraftServer) getPluginManager().getPlugin("MinecraftServer").instance();
		server.getEventBus().register(this);

		random = server.random;
	}

	@Subscribe
	public void onPlayerLogin(PlayerLoginEvent.Pre event) {
		event.getChannel().addLast("captcha", getCaptchaForPlayer());
	}

	@Subscribe
	public void onCommand(CommandEvent event) {
		ChannelCtx ctx = event.getPlayer().connection().handler("captcha");
		if (ctx == null) return;
		Captcha captcha = (Captcha) ctx.handler();

		String cmd = event.getCommand();
		if (cmd.startsWith("verify ")) {
			PlayerConnection player = event.getPlayer();
			BitSet[] choices = captcha.choices();
			for (int i = 0; i < choices.length; i++) {
				if (!choices[i].contains(cmd.charAt(7+i))) {
					player.playGlobalSound("entity.villager.no", Enums.BLOCKS, 1);
					player.sendMessage(new Text("非常抱歉，您输入的验证码不正确！").colorRGB(0xCCE442), false);
					return;
				}
			}

			// TODO 行为验证
			// 1. 必须先移动到验证码数字前面
			// 2. 如果生成时是封闭的，那必须打洞
			// 3. 生成一些隐形实体看发的packet
			// 最后把移动轨迹拿去训练AI
			double prob = 0;
			prob += captcha.getCommandScore();

			if (prob < 0) {
				player.playGlobalSound("entity.villager.death", Enums.BLOCKS, 1);
				player.disconnect(new Text("非常抱歉，您未通过行为验证！\n("+(float)prob+","+captcha.hashCode()+")").colorRGB(0xEECD89));
			} else {
				player.playGlobalSound("entity.villager.yes", Enums.BLOCKS, 1);
				player.sendMessage(new Text("验证码正确！欢迎回家，"+player.getName()).colorRGB(0xBBD3E9), false);
				player.sendPacket(new Packet("CustomPayload", IOUtil.getSharedByteBuf().putVarIntUTF("captcha:success").putDouble(prob)));
			}

			captcha.channelClosed(null);
			ctx.removeSelf();
		}
	}

	@Subscribe
	public void onMove(PlayerMoveEvent event) {
		//event.cancel();
	}

	@Subscribe
	public void onBreakBlock(BlockBreakEvent.Pre event) {
		event.setBreakTicks(100);
	}

	@Subscribe
	public void onBreakBlock(BlockBreakEvent.Post event) {
		System.out.println("block breaked ok "+event.getPosition());
	}

	public Captcha getCaptchaForPlayer() {
		CaptchaFont font = captchaFonts[random.nextInt(captchaFonts.length)];
		return font.generate(random, blockSets[random.nextInt(blockSets.length)], 6);
	}
}