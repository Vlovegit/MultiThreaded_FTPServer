
import java.net.*;

public class DispatcherMainThread implements Runnable {
	private ServerFTP serverFTP;
	private ServerSocket nServerSocket;
	
	public DispatcherMainThread(ServerFTP serverFTP, ServerSocket serverSocket) {
		this.serverFTP = serverFTP;
		this.nServerSocket = serverSocket;
	}
	
	public void run() {
		System.out.println(" Dispatcher Thread "+ Thread.currentThread().getName() + " Started");
		while (true) {
			try {
				(new Thread(new NormalWorkerThread(serverFTP, nServerSocket.accept()))).start();
			} catch (Exception e) {
				System.out.println("Dispatcher Thread " + Thread.currentThread().getName() + " failed to start");
			}
		}
	}
}