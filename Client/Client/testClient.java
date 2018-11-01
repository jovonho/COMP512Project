package Client;

import Server.Interface.*;
import Server.Common.*;

import java.util.*;
import java.io.*;
import java.rmi.RemoteException;
import java.rmi.ConnectException;
import java.rmi.ServerException;
import java.rmi.UnmarshalException;

public abstract class testClient
{
	IResourceManager m_resourceManager = null;

	public testClient()
	{
		super();
	}

	public abstract void connectServer();

	public void start()
	{
		// Prepare for reading commands
		System.out.println();
		System.out.println("Location \"help\" for list of supported commands");

		BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));

		while (true)
		{
			// Read the next command
			String command = "";
			Vector<String> arguments = new Vector<String>();
			try {
				System.out.print((char)27 + "[32;1m\n>] " + (char)27 + "[0m");
				command = stdin.readLine().trim();
			}
			catch (IOException io) {
				System.err.println((char)27 + "[31;1mClient exception: " + (char)27 + "[0m" + io.getLocalizedMessage());
				io.printStackTrace();
				System.exit(1);
			}

			try {
				arguments = parse(command);
				TestCommand cmd = TestCommand.fromString((String)arguments.elementAt(0));
				try {
					execute(cmd, arguments);
				}
				catch (ConnectException e) {
					connectServer();
					execute(cmd, arguments);
				}
			}
			catch (IllegalArgumentException|ServerException e) {
				System.err.println((char)27 + "[31;1mCommand exception: " + (char)27 + "[0m" + e.getLocalizedMessage());
			}
			catch (ConnectException|UnmarshalException e) {
				System.err.println((char)27 + "[31;1mCommand exception: " + (char)27 + "[0mConnection to server lost");
			}
			catch (Exception e) {
				System.err.println((char)27 + "[31;1mCommand exception: " + (char)27 + "[0mUncaught exception");
				e.printStackTrace();
			}
		}
	}

	public void execute(TestCommand cmd, Vector<String> arguments) throws RemoteException, NumberFormatException
	{
		switch (cmd)
		{
			case TestFlights: {
				if(m_resourceManager.addFlight(1, 2, 3, 4)){
					if(m_resourceManager.queryFlight(1,2)!=3){
						System.out.println("addFlight failed. Incorrect number of seats");
					}
					if(m_resourceManager.queryFlightPrice(1,2)!=4){
						System.out.println("addFlight failed. Incorrect price");
					}
					else System.out.println("addFlight worked properly");
				}
				if(m_resourceManager.deleteFlight(1,2)){
					if(m_resourceManager.queryFlight(1,2)!=0){
						System.out.println("System did not delete flight properly");
					}
					else System.out.println("deleteFlight worked properly");
				}
			break;

			}
			case TestRooms: {
				if(m_resourceManager.addRooms(1, "ss", 3, 4)){
					if(m_resourceManager.queryRooms(1,"ss")!=3){
						System.out.println("addRooms failed. Incorrect number of rooms");
					}
					if(m_resourceManager.queryRoomsPrice(1,"ss")!=4){
						System.out.println("addRooms failed. Incorrect price");
					}
					else System.out.println("addRooms worked properly");
				}
				if(m_resourceManager.deleteRooms(1,"ss")){
					if(m_resourceManager.queryRooms(1,"ss")!=0){
						System.out.println("System did not delete rooms properly");
					}
					else System.out.println("deleteRooms worked properly");
				}
			break;	
			}
			case TestCars: {
				if(m_resourceManager.addCars(1, "ss", 3, 4)){
					if(m_resourceManager.queryCars(1,"ss")!=3){
						System.out.println("addCars failed. Incorrect number of cars");
					}
					if(m_resourceManager.queryCarsPrice(1,"ss")!=4){
						System.out.println("addCars failed. Incorrect price");
					}
					else System.out.println("addcars worked properly");
				}
				if(m_resourceManager.deleteCars(1,"ss")){
					if(m_resourceManager.queryCars(1,"ss")!=0){
						System.out.println("System did not delete cars properly");
					}
					else System.out.println("deleteCars worked properly");
				}
			break;
			}
			case Quit: 
				checkArgumentsCount(1, arguments.size());

				System.out.println("Quitting client");
				System.exit(0);
			
		}
	}

	public static Vector<String> parse(String command)
	{
		Vector<String> arguments = new Vector<String>();
		StringTokenizer tokenizer = new StringTokenizer(command,",");
		String argument = "";
		while (tokenizer.hasMoreTokens())
		{
			argument = tokenizer.nextToken();
			argument = argument.trim();
			arguments.add(argument);
		}
		return arguments;
	}

	public static void checkArgumentsCount(Integer expected, Integer actual) throws IllegalArgumentException
	{
		if (expected != actual)
		{
			throw new IllegalArgumentException("Invalid number of arguments. Expected " + (expected - 1) + ", received " + (actual - 1) + ". Location \"help,<CommandName>\" to check usage of this command");
		}
	}

	public static int toInt(String string) throws NumberFormatException
	{
		return (new Integer(string)).intValue();
	}

	public static boolean toBoolean(String string)// throws Exception
	{
		return (new Boolean(string)).booleanValue();
	}
}
