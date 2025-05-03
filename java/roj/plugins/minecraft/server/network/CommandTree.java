package roj.plugins.minecraft.server.network;

import roj.collect.ToIntMap;
import roj.config.ParseException;
import roj.ui.Argument;
import roj.ui.CommandNode;
import roj.ui.CommandParser;
import roj.ui.Completion;
import roj.util.DynByteBuf;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;

/**
 * @author Roj234
 * @since 2024/3/22 12:52
 */
public class CommandTree extends CommandNode {
	public CommandTree() {}

	@Override
	public boolean apply(CommandParser ctx, List<Completion> completions) throws ParseException {
		return false;
	}

	private static final byte MASK_TYPE = 3;
	private static final byte FLAG_EXECUTABLE = 4;
	private static final byte FLAG_REDIRECT = 8;
	private static final byte FLAG_CUSTOM_SUGGESTIONS = 16;
	private static final byte TYPE_ROOT = 0;
	private static final byte TYPE_LITERAL = 1;
	private static final byte TYPE_ARGUMENT = 2;

	public void write(DynByteBuf buf) {
		ToIntMap<CommandNode> map = traverse(this);

		List<CommandNodeData> nodes = collectNodes(map);
		buf.putVarInt(nodes.size());
		for (int i = 0; i < nodes.size(); i++)
			nodes.get(i).write(buf);

		buf.putVarInt(map.getInt(this));
	}

	private static ToIntMap<CommandNode> traverse(CommandTree $$0) {
		ToIntMap<CommandNode> map = new ToIntMap<>();
		Queue<CommandNode> queue = new ArrayDeque<>();
		queue.add($$0);

		CommandNode node;
		while((node = queue.poll()) != null) {
			if (!map.containsKey(node)) {
				int id = map.size();
				map.put(node, id);
				queue.addAll(node.getChildren());
			}
		}

		return map;
	}

	private static List<CommandNodeData> collectNodes(ToIntMap<CommandNode> commands) {
		CommandNodeData[] dataArray = new CommandNodeData[commands.size()];
		for (ToIntMap.Entry<CommandNode> entry : commands.selfEntrySet()) {
			dataArray[entry.v] = createNodeData(entry.k, commands);
		}
		return Arrays.asList(dataArray);
	}

	private static CommandNodeData createNodeData(CommandNode node, ToIntMap<CommandNode> commands) {
		int flag = 0;
		int redirect;
		if (node.getRedirect() != null) {
			flag |= FLAG_REDIRECT;
			redirect = commands.getInt(node.getRedirect());
		} else {
			redirect = 0;
		}

		if (node.getCommand() != null) flag |= FLAG_EXECUTABLE;

		NodeStub stub;
		if (node instanceof CommandTree) {
			flag |= TYPE_ROOT;
			stub = null;
		} else if (node.getName() == null) {
			ArgumentNodeStub argNode = new ArgumentNodeStub((ArgumentNode)node);
			stub = argNode;
			flag |= TYPE_ARGUMENT;
			if (argNode.argumentType != null) {
				flag |= FLAG_CUSTOM_SUGGESTIONS;
			}
		} else {
			stub = new LiteralNodeStub(node.getName());
			flag |= TYPE_LITERAL;
		}

		int[] arr = new int[node.getChildren().size()];
		List<CommandNode> children = node.getChildren();
		for (int i = 0; i < children.size(); i++) {
			arr[i] = commands.getInt(children.get(i));
		}

		return new CommandNodeData(stub, flag, redirect, arr);
	}

	private static class CommandNodeData {
		final NodeStub stub;
		final int flags;
		final int redirect;
		final int[] children;

		CommandNodeData(NodeStub $$0, int $$1, int $$2, int[] $$3) {
			this.stub = $$0;
			this.flags = $$1;
			this.redirect = $$2;
			this.children = $$3;
		}

		public void write(DynByteBuf buf) {
			buf.put(flags).putVarInt(children.length);
			for (int child : children) buf.putVarInt(child);
			if ((flags & 8) != 0) buf.putVarInt(redirect);

			if (stub != null) stub.write(buf);
		}
	}

