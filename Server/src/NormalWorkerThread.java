import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.net.Socket;
import java.nio.ByteBuffer;

public class NormalWorkerThread implements Runnable{

    private ServerFTP serverFTP;
	private Socket nSocket;
	private Path path;
	private List<String> commandargs;
	private String currentThreadDir;
	InputStreamReader isr;
	BufferedReader br;
	DataInputStream dataInputStream;
    DataOutputStream dataOutputStream;
	OutputStream os;
	
    public NormalWorkerThread(ServerFTP serverFTP, Socket nSocket) throws Exception {
		this.serverFTP = serverFTP;
		this.nSocket = nSocket;
		path = Paths.get(System.getProperty("user.dir"));
		currentThreadDir = System.getProperty("user.dir");
		isr = new InputStreamReader(nSocket.getInputStream());
		br = new BufferedReader(isr);
		dataInputStream = new DataInputStream(nSocket.getInputStream());
		os = nSocket.getOutputStream();
		dataOutputStream = new DataOutputStream(os);
	}

    public void sendFile() throws Exception {
		//not a directory or file
		if (Files.notExists(path.resolve(commandargs.get(1)))) {
			dataOutputStream.writeBytes(" No such file or directory exist at Server" + "\n");
			return;
		} 
		//is a directory
		if (Files.isDirectory(path.resolve(commandargs.get(1)))) {
			dataOutputStream.writeBytes("Requested file is a directory, cannot fetch it" + "\n");
			return;
		}
		
		// Fetch lock for Get Operation
		int lockId = serverFTP.getGetLock(path.resolve(commandargs.get(1)));
		if (lockId == -1) {
			dataOutputStream.writeBytes("Failed to acquire a get lock on the file" + "\n");
			return;
		}
		
		dataOutputStream.writeBytes("\n");
		
		//send terminateId back to the client
		dataOutputStream.writeBytes(lockId + "\n");
		
		Thread.sleep(100);
		
		if (serverFTP.abortGet(path.resolve(commandargs.get(1)), lockId)) {
			quit();
			return;
		}
		
		//transfer content in batches
		byte[] buffer = new byte[1000];
		try {
			File file = new File(path.resolve(commandargs.get(1)).toString());
			
			long fileSize = file.length();
			byte[] fileSizeBytes = ByteBuffer.allocate(8).putLong(fileSize).array();
			dataOutputStream.write(fileSizeBytes, 0, 8);
			
			//Operation terminated from client side
			if (serverFTP.abortGet(path.resolve(commandargs.get(1)), lockId)) {
				quit();
				return;
			}
			
			//send file to client
			BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
			int count = 0;
			while((count = in.read(buffer)) > 0) {
				if (serverFTP.abortGet(path.resolve(commandargs.get(1)), lockId)) {
					in.close();
					quit();
					return;
				}
				dataOutputStream.write(buffer, 0, count);
			}

			//release lock for get operation

			serverFTP.releaseGetLock(path.resolve(commandargs.get(1)), lockId);
			in.close();
		} catch(Exception e) {
			System.out.println("File transfer error");
		}
		
	}

    public void receiveFile() throws Exception {
		
		//Fetch lock for put operation
		int lockId = serverFTP.getPutLockId(path.resolve(commandargs.get(1)));
		
		
		//send terminate Id to client
		dataOutputStream.writeBytes(lockId + "\n");
		
		while (!serverFTP.getPutLock(path.resolve(commandargs.get(1)), lockId))
			Thread.sleep(10);
		
		if (serverFTP.abortPut(path.resolve(commandargs.get(1)), lockId)) {
			quit();
			return;
		}
		
		//Start receiving file from client
		dataOutputStream.writeBytes("\n");
		
		if (serverFTP.abortPut(path.resolve(commandargs.get(1)), lockId)) {
			quit();
			return;
		}
		
		byte[] fileSizeBuffer = new byte[8];
		dataInputStream.read(fileSizeBuffer);
		ByteArrayInputStream bais = new ByteArrayInputStream(fileSizeBuffer);
		DataInputStream dis = new DataInputStream(bais);
		long fileSize = dis.readLong();
		
		if (serverFTP.abortPut(path.resolve(commandargs.get(1)), lockId)) {
			quit();
			return;
		}
		
		FileOutputStream f = new FileOutputStream(new File(commandargs.get(1)).toString());
		int count = 0;
		byte[] buffer = new byte[1000];
		long bytesReceived = 0;
		while(bytesReceived < fileSize) {

			//Operation terminated from client side
			if (serverFTP.abortPut(path.resolve(commandargs.get(1)), lockId)) {
				f.close();
				quit();
				return;
			}
			count = dataInputStream.read(buffer);
			f.write(buffer, 0, count);
			bytesReceived += count;
		}
		f.close();
		//release lock for put operation
		serverFTP.releasePutLock(path.resolve(commandargs.get(1)), lockId);
	}

	public void pwd() throws Exception {
		//send present working directory
		dataOutputStream.writeBytes(currentThreadDir + "\n");
	}

