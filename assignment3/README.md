# Mininet based Programming Assignments: Link & Network Layer Forwarding

## Requirement

1. Install required packages

   ```bash
   sudo apt-get update
   sudo apt-get install -y python-dev python-setuptools flex bison ant openjdk-7-jdk git screen
   ```

2. Install ltprotocol

   ```bash
   git clone git://github.com/dound/ltprotocol.git
   cd ltprotocol
   sudo python setup.py install
   ```

   Maybe you need to update your ```setuptools```:

   ````bash
   python -m pip install --upgrade pip setuptools wheel
   ````

3. Checkout the appropriate version of POX

   ```bash
   git clone https://github.com/noxrepo/pox
   cd pox
   git checkout f95dd1
   ```

4. Symlink POX and configure the POX modules

   ```bash
   cd /path/to/progAssign2
   ln -s /path/to/pox
   ./config.sh
   ```

## Compilation

First you need to compile the code by running the following command:

```bash
cd /path/to/progAssign2
ant
```

## Execution

1. Start Mininet emulation by running the following commands. You need to choose one topo from directory ```topos``` and then replace `yourtopo` in the command with the name of topo you choose.

   ```bash
   cd /path/to/progAssign2
   sudo python ./run_mininet.py topos/yourtopo
   ```

   Keep this terminal open.

2. Run the pox in the same directory:

   ```bash
   ./run_pox.sh
   ```

   Also keep this terminal open.

3. For every switch in your topo, open one terminal and run the following command:

   ```bash
   cd /path/to/progAssign2
   java -jar VirtualNetwork.jar -v [switch]
   ```

   You need to replace the `[switch]` with the specific switch name such as `s1`.

4. For every router in your topo, open one terminal and run the following command:

   ```bash
   cd /path/to/progAssign2
   java -jar VirtualNetwork.jar -v [router] -r [route_table] -a arp_cache
   ```

   For example, if you are running the above command for router `r1`, the command will be

   ```bash
   cd /path/to/progAssign2
   java -jar VirtualNetwork.jar -v r1 -r rtable.r1 -a arp_cache
   ```

5. Turn back to your Mininet terminal. Now you can run the `ping` command, such as

   ```bash
   h1 ping -c 2 h2
   ```
