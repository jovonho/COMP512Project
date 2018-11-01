package Server.Middleware;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Calendar;
import java.util.Vector;

import Server.Interface.*;
import Server.Common.*;


public class RMIMiddleware extends Middleware {
	
	private static String s_serverName = "Middleware";
	private static String s_rmiPrefix = "group33";
	
	private static String s_flightsHost = "";
	private static String s_carsHost = "";
	private static String s_roomsHost = "";
	
	private static String[] s_hosts = new String[3];
	private static String[] s_names = {"Flights", "Cars", "Rooms"};
	
	private static IResourceManager[] m_managers = {m_flightsManager, m_carsManager, m_roomsManager};
	
	
	public RMIMiddleware(String name)
	{
		super(name);
	}
	
	
	public static void main(String[] args) {
		
		// Get the three machines that will host our Resource Managers.
		if (args.length == 3)
		{
			 s_hosts[0] = args[0];
			 s_hosts[1] = args[1];
			 s_hosts[2] = args[2];
			
		}
		else 
		{
			System.err.println((char)27 + "[31;1mMiddleware exception: " 
					+ (char)27 + "[0mUsage: java server.middleware [flight host [cars host [rooms host]]]");
			System.exit(1);
		}
		
		try {
			
			RMIMiddleware server = new RMIMiddleware(s_serverName);
			connectSelf(server);
			
			for (int i=0; i < s_names.length; i++) {
				connectServer(s_hosts[i], 1099, s_names[i], i);	
			}	
			
			m_flightsManager = m_managers[0];
			m_carsManager = m_managers[1];
			m_roomsManager = m_managers[2];

			

		}
		catch (Exception e) {    
			System.err.println((char)27 + "[31;1mClient exception: " + (char)27 + "[0mUncaught exception");
			e.printStackTrace();
			System.exit(1);
		}
		
	}
	
	
	
	public static void connectSelf(RMIMiddleware server) {
		// Create the RMI server entry
		try {
			// Dynamically generate the stub (client proxy)
			IResourceManager middleware = (IResourceManager)UnicastRemoteObject.exportObject(server, 0);

			// Get or create a registry on the machine running the middleware.
			Registry l_registry;
			try {
				l_registry = LocateRegistry.createRegistry(1099);
			} catch (RemoteException e) {
				l_registry = LocateRegistry.getRegistry(1099);
			}
			final Registry registry = l_registry;
			registry.rebind(s_rmiPrefix + s_serverName, middleware);
			
			// Clean shutdown handler
			Runtime.getRuntime().addShutdownHook(new Thread() {
				public void run() {
					try {
						registry.unbind(s_rmiPrefix + s_serverName);
						System.out.println("'" + s_serverName + "' middleware unbound");
					}
					catch(Exception e) {
						System.err.println((char)27 + "[31;1mServer exception: " + (char)27 + "[0mUncaught exception");
						e.printStackTrace();
					}
				}
			});                                       
			System.out.println("'" + s_serverName + "' middleware server ready and bound to '" + s_rmiPrefix + s_serverName + "'");

		}
		
		catch (Exception e) {
			System.err.println((char)27 + "[31;1mServer exception: " + (char)27 + "[0mUncaught exception");
			e.printStackTrace();
			System.exit(1);
		}

		// Create and install a security manager
		if (System.getSecurityManager() == null)
		{
			System.setSecurityManager(new SecurityManager());
		}
	}
	
	public static void connectServer(String server, int port, String name, int index)
	{
		try {
			boolean first = true;
			while (true) {
				try {
					Registry registry = LocateRegistry.getRegistry(server, port);
					m_managers[index] = (IResourceManager)registry.lookup(s_rmiPrefix + name);
					System.out.println("Connected to '" + name + "' server [" + server + ":" + port + "/" + s_rmiPrefix + name + "]");
					break;
				}
				catch (NotBoundException|RemoteException e) {
					if (first) {
						System.out.println("Waiting for '" + name + "' server [" + server + ":" + port + "/" + s_rmiPrefix + name + "]");
						first = false;
					}
				}
				Thread.sleep(500);
			}
		}
		catch (Exception e) {
			System.err.println((char)27 + "[31;1mServer exception: " + (char)27 + "[0mUncaught exception");
			e.printStackTrace();
			System.exit(1);
		}
	}
	





	
	
	

}
