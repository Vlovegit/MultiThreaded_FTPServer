
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
			//Input
			InputStreamReader isr = new InputStreamReader(tSocket.getInputStream());
			BufferedReader br = new BufferedReader(isr);
			
			//check every 10 ms for input
			while (!br.ready())
				Thread.sleep(10);
			
			//capture and parse input
			List<String> tokens = new ArrayList<String>();
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
				case "terminate":   serverFTP.terminate(Integer.parseInt(tokens.get(1)));
                                    System.out.println("Terminate Command= " + tokens.get(1));
                                    break;
				default:            System.out.println("Invalid command");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println(Thread.currentThread().getName() + " TerminateWorkerThread Closed");
	}
}
