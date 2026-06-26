package me.jfenn.bingo.client.mixinhandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Helper for robust LAN discovery across multiple network interfaces.
 *
 * <p>Vanilla joins/sends the discovery multicast group on only the OS-default multicast
 * interface. On Windows 11 hosts with VPNs, virtual adapters (VMware/VirtualBox/WSL/Hamachi)
 * or multiple NICs, the default interface is frequently NOT the real LAN adapter, so the
 * broadcast goes out — or is listened for — on the wrong interface and peers never see the
 * room. Forcing the IPv4 multicast group alone (see DualStackForceIPv4Mixin) does not fix
 * this; the interface selection itself must be corrected.
 *
 * <p>These helpers enumerate every up, non-loopback interface that supports multicast and
 * has an IPv4 address, and join/send on all of them.
 */
public final class LanInterfacesHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger("ExBingo/LanInterfaces");

    private LanInterfacesHelper() {}

    /** Interfaces that are up, support multicast, are not loopback, and have an IPv4 address. */
    public static List<NetworkInterface> multicastIPv4Interfaces() {
        List<NetworkInterface> result = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces != null && ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                try {
                    if (!iface.isUp() || iface.isLoopback() || !iface.supportsMulticast()) continue;
                } catch (Throwable e) {
                    continue;
                }
                boolean hasIPv4 = false;
                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    if (addrs.nextElement() instanceof java.net.Inet4Address) {
                        hasIPv4 = true;
                        break;
                    }
                }
                if (hasIPv4) result.add(iface);
            }
        } catch (Throwable e) {
            LOGGER.debug("[ExBingo] Could not enumerate network interfaces", e);
        }
        return result;
    }

    /**
     * Joins {@code group} on every suitable IPv4 interface so the listener hears broadcasts
     * regardless of which adapter they arrive on. The vanilla single-interface join already
     * happened in the constructor; failures here are non-fatal (e.g. already joined).
     */
    public static void joinOnAllInterfaces(MulticastSocket socket, InetAddress group, int port) {
        int joined = 0;
        for (NetworkInterface iface : multicastIPv4Interfaces()) {
            try {
                socket.joinGroup(new InetSocketAddress(group, port), iface);
                joined++;
            } catch (Throwable e) {
                // already joined on this iface, or it rejects the group — ignore
            }
        }
        LOGGER.debug("[ExBingo] LAN discovery: joined multicast group on {} extra interface(s)", joined);
    }

    /**
     * Sends {@code packet} once per suitable IPv4 interface (in addition to the vanilla
     * default-interface send), so the broadcast reaches the real LAN adapter even when the
     * OS default route points at a VPN/virtual adapter.
     */
    public static void sendOnAllInterfaces(DatagramSocket defaultSocket, DatagramPacket packet) {
        for (NetworkInterface iface : multicastIPv4Interfaces()) {
            try (MulticastSocket socket = new MulticastSocket()) {
                socket.setNetworkInterface(iface);
                socket.send(packet);
            } catch (Throwable e) {
                // interface can't send to this group — ignore and try the next
            }
        }
    }
}
