package ilib.world.structure.cascading.api;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2020/9/20 0:45
 */
public class StructureData implements IStructureData {
	final IStructure structure;
	final IGenerationData[] data;

	public StructureData(IStructure structure, IGenerationData[] data) {
		this.structure = structure;
		this.data = data;
	}

	@Override
	public IStructure getStructure() {
		return structure;
	}

	@Override
	public IGenerationData[] getNearby() {
		return data;
	}
}
