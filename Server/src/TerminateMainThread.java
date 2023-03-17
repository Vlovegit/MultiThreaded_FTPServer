
import java.net.*;


public class TerminateMainThread implements Runnable {
	private ServerSocket tServerSocket;
	private ServerFTP serverFTP;
	
	
	public TerminateMainThread(ServerFTP serverFTP, ServerSocket socket) {
		this.tServerSocket = socket;
		this.serverFTP = serverFTP;
		
	}
	
	public void run() {
		System.out.println("////Terminate " + Thread.currentThread().getName() + " started////");
		while (true) {
			try {
				(new Thread(new TerminateServerThread(serverFTP, tServerSocket.accept()))).start();
			} catch (Exception e) {
				System.out.println("////Terminate " + Thread.currentThread().getName() + " failed to start////");
			}
		}
	}
}
