
import java.io.*;
import java.util.*;
import java.nio.file.Path;
import java.net.*;

public class GetHandlerThread implements Runnable {

    private int terminateID;
    private Socket socket;
    private List<String> commandArgs;
	private ClientFTP clientFtp;
	private Path serverPath, path;

	
	
	//inputstream outputstream

	private InputStreamReader inputStreamReader;
	private BufferedReader bufferedReader;
	private DataInputStream dataInputStream; 
	private OutputStream outputStream;
	private DataOutputStream dataOutputStream;

    //Constructor
	
	public GetHandlerThread(ClientFTP clientFtp, String machineip, int nPort, List<String> commandArgs, Path serverPath, Path path) throws Exception {
		this.clientFtp = clientFtp;
		this.commandArgs = commandArgs;
		this.serverPath = serverPath;
		this.path = path;
		
		InetAddress ip = InetAddress.getByName(machineip);
		socket = new Socket();
		socket.connect(new InetSocketAddress(ip.getHostAddress(), nPort), 2000);
		
		inputStreamReader = new InputStreamReader(socket.getInputStream());
		bufferedReader = new BufferedReader(inputStreamReader);
		dataInputStream = new DataInputStream(socket.getInputStream());
		outputStream = socket.getOutputStream();
		dataOutputStream = new DataOutputStream(outputStream);
	}
	
	public void receiveFile() throws Exception {
		//moving the file already being moved
		if (!clientFtp.move(serverPath.resolve(commandArgs.get(1)))) {
			System.out.println("Already getting the file from server");
			return;
		}
		
		//send command to server
		dataOutputStream.writeBytes("get " + serverPath.resolve(commandArgs.get(1)) + "\n");
		
		String errMsg;
		if (!(errMsg = bufferedReader.readLine()).equals("")) {
			System.out.println(errMsg);
			return;
		}
		
		//recieve terminate id from server
		try {
			terminateID = Integer.parseInt(bufferedReader.readLine());
		} catch(Exception e) {
			System.out.println("Terminate ID not valid");
		}
		System.out.println("TerminateID: " + terminateID);
		
		//Set lock on client side for Get operation
		clientFtp.moveIn(serverPath.resolve(commandArgs.get(1)), terminateID);
		
		if (clientFtp.abortGet(path.resolve(commandArgs.get(1)), serverPath.resolve(commandArgs.get(1)), terminateID)) return;
		
		//fetch file size
		byte[] fileSizeBuffer = new byte[8];
		dataInputStream.read(fileSizeBuffer);
		ByteArrayInputStream bais = new ByteArrayInputStream(fileSizeBuffer);
		DataInputStream in = new DataInputStream(bais);
		long fileSize = in.readLong();
		
		if (clientFtp.abortGet(path.resolve(commandArgs.get(1)), serverPath.resolve(commandArgs.get(1)), terminateID)) return;
		
		//get content from file
		FileOutputStream fileOutputStream = new FileOutputStream(new File(commandArgs.get(1)));
		int count = 0;
		byte[] buffer = new byte[8192];
		long bytesReceived = 0;
		while(bytesReceived < fileSize) {
			if (clientFtp.abortGet(path.resolve(commandArgs.get(1)), serverPath.resolve(commandArgs.get(1)), terminateID)) {
				fileOutputStream.close();
				return;
			}
			count = dataInputStream.read(buffer);
			fileOutputStream.write(buffer, 0, count);
			bytesReceived += count;
		}
		fileOutputStream.close();
		
		//Release lock for client
		clientFtp.moveOut(serverPath.resolve(commandArgs.get(1)), terminateID);
	}
	
	public void run() {
		try {
			receiveFile();
			Thread.sleep(100);
			dataOutputStream.writeBytes("quit" + "\n");
		} catch (Exception e) {
			System.out.println("Error in getting the file from server");
		}
	}
}

