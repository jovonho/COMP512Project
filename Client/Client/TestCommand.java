package Client;

public enum TestCommand {
	
	Help("List all available commands", "[CommandName]"),
	TestFlights("Runs an automated unit test for the Flight methods", ""),
	TestCars("Runs an automated unit test for the Car methods", ""),
	TestRooms("Runs an automated unit test for the Rooms methods", ""),
	Quit("Exit the client application", "");
	
	String m_description;
	String m_args;

	TestCommand(String p_description, String p_args)
	{
		m_description = p_description;
		m_args = p_args;
	}

	public static TestCommand fromString(String string)
	{
		for (TestCommand cmd : TestCommand.values())
		{
			if (cmd.name().equalsIgnoreCase(string))
			{
				return cmd;
			}
		}
		throw new IllegalArgumentException("Command " + string + " not found");
	}

	public static String description()
	{
		String ret = "Commands supported by the client:\n";
		for (TestCommand cmd : TestCommand.values())
		{	 
			ret += "\t" + cmd.name() + "\n";
		}
		ret += "use help,<CommandName> for more detailed information";
		return ret;
	}

	public String toString()
	{
		String ret = name() + ": " + m_description + "\n";
		ret += "Usage: " + name() + "," + m_args;
		return ret;
	}

}
