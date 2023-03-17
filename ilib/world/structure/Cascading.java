package ilib.world.structure;

import ilib.world.schematic.SchematicLoader;
import ilib.world.structure.cascading.SizedStructure;
import ilib.world.structure.cascading.StructureMap;
import ilib.world.structure.cascading.StructureMap.Direction;
import ilib.world.structure.cascading.Structures;
import ilib.world.structure.cascading.api.IStructure;

import net.minecraft.util.EnumFacing;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class Cascading {
	public static final StructureMap RUINED_CITY;

	static {
		IStructure roomEnd = new StructAL("t/room"), door = new StructAL("t/door"), base = new StructAL("t/base"), roadns = new StructAL("t/road_ns"), roadwe = new StructAL(
			"t/road_we"), floor = new StructAL("t/an_floor"), roof = new StructAL("t/roof");

		RUINED_CITY = new StructureMap("door", Structures.builder("door").group(door).next("base", EnumFacing.SOUTH).build(),
									   Structures.builder("base").group(base).next("floor_or_roof", 0.8, EnumFacing.UP).next("road_ns", 0.6, Direction.NS).next("road_we", 0.6, Direction.WE).build(),
									   Structures.builder("road_ns").group(roadns).next("room_or_road_ns", Direction.NS).build(),
									   Structures.builder("road_we").group(roadwe).next("room_or_road_we", Direction.WE).build(),

									   Structures.builder("room_or_road_ns").group("road", roadns, 0.6).next("road_ns", Direction.NS).group("room", roadns, 0.4).next("room", Direction.NS).build(),
									   Structures.builder("room_or_road_we").group("next", roadwe, 0.6).next("room_or_road_we", Direction.WE).group("end", roomEnd, 0.4).build(),

									   Structures.builder("floor_or_roof").group(floor, 0.4).next("road_ns", Direction.NS).next("road_we", Direction.WE).group(roof, 0.6).build());
	}

	protected static class StructAL extends SizedStructure {
		/**
		 * 自动加载
		 */
		public StructAL(String filename) {
			super(SchematicLoader.load("assets/ilib/structures/" + filename));
		}
	}

}