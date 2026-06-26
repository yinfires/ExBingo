package me.jfenn.bingo.client.mixin;

import me.jfenn.bingo.client.mixinhandler.LanInterfacesHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

/**
 * Broadcasts LAN discovery pings on ALL suitable IPv4 interfaces, not just the OS-default one.
 *
 * <p>Vanilla's {@code LanServerPinger} sends through a plain {@code DatagramSocket} bound to
 * the wildcard address, so the OS routing table picks the outgoing interface. With a VPN or
 * virtual adapter present that is often not the real LAN NIC, and peers on the physical LAN
 * never receive the ping. We keep the vanilla send and additionally send once per suitable
 * IPv4 interface. See {@link LanInterfacesHelper}.
 */
@Mixin(net.minecraft.client.server.LanServerPinger.class)
public abstract class LanServerPingerMixin {

    @Redirect(
            method = "run",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/net/DatagramSocket;send(Ljava/net/DatagramPacket;)V"
            )
    )
    private void exbingo$sendOnAllInterfaces(DatagramSocket socket, DatagramPacket packet) throws Exception {
        // keep the vanilla default-interface send (covers the common single-NIC case)...
        socket.send(packet);
        // ...and additionally broadcast on every other suitable IPv4 interface
        LanInterfacesHelper.sendOnAllInterfaces(socket, packet);
    }
}
