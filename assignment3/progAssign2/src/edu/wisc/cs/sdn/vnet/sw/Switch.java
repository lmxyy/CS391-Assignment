package edu.wisc.cs.sdn.vnet.sw;

import net.floodlightcontroller.packet.Ethernet;
import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;
import net.floodlightcontroller.packet.MACAddress;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Aaron Gember-Jacobson
 */
public class Switch extends Device {
    /**
     * Creates a router for a specific host.
     *
     * @param host hostname for the router
     */

    private final long agingTime = 3600000;

    private static class SwitchTableEntry {
        public MACAddress address;
        public Iface iface;
        public long time;

        public SwitchTableEntry(MACAddress address, Iface iface, long time) {
            this.address = address;
            this.iface = iface;
            this.time = time;
        }
    }

    private List<SwitchTableEntry> switchTable;

    public Switch(String host, DumpFile logfile) {
        super(host, logfile);
        switchTable = new ArrayList<>();
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
        /* TODO: Handle packets, necessary to modify the vlan?              */
        MACAddress sourceMAC = etherPacket.getSourceMAC();
        MACAddress destinationMAC = etherPacket.getDestinationMAC();
        long currentTime = System.currentTimeMillis();
        for (int i = 0; i < switchTable.size(); ++i) {
            SwitchTableEntry entry = switchTable.get(i);
            if (currentTime - entry.time > agingTime)
                switchTable.remove(i--);
        }
        switchTable.add(new SwitchTableEntry(sourceMAC, inIface, currentTime));

        boolean found = false;
        for (SwitchTableEntry entry : switchTable) {
            if (entry.address == destinationMAC) {
                found = true;
                if (entry.iface != inIface)
                    this.sendPacket(etherPacket, entry.iface);
            }
        }
        if (!found) {
            this.getInterfaces().values().forEach(iface -> {
                if (iface != inIface)
                    this.sendPacket(etherPacket, iface);
            });
        }
        /********************************************************************/
    }
}
