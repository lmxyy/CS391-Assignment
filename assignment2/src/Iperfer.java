import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

import java.io.IOException;

public class Iperfer {
    private static class Option {
        public Integer mode;
        public String hostname;
        public Integer port;
        public Double time;

        public Option(Namespace namespace) throws Exception {
            if (namespace.get("h") != null)
                this.hostname = (String) namespace.get("h");
            else this.hostname = null;
            if (namespace.get("p") != null)
                this.port = (Integer) namespace.get("p");
            else this.port = null;
            if (namespace.get("t") != null)
                this.time = (Double) namespace.get("t");
            else this.time = null;
            if (((Boolean) namespace.get("c")) == true && ((Boolean) namespace.get("s")) == false) {
                this.mode = 0;
                if (this.hostname == null || this.port == null || this.time == null)
                    throw new Exception("Error: missing or additional arguments");
                else if (this.port < 1024 || this.port > 65536)
                    throw new Exception("Error: port number must be in the range 1024 to 65535");
            } else if (((Boolean) namespace.get("c")) == false && ((Boolean) namespace.get("s")) == true) {
                this.mode = 1;
                if (this.hostname != null || this.port == null || this.time != null)
                    throw new Exception("Error: missing or additional arguments");
                else if (this.port < 1024 || this.port > 65536)
                    throw new Exception("Error: port number must be in the range 1024 to 65535");
            } else {
                this.mode = -1;
                throw new Exception("Error: missing or additional arguments");
            }
        }

        public void handle() throws IOException {
            assert mode != -1;
            if (mode == 0) {
                System.out.println("Client mode.");
                Client client = new Client(hostname, port, time);
                client.sendData();
            } else {
                System.out.println("Server mode.");
                Server server = new Server(port);
                server.listenClients();
            }
        }
    }

    public static void main(String[] args) {
        ArgumentParser parser = ArgumentParsers.newFor("iperfer").addHelp(false).build().version("${prog} 1.0");
        parser.addArgument("--help").action(Arguments.help());
        parser.addArgument("-c").action(Arguments.storeTrue()).
                help("indicate this is the iperf client which should generate data");
        parser.addArgument("-s").action(Arguments.storeTrue()).
                help("indicate this is the iperf server which should consume data");
        parser.addArgument("-h").metavar("<server hostname>").type(String.class).
                help("<server hostname> is the hostname or IP address of the iperf server which will consume data");
        parser.addArgument("-p").metavar("<port>").type(Integer.class).
                help("<port> is the port on which the remote host is waiting to consume data or the port on which the host is waiting to consume data; the port should be in the range [1024,65535]");
        parser.addArgument("-t").metavar("<time>").type(Double.class).
                help("<time> is the duration in seconds for which data should be generated\n");
        try {
            Namespace namespace = parser.parseArgs(args);
            Option option = new Option(namespace);
            option.handle();
        } catch (ArgumentParserException e) {
            parser.handleError(e);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(-1);
        }
    }
}
