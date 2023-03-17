
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.List;

public class PutHandlerThread implements Runnable {
	private InputStreamReader inputStreamReader;
	private BufferedReader bufferedReader;
	private OutputStream outputStream;
	private DataOutputStream dataOutputStream;
	private ClientFTP clientFTP;
	private Socket socket;
	private Path path, serverPath;
	private List<String> commandArgs;
	private int terminateID;
	
	
	
	public PutHandlerThread(ClientFTP clientFTP, String hostname, int nPort, List<String> commandArgs, Path serverPath) throws Exception {
		this.clientFTP = clientFTP;
		this.commandArgs = commandArgs;
		this.serverPath = serverPath;
		
		InetAddress ip = InetAddress.getByName(hostname);
		socket = new Socket();
		socket.connect(new InetSocketAddress(ip.getHostAddress(), nPort), 1000);
		
		inputStreamReader = new InputStreamReader(socket.getInputStream());
		bufferedReader = new BufferedReader(inputStreamReader);
		outputStream = socket.getOutputStream();
		dataOutputStream = new DataOutputStream(outputStream);
		
		path = Paths.get(System.getProperty("user.dir"));
	}

    public void sendFile() throws Exception {
		//send file to server
		if (!clientFTP.move(serverPath.resolve(commandArgs.get(1)))) {
			System.out.println("Already sending file to server");
			return;
		}
		
		//file not present at server
		if (Files.notExists(path.resolve(commandArgs.get(1)))) {
			System.out.println("No such file present at the client");
		} 
		//requested file is a directory
		else if (Files.isDirectory(path.resolve(commandArgs.get(1)))) {
			System.out.println("Cannot send file as it is a directory");
		}
		//send content of file to server
		else {
			//send command
			dataOutputStream.writeBytes("put " + serverPath.resolve(commandArgs.get(1)) + "\n");
			
			//wait for terminate ID
			try {
				terminateID = Integer.parseInt(bufferedReader.readLine());
			} catch(Exception e) {
				System.out.println("Invalid TerminateId");
			}
			System.out.println("TerminateId : " + terminateID);
			
			//Set lock on client side for Put operation
			clientFTP.moveIn(serverPath.resolve(commandArgs.get(1)), terminateID);
			
			if (clientFTP.abortPut(serverPath.resolve(commandArgs.get(1)), terminateID)) 
                return;
			
			
			bufferedReader.readLine();
			Thread.sleep(100);
			
			if (clientFTP.abortPut(serverPath.resolve(commandArgs.get(1)), terminateID)) return;
			
			byte[] buffer = new byte[1000];
			try {
				File file = new File(path.resolve(commandArgs.get(1)).toString());
				
				//send content in batches
				long fileSize = file.length();
				byte[] fileSizeBytes = ByteBuffer.allocate(8).putLong(fileSize).array();
				dataOutputStream.write(fileSizeBytes, 0, 8);
				
				if (clientFTP.abortPut(serverPath.resolve(commandArgs.get(1)), terminateID)) return;
				

				BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
				int count = 0;
				while((count = in.read(buffer)) > 0) {
					if (clientFTP.abortPut(serverPath.resolve(commandArgs.get(1)), terminateID)) {
						in.close();
						return;
					}
					dataOutputStream.write(buffer, 0, count);
				}
				
				in.close();
			} catch(Exception e){
				System.out.println("File transfer error");
			}
			
			//CLIENT side un-locking
			clientFTP.moveOut(serverPath.resolve(commandArgs.get(1)), terminateID);
		}
	}
	
	public void run() {
		try {
			sendFile();
			Thread.sleep(100);
			dataOutputStream.writeBytes("quit" + "\n");
		} catch (Exception e) {
			System.out.println("Put operation failed");
		}
	}
}
