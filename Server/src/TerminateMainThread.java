
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class TerminateMainThread implements Runnable {
	private ServerFTP serverFTP;
	private ServerSocket tServerSocket;
	
	public TerminateMainThread(ServerFTP serverFTP, ServerSocket socket) {
		this.serverFTP = serverFTP;
		this.tServerSocket = socket;
	}
	
	public void run() {
		System.out.println("Terminate Thread " + Thread.currentThread().getName() + " started");
		while (true) {
			try {
				(new Thread(new TerminateServerThread(serverFTP, tServerSocket.accept()))).start();
			} catch (Exception e) {
				System.out.println("Terminate Thread " + Thread.currentThread().getName() + " failed to start");
			}
		}
	}
}
