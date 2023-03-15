import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.net.Socket;
import java.nio.ByteBuffer;

public class NormalWorkerThread {

    private ServerFTP serverFTP;
	private Socket nSocket;
	private Path path;
	private List<String> tokens;
	InputStreamReader isr;
	BufferedReader br;
	DataInputStream dataInputStream;
    DataOutputStream dataOutputStream;
	OutputStream os;
	
    public NormalWorkerThread(ServerFTP serverFTP, Socket nSocket) throws Exception {
		this.serverFTP = serverFTP;
		this.nSocket = nSocket;
		path = Paths.get(System.getProperty("user.dir"));
		isr = new InputStreamReader(nSocket.getInputStream());
		br = new BufferedReader(isr);
		dataInputStream = new DataInputStream(nSocket.getInputStream());
		os = nSocket.getOutputStream();
		dataOutputStream = new DataOutputStream(os);
	}

    public void get() throws Exception {
		//not a directory or file
		if (Files.notExists(path.resolve(tokens.get(1)))) {
			dataOutputStream.writeBytes("get: " + path.resolve(tokens.get(1)).getFileName() + ": No such file or directory" + "\n");
			return;
		} 
		//is a directory
		if (Files.isDirectory(path.resolve(tokens.get(1)))) {
			dataOutputStream.writeBytes("get: " + path.resolve(tokens.get(1)).getFileName() + ": Is a directory" + "\n");
			return;
		}
		
		//////////
		// LOCK //
		//////////
		int lockID = serverFTP.getIN(path.resolve(tokens.get(1)));
		if (lockID == -1) {
			dataOutputStream.writeBytes("get: " + path.resolve(tokens.get(1)).getFileName() + ": No such file or directory" + "\n");
			return;
		}
		
		//blank message
		dataOutputStream.writeBytes("\n");
		
		//send terminateID
		dataOutputStream.writeBytes(lockID + "\n");
		
		//need to figure
		Thread.sleep(100);
		
		if (serverFTP.terminateGET(path.resolve(tokens.get(1)), lockID)) {
			quit();
			return;
		}
		
		//transfer file
		byte[] buffer = new byte[1000];
		try {
			File file = new File(path.resolve(tokens.get(1)).toString());
			
			//write long filesize as first 8 bytes
			long fileSize = file.length();
			byte[] fileSizeBytes = ByteBuffer.allocate(8).putLong(fileSize).array();
			dataOutputStream.write(fileSizeBytes, 0, 8);
			
			if (serverFTP.terminateGET(path.resolve(tokens.get(1)), lockID)) {
				quit();
				return;
			}
			
			//write file
			BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
			int count = 0;
			while((count = in.read(buffer)) > 0) {
				if (serverFTP.terminateGET(path.resolve(tokens.get(1)), lockID)) {
					in.close();
					quit();
					return;
				}
				dataOutputStream.write(buffer, 0, count);
			}
			
			in.close();
		} catch(Exception e) {
			System.out.println("transfer error: " + tokens.get(1));
		}
		
		////////////
		// UNLOCK //
		////////////
		serverFTP.getOUT(path.resolve(tokens.get(1)), lockID);
	}

    public void put() throws Exception {
		//LOCK ID
		int lockID = serverFTP.putIN_ID(path.resolve(tokens.get(1)));
		System.out.println(lockID);
		
		//send message ID
		dataOutputStream.writeBytes(lockID + "\n");
		
		
		//////////
		// LOCK //
		//////////
		while (!serverFTP.putIN(path.resolve(tokens.get(1)), lockID))
			Thread.sleep(10);
		
		if (serverFTP.terminatePUT(path.resolve(tokens.get(1)), lockID)) {
			quit();
			return;
		}
		
		//can write
		dataOutputStream.writeBytes("\n");
		
		if (serverFTP.terminatePUT(path.resolve(tokens.get(1)), lockID)) {
			quit();
			return;
		}
		
		//get file size
		byte[] fileSizeBuffer = new byte[8];
		dataInputStream.read(fileSizeBuffer);
		ByteArrayInputStream bais = new ByteArrayInputStream(fileSizeBuffer);
		DataInputStream dis = new DataInputStream(bais);
		long fileSize = dis.readLong();
		
		if (serverFTP.terminatePUT(path.resolve(tokens.get(1)), lockID)) {
			quit();
			return;
		}
		
		//receive the file
		FileOutputStream f = new FileOutputStream(new File(tokens.get(1)).toString());
		int count = 0;
		byte[] buffer = new byte[1000];
		long bytesReceived = 0;
		while(bytesReceived < fileSize) {
			if (serverFTP.terminatePUT(path.resolve(tokens.get(1)), lockID)) {
				f.close();
				quit();
				return;
			}
			count = dataInputStream.read(buffer);
			f.write(buffer, 0, count);
			bytesReceived += count;
		}
		f.close();
		
		////////////
		// UNLOCK //
		////////////
		serverFTP.putOUT(path.resolve(tokens.get(1)), lockID);
	}

    public void quit() throws Exception {
		//close socket
		nSocket.close();
		throw new Exception();
	}

    public void run() {
		System.out.println(Thread.currentThread().getName() + " NormalWorker Started");
		exitThread:
		while (true) {
			try {
				//check every 10 ms for input
				while (!br.ready())
					Thread.sleep(10);
				
				//capture and parse input
				tokens = new ArrayList<String>();
				String command = br.readLine();
				Scanner tokenize = new Scanner(command);
				//gets command
				if (tokenize.hasNext())
				    tokens.add(tokenize.next());
				//gets rest of string after the command; this allows filenames with spaces: 'file1 test.txt'
				if (tokenize.hasNext())
					tokens.add(command.substring(tokens.get(0).length()).trim());
				tokenize.close();
				System.out.println(tokens.toString());
				
				//command selector
				switch(tokens.get(0)) {
					case "get": 	get();		break;
					case "put": 	put();		break;
					//case "delete": 	delete();	break;
					//case "ls": 		ls();		break;
					//case "cd": 		cd();		break;
					//case "mkdir": 	mkdir();	break;
					//case "pwd": 	pwd();		break;
					case "quit": 	quit();		break exitThread;
					default:
						System.out.println("invalid command");
				}
			} catch (Exception e) {
				break exitThread;
			}
		}
		System.out.println(Thread.currentThread().getName() + " NormalWorker Exited");
	}
    
}