	private interface NodeStub {
		void write(DynByteBuf var1);
	}

	private static class ArgumentNodeStub implements NodeStub {
		private final String id, suggestionId;
		private final Argument<?> argumentType;

		public ArgumentNodeStub(ArgumentNode node) {
			this.id = node.getArgumentName();
			this.argumentType = node.getArgument();
			this.suggestionId = "minecraft:ask_server";//getSuggestionId(node.getCustomSuggestions()));
		}

		public void write(DynByteBuf buf) {
			buf.putVarIntUTF(this.id);
			if (argumentType.type().equals("字符串")) {
				buf.putVarInt(5).put(0);
				// string:word
			} else {
				throw new UnsupportedOperationException(argumentType.type()+" is not supported now");
			}

			if (suggestionId != null) buf.putVarIntUTF(suggestionId);
		}

		/*private static void serializeCap() {
			0, "brigadier:bool", BoolArgumentType
			1, "brigadier:float", FloatArgumentType
			2, "brigadier:double", DoubleArgumentType
			3, "brigadier:integer", IntegerArgumentType
			4, "brigadier:long", LongArgumentType
			5, "brigadier:string", StringArgumentType
			6, "entity", EntityArgumentType
			7, "game_profile", GameProfileArgumentType
			8, "block_pos", BlockPosArgumentType
			9, "column_pos", ColumnPosArgumentType
			10, "vec3", Vec3ArgumentType
			11, "vec2", Vec2ArgumentType
			12, "block_state", BlockStateArgumentType
			13, "block_predicate", BlockPredicateArgumentType
			14, "item_stack", ItemStackArgumentType
			15, "item_predicate", ItemPredicateArgumentType
			16, "color", ColorArgumentType
			17, "component", TextArgumentType
			18, "message", MessageArgumentType
			19, "nbt_compound_tag", NbtCompoundArgumentType
			20, "nbt_tag", NbtElementArgumentType
			21, "nbt_path", NbtPathArgumentType
			22, "objective", ScoreboardObjectiveArgumentType
			23, "objective_criteria", ScoreboardCriterionArgumentType
			24, "operation", OperationArgumentType
			25, "particle", ParticleEffectArgumentType
			26, "angle", AngleArgumentType
			27, "rotation", RotationArgumentType
			28, "scoreboard_slot", ScoreboardSlotArgumentType
			29, "score_holder", ScoreHolderArgumentType
			30, "swizzle", SwizzleArgumentType
			31, "team", TeamArgumentType
			32, "item_slot", ItemSlotArgumentType
			33, "resource_location", IdentifierArgumentType
			34, "mob_effect", StatusEffectArgumentType
			35, "function", CommandFunctionArgumentType
			36, "entity_anchor", EntityAnchorArgumentType
			37, "int_range", NumberRangeArgumentType.Ints
			38, "float_range", NumberRangeArgumentType.FloatRangeArgumentType
			39, "item_enchantment", EnchantmentArgumentType
			40, "entity_summon", EntitySummonArgumentType
			41, "dimension", DimensionArgumentType
			42, "time", TimeArgumentType
			43, "resource_or_tag", upcast(RegistryPredicateArgumentType.class), new RegistryPredicateArgumentType.Info());
			44, "resource", upcast(RegistryKeyArgumentType.class), new RegistryKeyArgumentType.Serializer());
			45, "template_mirror", BlockMirrorArgumentType
			46, "template_rotation", BlockRotationArgumentType

			net.minecraft.command.argument.serialize.ArgumentSerializer
			$$0.writeVarInt(Registry.COMMAND_ARGUMENT_TYPE.getRawId($$1));
			$$1.writePacket($$2, $$0);
		}*/
	}

	static class LiteralNodeStub implements NodeStub {
		private final String name;
		LiteralNodeStub(String name) { this.name = name; }
		public void write(DynByteBuf buf) { buf.putVarIntUTF(name); }
	}
}