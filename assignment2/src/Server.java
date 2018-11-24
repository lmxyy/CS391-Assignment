import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {
    private ServerSocket serverSocket;

    public Server(int port) {
        try {
            serverSocket = new ServerSocket(port);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("The program cannot build a server socket.");
            System.exit(-1);
        }
    }

    public void listenClients() throws IOException {
        Socket socket = serverSocket.accept();

        long startTime = System.currentTimeMillis();
        InputStream in = new DataInputStream(socket.getInputStream());
        byte[] clientData = new byte[1000];

        long bytesReceived = 0;
        int len;
        while ((len = in.read(clientData)) != -1)
            bytesReceived += len;

        long totalTime = System.currentTimeMillis() - startTime;
        double rate = calculateRate(bytesReceived, totalTime);
//        System.out.print("received=" + bytesReceived / 1000.0 + "KB rate=" + rate + "Mbps");
        System.out.printf("received=%d KB\trate=%.3f Mbps\n", bytesReceived / 1000, rate);
    }

    private double calculateRate(long bytesReceived, long totalTime) {
        return (8.0 * bytesReceived / 1000.0) / totalTime;
    }
}