	public void listFiles() throws Exception {
		try {
			DirectoryStream<Path> dirStream = Files.newDirectoryStream(path);
			for (Path filepath: dirStream)
				dataOutputStream.writeBytes(filepath.getFileName() + "\n");
			dataOutputStream.writeBytes("\n");
		} catch(Exception e) {
			dataOutputStream.writeBytes("Failed to fetch list of files" + "\n");
			dataOutputStream.writeBytes("\n");
		}
	}

	public void changeDirectory() throws Exception {
		try {

			
			//System.out.println("Command Size" + commandargs.size());
			if (commandargs.size() == 1) 
			{
				path = Paths.get(System.getProperty("user.dir"));
				currentThreadDir = System.getProperty("user.dir");
				//System.out.println("I am here");
				dataOutputStream.writeBytes("Present working directory set to parent directory");
				dataOutputStream.writeBytes("\n");
				//pwd();
			}
			
			else if (commandargs.get(1).equals("..")) {
				
				if(currentThreadDir.equals(System.getProperty("user.dir")))
				{
					dataOutputStream.writeBytes("Already in home directord");
					System.out.println("Already in home directory");
				}
				else if(path.getParent() != null)
				{
					path = path.getParent();
					currentThreadDir = currentThreadDir.substring(0, currentThreadDir.lastIndexOf('/')).trim();
					dataOutputStream.writeBytes("Present working direcotry changed");
				}
				dataOutputStream.writeBytes("\n");
				//pwd();
			}
			
			else {
				
				if (Files.notExists(path.resolve(commandargs.get(1)))) {
					dataOutputStream.writeBytes("Change directory failed as path doesn't exist" + "\n");
				} 
				
				else if (Files.isDirectory(path.resolve(commandargs.get(1)))) {

					path = path.resolve(commandargs.get(1));
					currentThreadDir = path.resolve("").toString();
					dataOutputStream.writeBytes("Present working direcotry changed");
				}
				
				else {
					dataOutputStream.writeBytes("Change directory failed as path is a file"+ "\n");
				}
				dataOutputStream.writeBytes("\n");
			}

		} catch (Exception e) {
			dataOutputStream.writeBytes("Change directory failed" + "\n");
		}
	}

	public void createDirectory() throws Exception {
		try {
			Files.createDirectory(path.resolve(commandargs.get(1)));
			dataOutputStream.writeBytes("Directory created Successfully");
			dataOutputStream.writeBytes("\n");
		} catch(FileAlreadyExistsException falee) {
			dataOutputStream.writeBytes("Directory creation failed as it already exists" + "\n");
		} catch(Exception e) {
			dataOutputStream.writeBytes("Directory creation failed" + "\n");
		}
	}

	public void delete() throws Exception {
		if (!serverFTP.remove(path.resolve(commandargs.get(1)))) {
			dataOutputStream.writeBytes("Cannot delete file as it is locked" + "\n");
			dataOutputStream.writeBytes("\n");
			return;
		}
		
		try {
			boolean confirm = Files.deleteIfExists(path.resolve(commandargs.get(1)));
			if (!confirm) {
				dataOutputStream.writeBytes("File does not exist at Server" + "\n");
				dataOutputStream.writeBytes("\n");
			} else
			dataOutputStream.writeBytes("File deleted at the server"+"\n");
		} catch(DirectoryNotEmptyException enee) {
			dataOutputStream.writeBytes("Cannot delete as directory is not empty" + "\n");
			dataOutputStream.writeBytes("\n");
		} catch(Exception e) {
			dataOutputStream.writeBytes("Failed to delete the file or directory" + "\n");
			dataOutputStream.writeBytes("\n");
		}
	}

    public void quit() throws Exception {
		//socket connection closed
		nSocket.close();
		throw new Exception();
	}

    public void run() {
		System.out.println(Thread.currentThread().getName() + " NormalWorker Started");
		exitThread:
		while (true) {
			try {
				//check for commands from users regularly
				while (!br.ready())
					Thread.sleep(10);
				
				//tokenize input into tokens
				commandargs = new ArrayList<String>();
				String cmd = br.readLine();
				Scanner tokenize = new Scanner(cmd);
				if (tokenize.hasNext())
				    commandargs.add(tokenize.next());
				
				if (tokenize.hasNext())
					commandargs.add(cmd.substring(commandargs.get(0).length()).trim());
				tokenize.close();
				System.out.println("Sent from Client : "+ cmd);
				
				
				switch(commandargs.get(0)) 
				{
					case "get": 	sendFile();		
									break;

					case "put": 	receiveFile();		
									break;

					case "delete": 	delete();	
									break;

					case "ls": 		listFiles();		
									break;

					case "cd": 		changeDirectory();		
									break;

					case "mkdir": 	createDirectory();	
									break;

					case "pwd": 	pwd();		
									break;

					case "quit": 	quit();		
									break exitThread;

					default:		System.out.println("invalid command");
				}
			} catch (Exception e) {
				break exitThread;
			}
		}
		System.out.println(Thread.currentThread().getName() + " Normal Worker Thread Exited");
	}
    
}
