package edu.wisc.cs.sdn.apps.l3routing;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import com.kenai.jffi.Array;
import edu.wisc.cs.sdn.apps.util.SwitchCommands;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.instruction.OFInstruction;
import org.openflow.protocol.instruction.OFInstructionActions;
import org.openflow.protocol.instruction.OFInstructionApplyActions;
import org.python.antlr.op.In;
import org.sdnplatform.sync.internal.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.wisc.cs.sdn.apps.util.Host;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitch.PortChangeType;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.ImmutablePort;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceListener;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryListener;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.routing.Link;

public class L3Routing implements IFloodlightModule, IOFSwitchListener,
        ILinkDiscoveryListener, IDeviceListener {
    public static final String MODULE_NAME = L3Routing.class.getSimpleName();

    // Interface to the logging system
    private static Logger log = LoggerFactory.getLogger(MODULE_NAME);

    // Interface to Floodlight core for interacting with connected switches
    private IFloodlightProviderService floodlightProv;

    // Interface to link discovery service
    private ILinkDiscoveryService linkDiscProv;

    // Interface to device manager service
    private IDeviceService deviceProv;

    // Switch table in which rules should be installed
    public static byte table;

    // Map of hosts to devices
    private Map<IDevice, Host> knownHosts;

    private Map<Host, Map<IOFSwitch, Path>> pathTable = new HashMap<Host, Map<IOFSwitch, Path>>();

    private class Entry {
        public Integer outPort, inPort;
        IOFSwitch iofSwitch;

        public Entry(Integer outPort, Integer inPort, IOFSwitch iofSwitch) {
            this.outPort = outPort;
            this.inPort = inPort;
            this.iofSwitch = iofSwitch;
        }
    }

    private class Graph {
        public Map<IOFSwitch, ArrayList<Entry>> toit = new HashMap<IOFSwitch, ArrayList<Entry>>();
        public Collection<Host> hosts = new ArrayList<Host>();
        public Map<Long, IOFSwitch> iofSwitches = new HashMap<Long, IOFSwitch>();

        public void init(Collection<Host> hosts, Map<Long, IOFSwitch> iofSwitches, Collection<Link> links) {
            toit.clear();
            this.hosts = hosts;
            this.iofSwitches = iofSwitches;
            for (IOFSwitch iofSwitch : iofSwitches.values()) {
                toit.put(iofSwitch, new ArrayList<Entry>());
            }
            for (Link link : links) {
                IOFSwitch u = iofSwitches.get(link.getSrc()), v = iofSwitches.get(link.getDst());
                Integer outPort = link.getSrcPort(), inPort = link.getDstPort();
                toit.get(u).add(new Entry(outPort, inPort, v));
            }
        }
    }

    Graph graph = new Graph();

    private class Path {

        private ArrayList<Pair<IOFSwitch, Integer>> path = new ArrayList<Pair<IOFSwitch, Integer>>();

        public Path() {
        }

        public Path(IOFSwitch iofSwitch, Integer port) {
            path.add(new Pair<IOFSwitch, Integer>(iofSwitch, port));
        }

        public void add(IOFSwitch iofSwitch, Integer port) {
            path.add(new Pair<IOFSwitch, Integer>(iofSwitch, port));
        }

        public int size() {
            return path.size();
        }

        public Pair<IOFSwitch, Integer> end() {
            if (size() == 0) return null;
            else return path.get(size() - 1);
        }

        public Path copyAndAdd(IOFSwitch iofSwitch, Integer port) {
            Path ret = new Path();
            for (Pair<IOFSwitch, Integer> entry : path) {
                ret.add(entry.getKey(), entry.getValue());
            }
            ret.add(iofSwitch, port);
            return ret;
        }
    }

    /**
     * Loads dependencies and initializes data structures.
     */
    @Override
    public void init(FloodlightModuleContext context)
            throws FloodlightModuleException {
        log.info(String.format("Initializing %s...", MODULE_NAME));
        Map<String, String> config = context.getConfigParams(this);
        table = Byte.parseByte(config.get("table"));

        this.floodlightProv = context.getServiceImpl(
                IFloodlightProviderService.class);
        this.linkDiscProv = context.getServiceImpl(ILinkDiscoveryService.class);
        this.deviceProv = context.getServiceImpl(IDeviceService.class);

        this.knownHosts = new ConcurrentHashMap<IDevice, Host>();
    }

    private void bfs(Host source) {
        if (!source.isAttachedToSwitch()) return;
        Map<IOFSwitch, Path> path = new HashMap<IOFSwitch, Path>();
        Queue<IOFSwitch> queue = new LinkedBlockingQueue<IOFSwitch>();
        queue.add(source.getSwitch());
        path.put(source.getSwitch(), new Path(source.getSwitch(), source.getPort()));
        while (!queue.isEmpty()) {
            IOFSwitch now = queue.poll();
            for (Entry entry : graph.toit.get(now)) {
                IOFSwitch iofSwitch = entry.iofSwitch;
                if (!path.containsKey(iofSwitch)) {
                    path.put(iofSwitch, path.get(now).copyAndAdd(iofSwitch, entry.inPort));
                    queue.add(iofSwitch);
                }
            }
        }
        pathTable.put(source, path);
    }

    private void bellmanFord() {
        init();
        Collection<Host> hosts = getHosts();
        Map<Long, IOFSwitch> switches = getSwitches();
        Collection<Link> links = getLinks();
        graph.init(hosts, switches, links);
        for (Host host : hosts) {
            bfs(host);
        }
        for (Host dst : hosts) {
            if (pathTable.get(dst) == null) continue;
            for (Map.Entry<IOFSwitch, Path> entry : pathTable.get(dst).entrySet()) {
                IOFSwitch iofSwitch = entry.getKey();
                Path path = entry.getValue();
                OFMatch ofMatch = new OFMatch();
                ofMatch.setDataLayerType(OFMatch.ETH_TYPE_IPV4);
                if (dst.getIPv4Address() == null) continue;
                ofMatch.setNetworkDestination(dst.getIPv4Address());
                OFActionOutput ofActionOutput = new OFActionOutput(path.end().getValue());
                OFInstructionApplyActions ofInstructionApplyActions = new OFInstructionApplyActions(new ArrayList<OFAction>(Collections.singletonList(ofActionOutput)));
                SwitchCommands.installRule(iofSwitch, table, SwitchCommands.DEFAULT_PRIORITY, ofMatch, new ArrayList<OFInstruction>(Collections.singletonList(ofInstructionApplyActions)));
            }
        }
    }

    private void init() {
        for (Host dst : pathTable.keySet()) {
//            System.err.println(dst.getName());
            for (Map.Entry<IOFSwitch, Path> entry : pathTable.get(dst).entrySet()) {
                IOFSwitch iofSwitch = entry.getKey();
                OFMatch ofMatch = new OFMatch();
                ofMatch.setDataLayerType(OFMatch.ETH_TYPE_IPV4);
                if (dst.getIPv4Address() == null) continue;
                ofMatch.setNetworkDestination(dst.getIPv4Address());
                SwitchCommands.removeRules(iofSwitch, table, ofMatch);
            }
        }
        pathTable.clear();
    }

    /**
     * Subscribes to events and performs other startup tasks.
     */
    @Override
    public void startUp(FloodlightModuleContext context)
            throws FloodlightModuleException {
        log.info(String.format("Starting %s...", MODULE_NAME));
        this.floodlightProv.addOFSwitchListener(this);
        this.linkDiscProv.addListener(this);
        this.deviceProv.addListener(this);

        /*********************************************************************/
        /* TODO: Initialize variables or perform startup tasks, if necessary */
        /*********************************************************************/
    }

    /**
     * Get a list of all known hosts in the network.
     */
    private Collection<Host> getHosts() {
        return this.knownHosts.values();
    }

    /**
     * Get a map of all active switches in the network. Switch DPID is used as
     * the key.
     */
    private Map<Long, IOFSwitch> getSwitches() {
        return floodlightProv.getAllSwitchMap();
    }

    /**
     * Get a list of all active links in the network.
     */
    private Collection<Link> getLinks() {
        return linkDiscProv.getLinks().keySet();
    }

    /**
     * Event handler called when a host joins the network.
     *
     * @param device information about the host
     */
    @Override
    public void deviceAdded(IDevice device) {
        Host host = new Host(device, this.floodlightProv);
        // We only care about a new host if we know its IP
        if (host.getIPv4Address() != null) {
            log.info(String.format("Host %s added", host.getName()));
            this.knownHosts.put(device, host);

            /*****************************************************************/
            /* TODO: Update routing: add rules to route to new host          */
            bellmanFord();
            /*****************************************************************/
        }
    }

    /**
     * Event handler called when a host is no longer attached to a switch.
     *
     * @param device information about the host
     */
    @Override
    public void deviceRemoved(IDevice device) {
        Host host = this.knownHosts.get(device);
        if (null == host) {
            return;
        }
        this.knownHosts.remove(host);

        log.info(String.format("Host %s is no longer attached to a switch",
                host.getName()));

        /*********************************************************************/
        /* TODO: Update routing: remove rules to route to host               */
        bellmanFord();
        /*********************************************************************/
    }

    /**
     * Event handler called when a host moves within the network.
     *
     * @param device information about the host
     */
    @Override
    public void deviceMoved(IDevice device) {
        Host host = this.knownHosts.get(device);
        if (null == host) {
            host = new Host(device, this.floodlightProv);
            this.knownHosts.put(device, host);
        }

        if (!host.isAttachedToSwitch()) {
            this.deviceRemoved(device);
            return;
        }
        log.info(String.format("Host %s moved to s%d:%d", host.getName(),
                host.getSwitch().getId(), host.getPort()));

        /*********************************************************************/
        /* TODO: Update routing: change rules to route to host               */
        bellmanFord();
        /*********************************************************************/
    }

    /**
     * Event handler called when a switch joins the network.
     *
     * @param switchId for the switch
     */
    @Override
    public void switchAdded(long switchId) {
        IOFSwitch sw = this.floodlightProv.getSwitch(switchId);
        log.info(String.format("Switch s%d added", switchId));

        /*********************************************************************/
        /* TODO: Update routing: change routing rules for all hosts          */
        bellmanFord();
        /*********************************************************************/
    }

    /**
     * Event handler called when a switch leaves the network.
     *
     * @param switchId for the switch
     */
    @Override
    public void switchRemoved(long switchId) {
        IOFSwitch sw = this.floodlightProv.getSwitch(switchId);
        log.info(String.format("Switch s%d removed", switchId));

        /*********************************************************************/
        /* TODO: Update routing: change routing rules for all hosts          */
        bellmanFord();
        /*********************************************************************/
    }

    /**
     * Event handler called when multiple links go up or down.
     *
     * @param updateList information about the change in each link's state
     */
    @Override
    public void linkDiscoveryUpdate(List<LDUpdate> updateList) {
        for (LDUpdate update : updateList) {
            // If we only know the switch & port for one end of the link, then
            // the link must be from a switch to a host
            if (0 == update.getDst()) {
                log.info(String.format("Link s%s:%d -> host updated",
                        update.getSrc(), update.getSrcPort()));
            }
            // Otherwise, the link is between two switches
            else {
                log.info(String.format("Link s%s:%d -> s%s:%d updated",
                        update.getSrc(), update.getSrcPort(),
                        update.getDst(), update.getDstPort()));
            }
        }

        /*********************************************************************/
        /* TODO: Update routing: change routing rules for all hosts          */
//        System.err.println("linkDiscoveryUpdate handler");
        bellmanFord();
        /*********************************************************************/
    }

    /**
     * Event handler called when link goes up or down.
     *
     * @param update information about the change in link state
     */
    @Override
    public void linkDiscoveryUpdate(LDUpdate update) {
        this.linkDiscoveryUpdate(Arrays.asList(update));
    }

    /**
     * Event handler called when the IP address of a host changes.
     *
     * @param device information about the host
     */
    @Override
    public void deviceIPV4AddrChanged(IDevice device) {
        this.deviceAdded(device);
    }

    /**
     * Event handler called when the VLAN of a host changes.
     *
     * @param device information about the host
     */
    @Override
    public void deviceVlanChanged(IDevice device) { /* Nothing we need to do, since we're not using VLANs */ }

    /**
     * Event handler called when the controller becomes the master for a switch.
     *
     * @param switchId for the switch
     */
    @Override
    public void switchActivated(long switchId) { /* Nothing we need to do, since we're not switching controller roles */ }

    /**
     * Event handler called when some attribute of a switch changes.
     *
     * @param switchId for the switch
     */
    @Override
    public void switchChanged(long switchId) { /* Nothing we need to do */ }

    /**
     * Event handler called when a port on a switch goes up or down, or is
     * added or removed.
     *
     * @param switchId for the switch
     * @param port     the port on the switch whose status changed
     * @param type     the type of status change (up, down, add, remove)
     */
    @Override
    public void switchPortChanged(long switchId, ImmutablePort port,
                                  PortChangeType type) { /* Nothing we need to do, since we'll get a linkDiscoveryUpdate event */ }

    /**
     * Gets a name for this module.
     *
     * @return name for this module
     */
    @Override
    public String getName() {
        return this.MODULE_NAME;
    }

    /**
     * Check if events must be passed to another module before this module is
     * notified of the event.
     */
    @Override
    public boolean isCallbackOrderingPrereq(String type, String name) {
        return false;
    }

    /**
     * Check if events must be passed to another module after this module has
     * been notified of the event.
     */
    @Override
    public boolean isCallbackOrderingPostreq(String type, String name) {
        return false;
    }

    /**
     * Tell the module system which services we provide.
     */
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        return null;
    }

    /**
     * Tell the module system which services we implement.
     */
    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService>
    getServiceImpls() {
        return null;
    }

    /**
     * Tell the module system which modules we depend on.
     */
    @Override
    public Collection<Class<? extends IFloodlightService>>
    getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> floodlightService =
                new ArrayList<Class<? extends IFloodlightService>>();
        floodlightService.add(IFloodlightProviderService.class);
        floodlightService.add(ILinkDiscoveryService.class);
        floodlightService.add(IDeviceService.class);
        return floodlightService;
    }
}
