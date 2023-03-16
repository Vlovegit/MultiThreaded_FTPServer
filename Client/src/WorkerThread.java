
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;


public class WorkerThread implements Runnable{

    private ClientFTP clientFtp;
	private int nPort;
	private int tPort;
    private Socket socket;
    private int terminateID;
	private String machineip;
	private Path path, serverPath;
	private List<String> commandArgs;
	
    //inputstream outputstream

    private BufferedReader bufferedReader;
    private DataInputStream dataInputStream; 
    private InputStreamReader inputStreamReader;
	private OutputStream outputStream;
	private DataOutputStream dataOutputStream;

	//Public Constructor


    public WorkerThread(ClientFTP clientFtp, String machineip, int nPort, int tPort) throws Exception {
		this.clientFtp = clientFtp;
		this.machineip = machineip;
		this.nPort = nPort;
		this.tPort = tPort;
		
		//Connecting to server
		InetAddress ip = InetAddress.getByName(machineip);
		socket = new Socket();
		socket.connect(new InetSocketAddress(ip.getHostAddress(), nPort), 2000);
		
		//initializing the streams
		initializeStream();
		
		//Set present working directory
		path = Paths.get(System.getProperty("user.dir"));
		System.out.println("Connected to Server: " + ip);
	}

    public void initializeStream() {
		try {
			//Input
			inputStreamReader = new InputStreamReader(socket.getInputStream());
			bufferedReader = new BufferedReader(inputStreamReader);
			
			//Data
			dataInputStream = new DataInputStream(socket.getInputStream());
			
			//Output
			outputStream = socket.getOutputStream();
			dataOutputStream = new DataOutputStream(outputStream);
			
			//get server directory
			dataOutputStream.writeBytes("pwd" + "\n");
			
			//set server directory
			String get_line;
			if (!(get_line = bufferedReader.readLine()).equals("")) {
				serverPath = Paths.get(get_line);
			}
		} catch (Exception e) {
			if (ClientThreadedMain.DEBUG_VARIABLE) System.out.println("Error in stream initialization"); //TODO
		}
	}
    // Client get function starts here

    public void receiveFile() throws Exception {
		if (commandArgs.size() != 2) {
			invalid();
			return;
		}
		
		if (commandArgs.get(1).endsWith(" &")) {
			commandArgs.set(1, commandArgs.get(1).substring(0, commandArgs.get(1).length()-1).trim());
			
			List<String> tempList = new ArrayList<String>(commandArgs);
			Path tempPath = Paths.get(serverPath.toString());
			Path tempPathClient = Paths.get(path.toString());
			
			(new Thread(new GetHandlerThread(clientFtp, machineip, nPort, tempList, tempPath, tempPathClient))).start();
			
			Thread.sleep(50);
			
			return;
		}
		
		//same file move again
		if (!clientFtp.move(serverPath.resolve(commandArgs.get(1)))) {
			System.out.println("The file is on the move");
			return;
		}
		
		//send command to Server
		dataOutputStream.writeBytes("get " + serverPath.resolve(commandArgs.get(1)) + "\n");
		
		//printing error message
		String get_line;
		if (!(get_line = bufferedReader.readLine()).equals("")) {
			System.out.println(get_line);
			return;
		}
		
		//waiting to read terminate ID
		try {
			terminateID = Integer.parseInt(bufferedReader.readLine());
		} catch(Exception e) {
			if (ClientThreadedMain.DEBUG_VARIABLE) System.out.println("Invalid TerminateID");
		}
		
		//locking client side
		clientFtp.moveIn(serverPath.resolve(commandArgs.get(1)), terminateID);
		
		
		//getting file size
		byte[] fileSizeBuffer = new byte[8];
		dataInputStream.read(fileSizeBuffer);
		ByteArrayInputStream bais = new ByteArrayInputStream(fileSizeBuffer);
		DataInputStream in = new DataInputStream(bais);
		long fileSize = in.readLong();
		
		//receiving the file
		FileOutputStream fileOutputStream = new FileOutputStream(new File(commandArgs.get(1)));
		int count = 0;
		byte[] buffer = new byte[1000];
		long bytesReceived = 0;
		while(bytesReceived < fileSize) {
			count = dataInputStream.read(buffer);
			fileOutputStream.write(buffer, 0, count);
			bytesReceived += count;
		}
		fileOutputStream.close();
		
		//CLIENT side un-locking
		clientFtp.moveOut(serverPath.resolve(commandArgs.get(1)), terminateID);
	}

