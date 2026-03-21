package net.leashedteleport.mixin;

import net.minecraft.world.entity.Leashable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.entity.Mob")
public interface LeashableEntityAccessor {

    @Accessor("leashData")
    void leashedteleport_setLeashData(Leashable.LeashData data);
}
