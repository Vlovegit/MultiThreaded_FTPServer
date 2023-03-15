import java.net.*;

public class ServerThreadedMain {

    public static final boolean DEBUG = false;
	private static ServerSocket nSocket, tSocket;
    
    public static void main(String[] args) {

        if(args.length<2)
			{
				System.out.println("Cannot start the server, two port numbers is needed");
				System.exit(0);
			}

			if(args[0].equals(args[1]))
			{
				System.out.println("Cannot start the server, port numbers cannot be same");
				System.exit(0);
			}

            try {
                nSocket = new ServerSocket(Integer.parseInt(args[0]));
                tSocket = new ServerSocket(Integer.parseInt(args[1]));
            }
            catch(BindException be) {
                System.out.println("Ports provided are in use, please try with a different port");
                return;
            }
            catch (Exception e) {
                e.printStackTrace();
                return;
            }

            try {
                FTPServer ftpServer = new FTPServer();               
                (new Thread(new DispatcherMainThread(ftpServer, nSocket))).start();
                (new Thread(new TerminateMainThread(ftpServer, tSocket))).start();
            } catch (Exception e) {
                System.out.println("Error creating a new thread");
                e.printStackTrace(); //TODO
                return;
            }
        }


}