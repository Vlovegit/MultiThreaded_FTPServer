
import java.io.*;
import java.util.*;
import java.nio.file.Path;
import java.net.*;

public class ThreadSpawnHandler implements Runnable {

    private Socket socket;
    private String command;

	
	
	//inputstream outputstream

	private InputStreamReader inputStreamReader;
	private BufferedReader bufferedReader;
	private OutputStream outputStream;
	private DataOutputStream dataOutputStream;

    //Constructor
	
	public ThreadSpawnHandler(ClientFTP clientFtp, String machineip, int nPort, String tempCommand) throws Exception {
		this.command = tempCommand;
		
		InetAddress ip = InetAddress.getByName(machineip);
		socket = new Socket();
		socket.connect(new InetSocketAddress(ip.getHostAddress(), nPort), 2000);
        inputStreamReader = new InputStreamReader(socket.getInputStream());
		bufferedReader = new BufferedReader(inputStreamReader);
        outputStream = socket.getOutputStream();
		dataOutputStream = new DataOutputStream(outputStream);
	}
	
	public void executeCommand() throws Exception {

		//send command to server
		String[] templist = {};
        templist = command.split(" ");

        if(templist[0].equals("cd")){
            if (templist.length == 1) 
			dataOutputStream.writeBytes("cd" + "\n");
		else
			dataOutputStream.writeBytes("cd " + templist[1] + "\n");
        
        String serverResponse;
        while (!(serverResponse = bufferedReader.readLine()).equals(""))
            System.out.println(serverResponse);
        dataOutputStream.writeBytes("pwd" + "\n");
        System.out.println(bufferedReader.readLine());
        return;

        }
        else if (templist[0].equals("delete"))
        {
            dataOutputStream.writeBytes("delete " + templist[1] + "\n");
			System.out.println(bufferedReader.readLine());
            return;

        }
        else if(templist[0].equals("pwd")){
            
            dataOutputStream.writeBytes("pwd" + "\n");
			System.out.println(bufferedReader.readLine());
            return;
        }
        else
        {
        dataOutputStream.writeBytes(command + "\n");
        String serverResponse;
        while (!(serverResponse = bufferedReader.readLine()).equals(""))
            System.out.println(serverResponse);
        }
		
		
        
	}

	
	
	public void run() {
		try {
			executeCommand();
			Thread.sleep(100);
			dataOutputStream.writeBytes("quit" + "\n");
		} catch (Exception e) {
			if (ClientThreadedMain.DEBUG_VARIABLE) System.out.println("ERROR IN THREAD SPAWN HANDLER");
		}
	}
}

