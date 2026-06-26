package me.jfenn.bingo.client.mixin;

import me.jfenn.bingo.client.mixinhandler.LanInterfacesHelper;
import net.minecraft.client.server.LanServerPinger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.InetAddress;
import java.net.MulticastSocket;

/**
 * Makes LAN discovery listen on ALL suitable IPv4 interfaces, not just the OS-default one.
 *
 * <p>Vanilla's {@code LanServerDetector} joins the discovery multicast group via the
 * deprecated {@code joinGroup(InetAddress)}, which binds to a single default multicast
 * interface. On hosts with VPNs/virtual adapters that default is often not the real LAN
 * NIC, so broadcasts arriving on the physical LAN are never received. We additionally join
 * the group on every up, multicast-capable IPv4 interface. See {@link LanInterfacesHelper}.
 */
@Mixin(targets = "net.minecraft.client.server.LanServerDetection$LanServerDetector")
public abstract class LanServerDetectorMixin {

    @Shadow @org.spongepowered.asm.mixin.Final
    private MulticastSocket socket;

    @Shadow @org.spongepowered.asm.mixin.Final
    private InetAddress pingGroup;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void exbingo$joinAllInterfaces(CallbackInfo ci) {
        LanInterfacesHelper.joinOnAllInterfaces(socket, pingGroup, LanServerPinger.PING_PORT);
    }
}
