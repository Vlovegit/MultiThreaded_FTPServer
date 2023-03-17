
import java.net.*;

public class DispatcherMainThread implements Runnable {
	private ServerSocket nServerSocket;
	private ServerFTP serverFTP;
	
	
	public DispatcherMainThread(ServerFTP serverFTP, ServerSocket serverSocket) {
		this.nServerSocket = serverSocket;
		this.serverFTP = serverFTP;	
	}
	
	public void run() {
		System.out.println("////Dispatcher "+ Thread.currentThread().getName() + " Started////");
		while (true) {
			try {
				(new Thread(new NormalWorkerThread(serverFTP, nServerSocket.accept()))).start();
			} catch (Exception e) {
				System.out.println("////Dispatcher " + Thread.currentThread().getName() + " failed to start////");
			}
		}
	}
}