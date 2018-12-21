# Mininet based Programming Assignments: Link & Network Layer Forwarding

## Requirement

1. Install required packages

   ```bash
   sudo apt-get update
   sudo apt-get install -y curl traceroute ant openjdk-7-jdk git iputils-arping
   ```

2. Download Floodlight:

   ```bash
   git clone https://bitbucket.org/sdnhub/floodlight-plus.git
   cd path/to/progAssign4
   ln -s path/to/floodlight-plus
   cd path/to/floodlight-plus
   patch -p1 < path/to/progAssign4/floodlight.patch
   cd path/to/progAssign4/floodlight.patch
   ./fixmininet.sh
   ```