	public void sendFile() throws Exception {
		
		if (commandArgs.get(1).endsWith(" &")) {
			commandArgs.set(1, commandArgs.get(1).substring(0, commandArgs.get(1).length()-1).trim());
			//background
			
			List<String> tempList = new ArrayList<String>(commandArgs);
			Path tempPath = Paths.get(serverPath.toString());
			
			(new Thread(new PutHandlerThread(clientFtp, machineip, nPort, tempList, tempPath))).start();
			
			Thread.sleep(50);
			
			return;
		}
		
		//same transfer
		if (!clientFtp.move(serverPath.resolve(commandArgs.get(1)))) {
			System.out.println("error: file already transfering");
			return;
		}
		
		//not a directory or file
		if (Files.notExists(path.resolve(commandArgs.get(1)))) {
			System.out.println("put: " + commandArgs.get(1) + ": No such file or directory");
		} 
		//is a directory
		else if (Files.isDirectory(path.resolve(commandArgs.get(1)))) {
			System.out.println("put: " + commandArgs.get(1) + ": Is a directory");
		}
		//transfer file
		else {
			//send command
			dataOutputStream.writeBytes("put " + serverPath.resolve(commandArgs.get(1)) + "\n");
			
			//wait for terminate ID
			try {
				terminateID = Integer.parseInt(bufferedReader.readLine());
			} catch(Exception e) {
				System.out.println("Invalid TerminateID");
			}
			
			//CLIENT side locking
			clientFtp.moveIn(serverPath.resolve(commandArgs.get(1)), terminateID);
			
			//signal to start writing
			bufferedReader.readLine();
			
			//need to figure
			Thread.sleep(100);
			
			
			byte[] buffer = new byte[1000];
			try {
				File file = new File(path.resolve(commandArgs.get(1)).toString());
				
				//write long filesize as first 8 bytes
				long fileSize = file.length();
				byte[] fileSizeBytes = ByteBuffer.allocate(8).putLong(fileSize).array();
				dataOutputStream.write(fileSizeBytes, 0, 8);
				
				Thread.sleep(100);
				
				//write file
				BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
				int count = 0;
				while((count = in.read(buffer)) > 0)
					dataOutputStream.write(buffer, 0, count);
				
				in.close();
			} catch(Exception e){
				System.out.println("Client file transfer failed");
			}
			
			//CLIENT side un-locking
			clientFtp.moveOut(serverPath.resolve(commandArgs.get(1)), terminateID);
		}
	}
	

	public void listFiles() throws Exception {
		
		//send command
		dataOutputStream.writeBytes("ls" + "\n");
		
		//messages
		String serverResponse;
		while (!(serverResponse = bufferedReader.readLine()).equals(""))
		    System.out.println(serverResponse);
	}

	public void changeDirectory() throws Exception {
		
		if (commandArgs.size() == 1) 
			dataOutputStream.writeBytes("cd" + "\n");
		else
			dataOutputStream.writeBytes("cd " + commandArgs.get(1) + "\n");
		
		String serverResponse;
		if (!(serverResponse = bufferedReader.readLine()).equals(""))
			System.out.println(serverResponse);
		
		dataOutputStream.writeBytes("pwd" + "\n");
		System.out.println(bufferedReader.readLine());
	}

	public void createDirectory() throws Exception {
		
		dataOutputStream.writeBytes("mkdir " + commandArgs.get(1) + "\n");
		String serverResponse;
		if (!(serverResponse = bufferedReader.readLine()).equals(""))
			System.out.println(serverResponse);
	}



    public void invalid() {
		System.out.println("Invalid Arguments Entered");
	}

	public void quit() throws Exception {
		
		if (!clientFtp.quit()) {
			System.out.println("Cannot quit, file is being transfered");
			return;
		}
		dataOutputStream.writeBytes("quit" + "\n");
	}
	
	public void terminate() throws Exception {
		
		try {
			int terminateID = Integer.parseInt(commandArgs.get(1));
			if (!clientFtp.abortAppend(terminateID))
				System.out.println("TerminateID is Invalid");
			else
			System.out.println("Machine ip" + machineip);
			System.out.println("Tport " +tPort);
			(new Thread(new TerminateWorkerThread(machineip, tPort, terminateID))).start();
		} catch (Exception e) {
			System.out.println("TerminateID is Invalid");
			e.printStackTrace();
		}
	}

	public void CLI() {
		try {
			//keyboard input
			Scanner input = new Scanner(System.in);
			String command;
			
			do {
				
				System.out.print("myftp>");
				command = input.nextLine();
				command = command.trim();
				commandArgs = new ArrayList<String>();
				Scanner tokenize = new Scanner(command);
				if (tokenize.hasNext())
				    commandArgs.add(tokenize.next());

				if (tokenize.hasNext())
					commandArgs.add(command.substring(commandArgs.get(0).length()).trim());
				tokenize.close();
				
				
				if (commandArgs.isEmpty())
				{
					System.out.println("No command entered, please try again");
					continue;
				}
				
				
				switch(commandArgs.get(0)) 
				{
					case "get": 		receiveFile(); 			
										break;

					case "put": 		sendFile(); 			
										break;
					
					case "delete": 		dataOutputStream.writeBytes("delete " + commandArgs.get(1) + "\n");
										System.out.println(bufferedReader.readLine());
										break;

					case "ls": 			listFiles();		
										break;

					case "cd": 			changeDirectory(); 			
										break;

					case "mkdir": 		createDirectory(); 		
										break;
					
					case "pwd": 		dataOutputStream.writeBytes("pwd" + "\n");
										System.out.println(bufferedReader.readLine());			
										break;

					case "quit": 		quit(); 		
										break;

					case "terminate":	terminate();	
										break;
					
					default:			System.out.println("Invalid command '" + commandArgs.get(0) + "'");
				}
			} while (!command.equalsIgnoreCase("quit"));
			input.close();
			
			
			System.out.println(ClientThreadedMain.EXIT_STRING);
		} catch (Exception e) {
			System.out.println("error: disconnected from host");
			//e.printStackTrace(); //TODO
		}
	}
	
	public void run() {
		CLI();
	}

	
	
}
