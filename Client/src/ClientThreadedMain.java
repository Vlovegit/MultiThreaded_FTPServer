

import java.io.IOException;
import java.net.*;

public class ClientThreadedMain {

    public static String machineip;
    public static int nPort, tPort;
    public static final boolean DEBUG_VARIABLE = false;
	public static final String EXIT_STRING = "FTP Session over!!!";

    public static void main(String[] args) {

        //Check for valid no. of args
		if(args.length<3)
		{
				System.out.println("Cannot connect to the server as machine address, nport or tport missing in the arguements");
				return;
		}
        //check if nport and tport are same
        if (args[1].equals(args[2]))
		{
			System.out.println("Cannot connect to the server, nport and tport cannot be same");
			System.exit(0);
		}

        //Resolve host
		try {
			machineip = args[0];
			nPort = Integer.parseInt(args[1]);
			tPort = Integer.parseInt(args[2]);

            
            ClientFTP clientFtp = new ClientFTP();
			
			//The initial thread
			(new Thread(new WorkerThread(clientFtp, machineip, nPort))).start();

		} catch(ConnectException ce)
		{
			System.out.println("Cannot connect to server. The host or port provided is incorrect. Please check and try again");
		}
		catch(SocketException se)
		{
			System.out.println("Connection to the server lost. Please reconnect.");
		}catch (SocketTimeoutException ste) {
			System.out.println("Timeout: Server busy");
		}
	    catch(IOException ioe){
			System.out.println("IO Exception");//new
		}
		catch (Exception e) {
			e.printStackTrace();
		}


    }
	

}
