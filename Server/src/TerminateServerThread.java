
import java.io.*;
import java.net.Socket;
import java.util.*;



public class TerminateServerThread implements Runnable {
	private ServerFTP serverFTP;
	private Socket tSocket;
	
	public TerminateServerThread(ServerFTP serverFTP, Socket tSocket) {
		this.serverFTP = serverFTP;
		this.tSocket = tSocket;
	}

    public void run() {
		System.out.println(Thread.currentThread().getName() + " TerminateWorker Started");
		try {
			
			InputStreamReader isr = new InputStreamReader(tSocket.getInputStream());
			BufferedReader br = new BufferedReader(isr);
			
			//check for commands from users regularly
			while (!br.ready())
				Thread.sleep(10);
			
			//capture and parse input
			List<String> commandArgs = new ArrayList<String>();
			String command = br.readLine();
			Scanner tokenize = new Scanner(command);
			//tokenize command
			if (tokenize.hasNext())
				commandArgs.add(tokenize.next());

			if (tokenize.hasNext())
				commandArgs.add(command.substring(commandArgs.get(0).length()).trim());
			tokenize.close();
			System.out.println(commandArgs.toString());
			
			//command selector
			switch(commandArgs.get(0)) {
				case "terminate":   serverFTP.abort(Integer.parseInt(commandArgs.get(1)));
                                    System.out.println("Abort Command = " + commandArgs.get(1));
                                    break;
				default:            System.out.println("Invalid command");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println(Thread.currentThread().getName() + " TerminateWorkerThread Closed");
	}
}
