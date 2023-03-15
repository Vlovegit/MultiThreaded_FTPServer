import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;


public class WorkerThread implements Runnable{

    private ClientFTP clientFtp;
	private int nPort;
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


    public WorkerThread(ClientFTP clientFtp, String machineip, int nPort) throws Exception {
		this.clientFtp = clientFtp;
		this.machineip = machineip;
		this.nPort = nPort;
		
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
			if (!(get_line = reader.readLine()).equals("")) {
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

    public void invalid() {
		System.out.println("Invalid Arguments Entered");
	}

	public void quit() throws Exception {
		//only one argument
		if (commandArgs.size() != 1) {
			invalid();
			return;
		}
		
		if (!clientFtp.quit()) {
			System.out.println("Cannot quit, file is being transfered");
			return;
		}
		
		//send command
		dataOutputStream.writeBytes("quit" + "\n");
	}

	public void notSupported() {
		System.out.println("Command does not support new thread spawn");
	}
	
	public void terminate() throws Exception {
		//only two arguments
		if (commandArgs.size() != 2) {
			invalid();
			return;
		}
		
		//not backgroundable
		if (commandArgs.get(1).endsWith(" &")) {
			notSupported();
			return;
		}
		
		try {
			int terminateID = Integer.parseInt(commandArgs.get(1));
			if (!clientFtp.abortAppend(terminateID))
				System.out.println("TerminateID is Invalid");
			else
				(new Thread(new TerminateWorkerThread(machineip, Main.tPort, terminateID))).start();
		} catch (Exception e) {
			System.out.println("TerminateID is Invalid");
		}
	}

	public void CLI() {
		try {
			//keyboard input
			Scanner input = new Scanner(System.in);
			String command;
			
			do {
				//get input
				System.out.print(ClientThreadedMain.PROMPT_STRING);
				command = input.nextLine();
				command = command.trim();
				
				//parse input into tokens
				commandArgs = new ArrayList<String>();
				Scanner tokenize = new Scanner(command);
				//gets command
				if (tokenize.hasNext())
				    commandArgs.add(tokenize.next());
				//gets rest of string after the command; this allows filenames with spaces: 'file1 test.txt'
				if (tokenize.hasNext())
					commandArgs.add(command.substring(commandArgs.get(0).length()).trim());
				tokenize.close();
				if (ClientThreadedMain.DEBUG_VARIABLE) System.out.println(commandArgs);
				
				//allows for blank enter
				if (commandArgs.isEmpty())
					continue;
				
				//command selector
				switch(commandArgs.get(0)) {
					case "get": 		receiveFile(); 			break;
					//case "put": 		put(); 			break;
					//case "delete": 		delete(); 		break;
					//case "ls": 			ls(); 			break;
					//case "cd": 			cd(); 			break;
					//case "mkdir": 		mkdir(); 		break;
					//case "pwd": 		pwd(); 			break;
					case "quit": 		quit(); 		break;
					case "terminate":	terminate();	break;
					default:
						System.out.println("Invalid command '" + commandArgs.get(0) + "'");
				}
			} while (!command.equalsIgnoreCase("quit"));
			input.close();
			
			
			System.out.println(ClientThreadedMain.EXIT_STRING);
		} catch (Exception e) {
			System.out.println("error: disconnected from host");
			if (ClientThreadedMain.DEBUG_VARIABLE) e.printStackTrace(); //TODO
		}
	}
	
	public void run() {
		CLI();
	}

	
	
}
