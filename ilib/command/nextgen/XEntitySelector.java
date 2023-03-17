package ilib.command.nextgen;

import ilib.command.nextgen.filter.Filter;
import roj.collect.IntList;
import roj.collect.SimpleList;
import roj.concurrent.OperationDone;
import roj.config.word.Tokenizer;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.WorldServer;

import net.minecraftforge.common.DimensionManager;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2023/2/28 0028 23:36
 */
public class XEntitySelector extends Tokenizer {
	private static final String SELF = "SELF\u0000";
	private static final String PLAYER = "PLAYER\u0000";
	private static final String ALL_ENTITIES = "ANY\u0000";
	private String source;

	private IntList worlds;
	private AxisAlignedBB boundingBox;

	private Class<? extends Entity> type;

	private TagId tag;

	private Consumer<Entity> filter;

	private SimpleList<Entity> tmp;

	public ICommandSender f_sender;
	public WorldServer f_world;
	public int f_skipBits;

	public static XEntitySelector create(String s) {
		XSelTokenizer wr = new XSelTokenizer();
		// team,name
		return null;
	}

	public void forEach(ICommandSender sender, Consumer<Entity> consumer) {
		this.f_sender = sender;

		try {
			if (boundingBox != null || source == SELF) {
				inWorld((WorldServer) sender.getEntityWorld());
				return;
			}

			if (worlds == null) {
				for (WorldServer w : DimensionManager.getWorlds()) {
					try {
						inWorld(w);
					} catch (SkipThisWorld ignored) {}
				}
			} else {
				for (Integer w : worlds) {
					WorldServer world = DimensionManager.getWorld(w);
					if (world != null) {
						try {
							inWorld(world);
						} catch (SkipThisWorld ignored) {}
					}
				}
			}
		} catch (SkipThisWorld | OperationDone ignored) {

		} finally {
			this.f_sender = null;
			this.f_world = null;
			if (tmp != null) tmp.clear();
		}
	}

	private void inWorld(WorldServer w) {
		this.f_world = w;

		if (source == SELF) {
			Entity entity = f_sender.getCommandSenderEntity();
			if (entity != null) {
				reset(0);
				filter.accept(entity);
			}
			return;
		}

		if (source == PLAYER) {
			List<EntityPlayer> players = w.playerEntities;
			reset(Filter.TYPE_BIT);
			for (int i = 0; i < players.size(); i++) {
				filter.accept(players.get(i));
			}
		} else {
			if (boundingBox != null && boundingBox.getAverageEdgeLength() < 128) {
				reset(Filter.COORD_BIT);
				w.getEntitiesWithinAABB(type, boundingBox, entity -> {
					filter.accept(entity);
					return false;
				});
				return;
			}

			if (source != ALL_ENTITIES) {
				Collection<Entity> set = TagId.getMonitoredEntities(source);
				if (set != null) {
					if (tmp == null) tmp = new SimpleList<>(set);
					else {
						tmp.clear();
						tmp.ensureCapacity(set.size());
						tmp.addAll(set);
					}

					reset(Filter.TAG_BIT);
					for (int i = 0; i < tmp.size(); i++) {
						filter.accept(tmp.get(i));
					}
					return;
				}
			}

			reset(0);
			List<Entity> list = w.loadedEntityList;
			for (int i = 0; i < list.size(); i++) {
				filter.accept(list.get(i));
			}
		}
	}

	private void reset(int bits) {
		f_skipBits = bits;
		if (filter instanceof Filter) ((Filter) filter).reset(this);
	}

	// todo
	public boolean isLegal() {
		return true;
	}

	private static void reset(Consumer<Entity> f) {
	}
}
