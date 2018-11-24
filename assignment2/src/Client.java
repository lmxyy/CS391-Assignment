import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;

public class Client {
    private String hostname;
    private int port;
    private double time;
    private final int DATA = 0;

    public Client(String hostname, int port, double time) {
        this.hostname = hostname;
        this.port = port;
        this.time = time;
    }

    public void sendData() throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(hostname, port));
        } catch (ConnectException e) {
            System.out.println("The program could not connect to the server.");
            System.exit(-1);
        }

        double maxTime = 1000 * time;

        byte[] dataBytes = ByteBuffer.allocate(1000).putInt(DATA).array();
        DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());

        long startTime = System.currentTimeMillis();
        long totalTime = 1;
        long bytesSent = 0;
        while (true) {
            long timeSpan = System.currentTimeMillis() - startTime;
            if (timeSpan > maxTime) break;
            totalTime = timeSpan;
            dataOutputStream.write(dataBytes);
            dataOutputStream.flush();
            bytesSent += dataBytes.length;
        }

        dataOutputStream.close();
        socket.close();
        double rate = calculateRate(bytesSent, totalTime);
//        System.out.println("sent=" + bytesSent / 1000.0 + "KB rate=" + 8 * rate / 1000.0 + "Mbps");
        System.out.printf("sent=%d KB\trate=%.3f Mbps\n", bytesSent / 1000, rate);
    }

    private double calculateRate(long bytesSent, long totalTime) {
        double rate = (8.0 * bytesSent / 1000.0) / totalTime;
        return rate;
    }
}
