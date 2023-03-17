package ilib.asm.nx;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.Entity;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.ReportedException;

import java.util.Map;

/**
 * @author Roj233
 * @since 2022/5/16 6:10
 */
@Nixim("/")
abstract class NoDataLock extends EntityDataManager {
	@Shadow
	private Map<Integer, DataEntry<?>> entries;

	NoDataLock(Entity entityIn) {
		super(entityIn);
	}

	@Inject
	@SuppressWarnings("unchecked")
	private <T> EntityDataManager.DataEntry<T> getEntry(DataParameter<T> key) {
		EntityDataManager.DataEntry<?> entry;
		try {
			entry = entries.get(key.getId());
		} catch (Throwable e) {
			CrashReport rpt = CrashReport.makeCrashReport(e, "Getting synched entity data");
			CrashReportCategory cat = rpt.makeCategory("Synched entity data");
			cat.addCrashSection("Data ID", key);
			throw new ReportedException(rpt);
		}

		return (DataEntry<T>) entry;
	}
}
