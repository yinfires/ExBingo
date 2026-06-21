package me.jfenn.bingo.mixin;

import me.jfenn.bingo.impl.BossBarManager;
import net.minecraft.server.level.ServerBossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.ref.WeakReference;

@Mixin(ServerBossEvent.class)
public class ServerBossBarMixin {
    @Inject(at = @At("TAIL"), method = "<init>")
    public void init(CallbackInfo ci) {
        ServerBossEvent bossBar = (ServerBossEvent) (Object) this;
        BossBarManager.getTrackedServerBossBars()
                .add(new WeakReference<>(bossBar));
    }
}
