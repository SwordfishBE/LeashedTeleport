package net.leashedteleport.util;

import net.leashedteleport.mixin.PersistentEntitySectionManagerAccessor;
import net.leashedteleport.mixin.ServerLevelEntityManagerAccessor;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Reads existing entity sections without loading chunks or changing visibility. */
public final class LoadedMobLookup {
    private LoadedMobLookup() {}

    public static Mob find(ServerLevel level, UUID id, Vec3 lastPosition) {
        if (level == null) return null;
        var visible = level.getEntity(id);
        if (visible instanceof Mob mob) return mob;
        if (!(level instanceof ServerLevelEntityManagerAccessor accessor)) return null;
        var manager = accessor.leashedteleport$getEntityManager();
        var sections = ((PersistentEntitySectionManagerAccessor) manager).leashedteleport$getSectionStorage();
        // Source movement is limited to one block: inspect at most eight existing sections.
        for (int x = Mth.floor(lastPosition.x - 1) >> 4; x <= Mth.floor(lastPosition.x + 1) >> 4; x++) {
            for (int y = Mth.floor(lastPosition.y - 1) >> 4; y <= Mth.floor(lastPosition.y + 1) >> 4; y++) {
                for (int z = Mth.floor(lastPosition.z - 1) >> 4; z <= Mth.floor(lastPosition.z + 1) >> 4; z++) {
                    var section = sections.getSection(SectionPos.asLong(x, y, z));
                    if (section == null) continue;
                    var found = section.getEntities().filter(entity -> entity.getUUID().equals(id)).findFirst().orElse(null);
                    if (found instanceof Mob mob) return mob;
                }
            }
        }
        return null;
    }
}
