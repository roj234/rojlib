package ilib.asm.nx.client;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.io.FastFailException;

import net.minecraft.client.resources.FallbackResourceManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.SimpleResource;
import net.minecraft.client.resources.data.MetadataSerializer;
import net.minecraft.util.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Roj233
 * @since 2022/6/2 20:45
 */
@Nixim("/")
class NxResMan extends FallbackResourceManager {
	@Shadow
	private MetadataSerializer frmMetadataSerializer;

	NxResMan() {
		super(null);
	}

	@Inject("/")
	public IResource getResource(ResourceLocation loc) throws IOException {
		this.checkResourcePath(loc);

		IResourcePack metaPack = null;
		ResourceLocation metaLoc = getLocationMcmeta(loc);

		for (int i = resourcePacks.size() - 1; i >= 0; --i) {
			IResourcePack pack = resourcePacks.get(i);
			if (metaPack == null && pack.resourceExists(metaLoc)) {
				metaPack = pack;
			}

			if (pack.resourceExists(loc)) {
				InputStream metaIn = null;
				if (metaPack != null) {
					metaIn = this.getInputStream(metaLoc, metaPack);
				}

				return new SimpleResource(pack.getPackName(), loc, getInputStream(loc, pack), metaIn, frmMetadataSerializer);
			}
		}

		throw new FastFailException(loc.toString());
	}

	@Inject("/")
	public List<IResource> getAllResources(ResourceLocation loc) throws IOException {
		checkResourcePath(loc);

		List<IResource> list = new ArrayList<>(1);
		ResourceLocation meta = getLocationMcmeta(loc);

		for (int i = resourcePacks.size() - 1; i >= 0; --i) {
			IResourcePack pack = resourcePacks.get(i);
			if (pack.resourceExists(loc)) {
				InputStream metaIn = pack.resourceExists(meta) ? getInputStream(meta, pack) : null;
				list.add(new SimpleResource(pack.getPackName(), loc, getInputStream(loc, pack), metaIn, frmMetadataSerializer));
			}
		}

		if (list.isEmpty()) {
			throw new FastFailException(loc.toString());
		} else {
			return list;
		}
	}

	@Shadow
	private void checkResourcePath(ResourceLocation x) throws IOException {}

	@Shadow
	static ResourceLocation getLocationMcmeta(ResourceLocation location) {
		return null;
	}
}
