package Server.TransactionManager;

import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;

import Server.Common.Flight;
import Server.Common.ResourceManager;
import Server.Common.Trace;
import Server.Interface.IResourceManager;
import Server.Interface.IResourceManager.InvalidTransactionException;
import Server.Interface.IResourceManager.TransactionAbortedException;
import Server.LockManager.*;

public class TransactionManager {
	
	private int TxCount = 0;
	private final long TTL = 5000;

	private LockManager lockManager = new LockManager();
	
	private List<Integer> activeTransactions = new ArrayList<Integer>();
	private List<Integer> silentlyAbortedTransactions = new ArrayList<>();
	private Map<Integer, Timer> transactionTimers = new HashMap<Integer, Timer>();
	
	private IResourceManager m_flightsManager = null;
	
	private Map<Integer, List<Object>> localCopies = new HashMap<>();
	
	
	public TransactionManager(IResourceManager flightsManager) 
	{
		super();
		TxCount = 0;
		this.m_flightsManager = flightsManager;
	}
	
	public int startNewTx() {
		int xid = ++TxCount;
		activeTransactions.add(xid);
		localCopies.put(xid, new ArrayList<Object>());
		
		Timer timer = new Timer();
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				Trace.info("Transaction " + xid + " timed out");
				abortTx(xid);
				silentlyAbortedTransactions.add(xid);
			}
		}, TTL);
		
		transactionTimers.put(xid, timer);
		Trace.info("TM::start() Transaction " + xid + " started");
		return xid;
	}
	
	// TODO Do we abort if we get a deadlock exception ??
	
	/**
	 * 
	 * @return False is a parameter is wrong
	 * 
	 * 
	 * @throws InvalidTransactionException 
	 * @throws TransactionAbortedException
	 * @throws RemoteException
	 */
			
	
	public boolean addFlight(int xid, int flightNum, int flightSeats, int flightPrice) throws InvalidTransactionException, TransactionAbortedException, RemoteException {
		//reset timer before in case we don't have enough time to complete the entire operation.
		resetTimer(xid);
		checkValid(xid);
		
		Flight flight;
		String str = "flight-" + flightNum;
		
		// First we try locking on the object.
		try 
		{
			// If we can get a lock without problems then we either we had a redundant lock (1), we upgraded our lock (2) or we created a new lock (3)
			// In cases (1) and (2), the object is already in our local copy.
			// In case (1) we haven't touched the object during the transaction yet.
			if (lockManager.Lock(xid, str, TransactionLockObject.LockType.LOCK_WRITE)) 
			{
				// We search our local copy for the object
				
				boolean found = false;
				for (Object o : localCopies.get(xid)) 
				{
					if (str.equals( ((Flight)o).getKey()) ) 
					{
						found = true;
						Flight l_flight = (Flight)o;
						
						// Then we update accordingly, if price is <=0, we only increase the seat count
						if (flightPrice <= 0) {
							flight = new Flight(flightNum, flightSeats + l_flight.getCount(), l_flight.getPrice());
							localCopies.get(xid).add(flight);
							break;
						}	
						// Else we update price and add seats
						else {
							flight = new Flight(flightNum, flightSeats + l_flight.getCount(), flightPrice);			
							localCopies.get(xid).add(flight);
							break;
						}
					}
				}
				
				// If it is not in our local copy then we must fetch it from the RM 
				// The object may or may not exist at the RM.
				if (!found) 
				{
					// Attempt to fetch it from the RM
					flight = (Flight) m_flightsManager.getItem(xid, "flight-" + flightNum);
					
					// If it is not in the RM, we create a new object
					if (flight == null) 
					{
						flight = new Flight(flightNum, flightSeats, flightPrice);
						localCopies.get(xid).add(flight);
					}
					
					// If it exists in the RM we modify it accordingly and add it to our local copy
					else if (flightPrice <= 0) {
						flight = new Flight(flightNum, flightSeats + flight.getCount(), flight.getPrice());
						localCopies.get(xid).add(flight);
					}
					else {
						flight = new Flight(flightNum, flightSeats + flight.getCount(), flightPrice);			
						localCopies.get(xid).add(flight);
					}
				}
				
			}
			
			// lockManager returns false if one of the input arguments is wrong
			else {
				return false;
			}
		} 

		// If we deadlock then we must abort the transaction
		catch (DeadlockException e) {
			abortTx(xid);
			return false;
		}

		// Check if object already exists at the RM
		
		resetTimer(xid);
		return true;	
	}
	
	private void resetTimer(int xid) 
	{
		transactionTimers.get(xid).cancel();
		transactionTimers.remove(xid);
		Timer timer = new Timer();
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				Trace.info("Transaction " + xid + " timed out");
				abortTx(xid);
				silentlyAbortedTransactions.add(xid);
			}
		}, TTL);
		transactionTimers.put(xid, timer);
	}
	
	
	private void checkValid(int xid) throws InvalidTransactionException, TransactionAbortedException {
		if (!activeTransactions.contains((Integer) xid)) {
			// If the txid is not in the active transactions list then either it does not exist 
			// or it was silently aborted since last time it was called.
			if (silentlyAbortedTransactions.contains((Integer) xid)) {
				// remove it 
				silentlyAbortedTransactions.remove((Integer) xid);
				throw new TransactionAbortedException();
			}
			throw new InvalidTransactionException();
		}
	}
	
	public boolean abortTx(int xid) {

		if (activeTransactions.contains((Integer) xid)) 
		{
			lockManager.UnlockAll(xid);
			activeTransactions.remove((Integer) xid);
			transactionTimers.remove((Integer) xid);	
			System.out.println("From TM: " + xid + " ABORTED");
			return true;
		}
		else {
			return false;
		}
	}
	
	
	
	public static void main(String[] args) {
		IResourceManager m_flightsManager = new ResourceManager("Flights");
		TransactionManager TM = new TransactionManager(m_flightsManager);
		
		try {
			TM.testAddFlight();
		} 
		catch (Exception e) {
			e.printStackTrace();
		}

	}
	
	private void testAddFlight() throws RemoteException, InvalidTransactionException, TransactionAbortedException {
		int xid = startNewTx();
//		System.out.println("Test 1\nExpect a new flight-1 with 50 seats at 100$");
//		addFlight(1, 1, 50, 100);
//		System.out.println(localCopies.get(xid).toString());
		
		m_flightsManager.addFlight(1, 1, 50, 100);
		
		System.out.println("");
		System.out.println("Test 2\nIncrease flight count only (price <= 0)");
		addFlight(1,1,40,0);
		
		int index = localCopies.get(xid).size();
		Flight result = (Flight)localCopies.get(xid).get(index-1);
		if (result.getCount() == 90) {
			System.out.println("Success");
		}
		else {
			System.err.println("Failure");
		}
		System.out.println(localCopies.get(xid).toString());
		
		
	}
	
	private void checkValidTest() throws RemoteException {
		
		int xid = startNewTx();
		try {
			Thread.sleep(TTL + 500);
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		try {
			checkValid(xid);
			System.out.println("Hello");
		} catch (InvalidTransactionException e) {
			System.err.println("checkValid test Failed");
		} catch (TransactionAbortedException e) {
			System.out.println("checkValid test successfull");
		} 
	}
	
	
}
