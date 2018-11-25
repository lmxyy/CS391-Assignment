package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import org.openflow.util.HexString;

import java.nio.ByteBuffer;

/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router extends Device {
    /**
     * Routing table for the router
     */
    private RouteTable routeTable;

    /**
     * ARP cache for the router
     */
    private ArpCache arpCache;

    /**
     * Creates a router for a specific host.
     *
     * @param host hostname for the router
     */
    public Router(String host, DumpFile logfile) {
        super(host, logfile);
        this.routeTable = new RouteTable();
        this.arpCache = new ArpCache();
    }

    /**
     * @return routing table for the router
     */
    public RouteTable getRouteTable() {
        return this.routeTable;
    }

    /**
     * Load a new routing table from a file.
     *
     * @param routeTableFile the name of the file containing the routing table
     */
    public void loadRouteTable(String routeTableFile) {
        if (!routeTable.load(routeTableFile, this)) {
            System.err.println("Error setting up routing table from file "
                    + routeTableFile);
            System.exit(1);
        }

        System.out.println("Loaded static route table");
        System.out.println("-------------------------------------------------");
        System.out.print(this.routeTable.toString());
        System.out.println("-------------------------------------------------");
    }

    /**
     * Load a new ARP cache from a file.
     *
     * @param arpCacheFile the name of the file containing the ARP cache
     */
    public void loadArpCache(String arpCacheFile) {
        if (!arpCache.load(arpCacheFile)) {
            System.err.println("Error setting up ARP cache from file "
                    + arpCacheFile);
            System.exit(1);
        }

        System.out.println("Loaded static ARP cache");
        System.out.println("----------------------------------");
        System.out.print(this.arpCache.toString());
        System.out.println("----------------------------------");
    }

    /**
     * Handle an Ethernet packet received on a specific interface.
     *
     * @param etherPacket the Ethernet packet that was received
     * @param inIface     the interface on which the packet was received
     */
    public void handlePacket(Ethernet etherPacket, Iface inIface) {
        System.out.println("*** -> Received packet: " +
                etherPacket.toString().replace("\n", "\n\t"));

        /********************************************************************/
        /* TODO: Handle packets                                             */
//        The packet is not an IPv4 packet.
        if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4) return;

        IPv4 packet = (IPv4) etherPacket.getPayload();
//        Check the checksum.
        if (packet.calcChecksum() != 0)
            return;

//        Check the TTL.
        int ttl = packet.getTtl() & 0xff;
        if (ttl > 1)
            packet.setTtl((byte) (ttl - 1));
        else
            return;
//        Determine whether the packet is destined for one of the router’s interfaces.
        int destinationAddress = packet.getDestinationAddress();
        int cnt = 0;
        for (Iface iface : this.interfaces.values()) {
            int ipAddress = iface.getIpAddress();
            boolean match = true;
            for (int i = 31; i >= 0; --i) {
                if (((ipAddress >> i) & 1) != ((destinationAddress >> i) & 1)) {
                    match = false;
                    break;
                }
            }
            if (match) cnt++;
        }
        if (cnt == 1)
            return;
//        Ready for forwarding the packet.
        RouteEntry entry = routeTable.lookup(destinationAddress);
        if (entry == null)
            return;
        etherPacket.setSourceMACAddress(entry.getInterface().getMacAddress().toBytes());
        etherPacket.setDestinationMACAddress(arpCache.lookup(destinationAddress).getMac().toBytes());
        packet.resetChecksum();
        packet.serialize();
        sendPacket(etherPacket, entry.getInterface());
        /********************************************************************/
    }
}
