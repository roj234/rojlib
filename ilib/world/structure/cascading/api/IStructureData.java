package ilib.world.structure.cascading.api;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2020/9/20 0:44
 */
public interface IStructureData {
	IStructure getStructure();

	IGenerationData[] getNearby();
}
