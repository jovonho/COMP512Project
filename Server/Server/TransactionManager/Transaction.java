package Server.TransactionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import Client.Command;

public class Transaction {

	protected List<Command> commands = new ArrayList<Command>();
	protected Timer timer;
	
	private int xid;

	
	public Transaction(int xid, long TTL) {
		this.xid = xid;
		timer = new Timer();
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				//Abort the transaction
			}
		}, TTL);
	}
	
	
	
}
