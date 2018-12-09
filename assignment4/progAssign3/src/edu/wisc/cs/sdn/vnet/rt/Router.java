package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.*;
import org.openflow.util.HexString;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

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
    private class PacketIface {
        BasePacket packet;
        Iface inIface, outIface;

        public PacketIface(BasePacket packet, Iface inIface, Iface outIface) {
            this.packet = packet;
            this.inIface = inIface;
            this.outIface = outIface;
        }

        public void sendIcmpMessage(byte type, byte code, boolean echo) {
            IPv4 ipPacket = (IPv4) packet.getPayload();
            Ethernet icmpMessage = getIcmpMessage(inIface, ipPacket, (byte) 3, (byte) 1, false);
            sendPacket(icmpMessage, inIface);
        }

        public void sendIpPacket(MACAddress mac) {
            Ethernet etherPacket = (Ethernet) packet;
            etherPacket.setDestinationMACAddress(mac.toBytes());
            sendPacket(etherPacket, outIface);
        }
    }

    private AtomicReference<ArpCache> arpCache;
    private AtomicReference<Map<Integer, Queue<PacketIface>>> mapQueues;

    private class WaitArpReply implements Runnable {
        Ethernet etherPacket;
        Iface outIface;
        int nextHop;
        AtomicReference<Map<Integer, Queue<PacketIface>>> mapQueues;

        public WaitArpReply(Ethernet etherPacket, Iface outIface, int nextHop, AtomicReference<Map<Integer, Queue<PacketIface>>> mapQueues) {
            this.etherPacket = etherPacket;
            this.outIface = outIface;
            this.nextHop = nextHop;
            this.mapQueues = mapQueues;
        }

        public void run() {
            for (int i = 0; i < 3; ++i) {
                Ethernet arpMessage = getArpMessage(etherPacket, outIface, nextHop);
                sendPacket(etherPacket, outIface);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                ArpEntry arpEntry = arpCache.get().lookup(nextHop);
                if (arpEntry != null) return;
            }
            Queue<PacketIface> queue = mapQueues.get().get(nextHop);
            if (queue == null) return;
//            queue.forEach(packetIface -> packetIface.sendIcmpMessage((byte) 3, (byte) 1, false));
            for (PacketIface packetIface : queue) {
                packetIface.sendIcmpMessage((byte) 3, (byte) 1, false);
            }
        }
    }

    /**
     * Creates a router for a specific host.
     *
     * @param host hostname for the router
     */
    public Router(String host, DumpFile logfile) {
        super(host, logfile);
        this.routeTable = new RouteTable();
        this.arpCache = new AtomicReference<>(new ArpCache());
        this.mapQueues = new AtomicReference<>(new HashMap<>());
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
        if (!arpCache.get().load(arpCacheFile)) {
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
            case Ethernet.TYPE_ARP:
                this.handleArpPacket(etherPacket, inIface);
                break;
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
            Ethernet icmpMessage = getIcmpMessage(inIface, ipPacket, (byte) 11, (byte) 0, false);
            this.sendPacket(icmpMessage, inIface);
            return;
        }

        // Reset checksum now that TTL is decremented
        ipPacket.resetChecksum();

        // Check if packet is destined for one of router's interfaces
        for (Iface iface : this.interfaces.values()) {
            if (ipPacket.getDestinationAddress() == iface.getIpAddress()) {
//                Destination port unreachable ICMP
                if (ipPacket.getProtocol() == IPv4.PROTOCOL_TCP || ipPacket.getProtocol() == IPv4.PROTOCOL_UDP) {
                    Ethernet icmpMessage = getIcmpMessage(inIface, ipPacket, (byte) 3, (byte) 3, false);
                    this.sendPacket(icmpMessage, inIface);
                } else if (ipPacket.getProtocol() == IPv4.PROTOCOL_ICMP && ((ICMP) ipPacket.getPayload()).getIcmpType() == 8) {
                    Ethernet icmpMessage = getIcmpMessage(inIface, ipPacket, (byte) 0, (byte) 0, true);
                    this.sendPacket(icmpMessage, inIface);
                }
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
        System.out.println("Get IP header.");

        // Get IP header
        IPv4 ipPacket = (IPv4) etherPacket.getPayload();
        int dstAddr = ipPacket.getDestinationAddress();

        // Find matching route table entry
        System.out.println("Find matching route table entry.");
        RouteEntry bestMatch = this.routeTable.lookup(dstAddr);

        // If no entry matched, do nothing
        if (null == bestMatch) {
//            Destination net unreachable ICMP
            Ethernet icmpMessage = getIcmpMessage(inIface, ipPacket, (byte) 3, (byte) 0, false);
            this.sendPacket(icmpMessage, inIface);
            return;
        }

        // Make sure we don't sent a packet back out the interface it came in
        Iface outIface = bestMatch.getInterface();
//        Destination port unreachable ICMP

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
        System.err.println("heiheihei.");
        ArpEntry arpEntry = this.arpCache.get().lookup(nextHop);
        if (null == arpEntry) {
            System.err.println("null arp entry.");
            Thread thread = null;
            if (!mapQueues.get().containsKey(nextHop)) {
                mapQueues.get().put(nextHop, new LinkedBlockingQueue<PacketIface>());
                WaitArpReply waitArpReply = new WaitArpReply(etherPacket, outIface, nextHop, mapQueues);
                thread = new Thread(waitArpReply);
            }
            mapQueues.get().get(nextHop).add(new PacketIface(etherPacket, inIface, outIface));
            if (thread != null) thread.start();
            return;
        }
        etherPacket.setDestinationMACAddress(arpEntry.getMac().toBytes());

        this.sendPacket(etherPacket, outIface);
    }

    private Ethernet getIcmpMessage(Iface inIface, IPv4 ipPacket, byte type, byte code, boolean echo) {
        Ethernet ethernet = new Ethernet();
        IPv4 iPv4 = new IPv4();
        ICMP icmp = new ICMP();
        Data data = new Data();

        ethernet.setEtherType(Ethernet.TYPE_IPv4);
        ethernet.setSourceMACAddress(inIface.getMacAddress().toBytes());
        ethernet.setDestinationMACAddress(arpCache.get().lookup(ipPacket.getSourceAddress()).getMac().toBytes());
        ethernet.setPayload(iPv4);

        iPv4.setTtl((byte) 64);
        iPv4.setProtocol(IPv4.PROTOCOL_ICMP);
        if (echo) iPv4.setSourceAddress(ipPacket.getDestinationAddress());
        else iPv4.setSourceAddress(inIface.getIpAddress());
        iPv4.setDestinationAddress(ipPacket.getSourceAddress());
        iPv4.setPayload(icmp);

        icmp.setIcmpType(type);
        icmp.setIcmpCode(code);
        icmp.setPayload(data);

        if (echo) data.setData(ipPacket.getPayload().getPayload().serialize());
        else {
            int length = 4 + ipPacket.getHeaderLength() * 4 + 8;
            byte[] dataBytes = new byte[length];
            ByteBuffer byteBuffer = ByteBuffer.wrap(dataBytes);
            byteBuffer.putInt(0);
            byte[] ipHeader = ipPacket.serialize();
            byteBuffer.put(Arrays.copyOfRange(ipHeader, 0, ipPacket.getHeaderLength() * 4 + 8));
            byteBuffer.rewind();
            data.setData(dataBytes);
        }
        return ethernet;
    }

    private void handleArpPacket(Ethernet etherPacket, Iface inIface) {
        ARP arpPacket = (ARP) etherPacket.getPayload();
        System.out.println("Handle ARP packet");
        if (arpPacket.getOpCode() == ARP.OP_REQUEST) {
            int targetIp = ByteBuffer.wrap(arpPacket.getTargetProtocolAddress()).getInt();
            if (targetIp == inIface.getIpAddress()) {
                Ethernet arpMessage = getArpMessage(etherPacket, inIface, 0);
                this.sendPacket(arpMessage, inIface);
            }
        } else if (arpPacket.getOpCode() == ARP.OP_REPLY) {
            int senderIp = ByteBuffer.wrap(arpPacket.getSenderProtocolAddress()).getInt();
            MACAddress senderMac = MACAddress.valueOf(arpPacket.getSenderHardwareAddress());
            arpCache.get().insert(senderMac, senderIp);
            Queue<PacketIface> queue = mapQueues.get().get(senderIp);
            mapQueues.get().remove(senderIp);
            if (queue == null) return;
//            queue.forEach(packetIface -> packetIface.sendIpPacket(senderMac));
            for (PacketIface packetIface : queue) {
                packetIface.sendIpPacket(senderMac);
            }
        } else {
            System.err.println("Unknown ARP packet OP code.");
            return;
        }
    }

    private Ethernet getArpMessage(Ethernet etherPacket, Iface iface, int ip) {
        ARP arpPacket = (ARP) etherPacket.getPayload();

        Ethernet ethernet = new Ethernet();
        ARP arp = new ARP();

        ethernet.setEtherType(Ethernet.TYPE_ARP);
        ethernet.setSourceMACAddress(iface.getMacAddress().toBytes());
        if (ip != 0) ethernet.setDestinationMACAddress("FF:FF:FF:FF:FF:FF");
        else ethernet.setDestinationMACAddress(etherPacket.getSourceMACAddress());
        ethernet.setPayload(arp);

        arp.setHardwareType(ARP.HW_TYPE_ETHERNET);
        arp.setProtocolType(ARP.PROTO_TYPE_IP);
        arp.setHardwareAddressLength((byte) Ethernet.DATALAYER_ADDRESS_LENGTH);
        arp.setProtocolAddressLength((byte) 4);
        if (ip != 0) arp.setOpCode(ARP.OP_REQUEST);
        else arp.setOpCode(ARP.OP_REPLY);
        arp.setSenderHardwareAddress(iface.getMacAddress().toBytes());
        arp.setSenderProtocolAddress(iface.getIpAddress());
        if (ip != 0) arp.setTargetHardwareAddress(MACAddress.valueOf(0).toBytes());
        else arp.setTargetHardwareAddress(arpPacket.getSenderHardwareAddress());
        if (ip != 0) arp.setTargetProtocolAddress(ip);
        else arp.setTargetProtocolAddress(arpPacket.getSenderProtocolAddress());

        return ethernet;
    }
}
