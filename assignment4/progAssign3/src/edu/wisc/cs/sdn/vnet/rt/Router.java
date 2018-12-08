package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.ICMP;
import net.floodlightcontroller.packet.IPv4;
import org.openflow.util.HexString;

import java.nio.ByteBuffer;
import java.util.Arrays;

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
        switch (etherPacket.getEtherType()) {
            case Ethernet.TYPE_IPv4:
                this.handleIpPacket(etherPacket, inIface);
                break;
            // Ignore all other packet types, for now
            default:
                System.err.println("An unknown packet.");
                break;
        }

        /********************************************************************/
    }

    private void handleIpPacket(Ethernet etherPacket, Iface inIface) {
        // Make sure it's an IP packet
        if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4) {
            return;
        }

        // Get IP header
        IPv4 ipPacket = (IPv4) etherPacket.getPayload();
        System.out.println("Handle IP packet");

        // Verify checksum
        short origCksum = ipPacket.getChecksum();
        ipPacket.resetChecksum();
        byte[] serialized = ipPacket.serialize();
        ipPacket.deserialize(serialized, 0, serialized.length);
        short calcCksum = ipPacket.getChecksum();
        if (origCksum != calcCksum) {
            return;
        }

        // Check TTL
        ipPacket.setTtl((byte) (ipPacket.getTtl() - 1));
        if (0 == ipPacket.getTtl()) {
//            Time Exceeded ICMP
            Ethernet icmpMessage = getICMPMessage(inIface, ipPacket, (byte) 11, (byte) 0);
            this.sendPacket(icmpMessage, inIface);
            return;
        }

        // Reset checksum now that TTL is decremented
        ipPacket.resetChecksum();

        // Check if packet is destined for one of router's interfaces
        for (Iface iface : this.interfaces.values()) {
            if (ipPacket.getDestinationAddress() == iface.getIpAddress()) {
                return;
            }
        }

        // Do route lookup and forward
        this.forwardIpPacket(etherPacket, inIface);
    }

    private void forwardIpPacket(Ethernet etherPacket, Iface inIface) {
        // Make sure it's an IP packet
        if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4) {
            return;
        }
        System.out.println("Forward IP packet");

        // Get IP header
        IPv4 ipPacket = (IPv4) etherPacket.getPayload();
        int dstAddr = ipPacket.getDestinationAddress();

        // Find matching route table entry 
        RouteEntry bestMatch = this.routeTable.lookup(dstAddr);

        // If no entry matched, do nothing
        if (null == bestMatch) {
            return;
        }

        // Make sure we don't sent a packet back out the interface it came in
        Iface outIface = bestMatch.getInterface();
        if (outIface == inIface) {
            return;
        }

        // Set source MAC address in Ethernet header
        etherPacket.setSourceMACAddress(outIface.getMacAddress().toBytes());

        // If no gateway, then nextHop is IP destination
        int nextHop = bestMatch.getGatewayAddress();
        if (0 == nextHop) {
            nextHop = dstAddr;
        }

        // Set destination MAC address in Ethernet header
        ArpEntry arpEntry = this.arpCache.lookup(nextHop);
        if (null == arpEntry) {
            return;
        }
        etherPacket.setDestinationMACAddress(arpEntry.getMac().toBytes());

        this.sendPacket(etherPacket, outIface);
    }

    private Ethernet getICMPMessage(Iface inIface, IPv4 ipPacket, byte type, byte code) {
        Ethernet ethernet = new Ethernet();
        IPv4 iPv4 = new IPv4();
        ICMP icmp = new ICMP();
        Data data = new Data();

        ethernet.setEtherType(Ethernet.TYPE_IPv4);
        ethernet.setSourceMACAddress(inIface.getMacAddress().toBytes());
        ethernet.setDestinationMACAddress(arpCache.lookup(ipPacket.getSourceAddress()).getMac().toBytes());
        ethernet.setPayload(iPv4);

        iPv4.setTtl((byte) 64);
        iPv4.setProtocol(IPv4.PROTOCOL_ICMP);
        iPv4.setSourceAddress(inIface.getIpAddress());
        iPv4.setDestinationAddress(ipPacket.getSourceAddress());
        iPv4.setPayload(icmp);

        icmp.setIcmpType(type);
        icmp.setIcmpCode(code);
        icmp.setPayload(data);

        int length = 4 + ipPacket.getHeaderLength() * 4 + 8;
        byte[] dataBytes = new byte[length];
        ByteBuffer byteBuffer = ByteBuffer.wrap(dataBytes);
        byteBuffer.putInt(0);
        byte[] ipHeader = ipPacket.serialize();
        byteBuffer.put(Arrays.copyOfRange(ipHeader, 0, ipPacket.getHeaderLength() * 4));
        byteBuffer.put(Arrays.copyOfRange(ipHeader, 0, 8));
        byteBuffer.rewind();
        data.setData(dataBytes);

        System.err.println("src: " + HexString.toHexString(iPv4.getSourceAddress()) + "\tdst: " + HexString.toHexString(iPv4.getDestinationAddress()));
        return ethernet;
    }
}
