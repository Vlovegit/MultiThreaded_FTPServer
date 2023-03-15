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
			System.out.println("The file is on the move!!!");
			return;
		}
		
		//send command to server
		dataOutputStream.writeBytes("get " + serverPath.resolve(commandArgs.get(1)) + "\n");
		
		//error messages
		String get_line;
		if (!(get_line = bufferedReader.readLine()).equals("")) {
			System.out.println(get_line);
			return;
		}
		
		//waiting to read terminate ID
		try {
			terminateID = Integer.parseInt(bufferedReader.readLine());
		} catch(Exception e) {
			if (ClientThreadedMain.DEBUG_VARIABLE) System.out.println("Terminate ID not valid");
		}
		System.out.println("TerminateID: " + terminateID);
		
		//locking client side
		clientFtp.moveIn(serverPath.resolve(commandArgs.get(1)), terminateID);
		
		if (clientFtp.abortGet(path.resolve(commandArgs.get(1)), serverPath.resolve(commandArgs.get(1)), terminateID)) return;
		
		//get file size
		byte[] fileSizeBuffer = new byte[8];
		dataInputStream.read(fileSizeBuffer);
		ByteArrayInputStream bais = new ByteArrayInputStream(fileSizeBuffer);
		DataInputStream in = new DataInputStream(bais);
		long fileSize = in.readLong();
		
		if (clientFtp.abortGet(path.resolve(commandArgs.get(1)), serverPath.resolve(commandArgs.get(1)), terminateID)) return;
		
		//receive the file
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
		
		//CLIENT side un-locking
		clientFtp.moveOut(serverPath.resolve(commandArgs.get(1)), terminateID);
	}
	
	public void run() {
		try {
			receiveFile();
			Thread.sleep(100);
			dataOutputStream.writeBytes("quit" + "\n");
		} catch (Exception e) {
			if (ClientThreadedMain.DEBUG_VARIABLE) System.out.println("ERROR IN GET HANDLER");
		}
	}
}

