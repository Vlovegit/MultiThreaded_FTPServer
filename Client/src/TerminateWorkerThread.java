import java.io.*;
import java.net.*;

public class TerminateWorkerThread implements Runnable {


    private Socket socket;
    private int terminateID;
	private OutputStream outputStream;
	private DataOutputStream dataOutputStream;

	
	public TerminateWorkerThread(String machineip, int tPort, int terminateID) throws Exception {
		this.terminateID = terminateID;
		
		InetAddress ip = InetAddress.getByName(machineip);
		socket = new Socket();
		socket.connect(new InetSocketAddress(ip.getHostAddress(), tPort), 2000);
		
		outputStream = socket.getOutputStream();
		dataOutputStream = new DataOutputStream(outputStream);
	}
	
	public void run() {
		try {
			dataOutputStream.writeBytes("terminate " + terminateID + "\n");
		} catch (IOException e) {
			if (ClientThreadedMain.DEBUG_VARIABLE) System.out.println("Terminate Worker Thread");
		}
	}
}
