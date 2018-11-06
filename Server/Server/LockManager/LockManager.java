package Server.LockManager;

import Server.Common.*;

import java.util.BitSet;
import java.util.Vector;

public class LockManager
{
	private static int TABLE_SIZE = 2039;
	private static int DEADLOCK_TIMEOUT = 10000;

	private static TPHashTable lockTable = new TPHashTable(LockManager.TABLE_SIZE);
	private static TPHashTable stampTable = new TPHashTable(LockManager.TABLE_SIZE);
	private static TPHashTable waitTable = new TPHashTable(LockManager.TABLE_SIZE);

	public LockManager()
	{
		super();
	}
	
	public static void main(String[] args) {
		try {
			testSuite();
		} catch (RedundantLockRequestException e) {
			e.printStackTrace();
		}
	}
	
	// Testing method
	public static void testSuite() throws RedundantLockRequestException {
		
		LockManager lockManager = new LockManager();
		
		try {
			System.out.println("Testing req READ lock");
			lockManager.Lock(1, "car-mtl", TransactionLockObject.LockType.LOCK_READ);
			System.out.println("\nTesting converting to WRITE lock");
			lockManager.Lock(1, "car-mtl", TransactionLockObject.LockType.LOCK_WRITE);

			System.out.println("\nTesting redundant Read req");
			lockManager.Lock(1, "car-mtl", TransactionLockObject.LockType.LOCK_READ);
			
			System.out.println("\nTesting redundant Write req");
			lockManager.Lock(1, "car-mtl", TransactionLockObject.LockType.LOCK_WRITE);
			
			System.out.println("\nUnlocking all locks on object");
			lockManager.UnlockAll(1);

			System.out.println("\nTesting if we've indeed removed the locks");
			try{
				System.out.println(lockManager.lockTable.get(new DataLockObject(1, "car-mtl", TransactionLockObject.LockType.LOCK_READ)).toString());				
			} catch (NullPointerException e) {
				System.out.println("No more READ lock on object");
			}
			try {
				System.out.println(lockManager.lockTable.get(new DataLockObject(1, "car-mtl", TransactionLockObject.LockType.LOCK_WRITE)).toString());				
			} catch (NullPointerException e) {
				System.out.println("No more WRITE lock either");
			}

			
			System.out.println("\nTest case: Two txs want to read the same item");
			
			lockManager.Lock(1, "car-mtl", TransactionLockObject.LockType.LOCK_READ);
			lockManager.Lock(2, "car-mtl", TransactionLockObject.LockType.LOCK_READ);
			
			System.out.println("\nTest case: One of the two wants to upgrade to WRITE lock");
			
			try {
				lockManager.Lock(2, "car-mtl", TransactionLockObject.LockType.LOCK_WRITE);
			} catch (DeadlockException e) {
				System.out.println("Deadlock Exception caught. WRITE lock could not be granted since other tx had READ lock still");
			}
			
			lockManager.UnlockAll(1);
			lockManager.UnlockAll(2);
			
			System.out.println("\nTest case: A tx wants to WRITE to item another tx has READ lock on, but then READ lock is released before deadlock");
			
			lockManager.Lock(1, "car-mtl", TransactionLockObject.LockType.LOCK_READ);
			try {
				lockManager.Lock(2, "car-mtl", TransactionLockObject.LockType.LOCK_WRITE);
			} catch (DeadlockException e) {
				System.err.println("Did not work. I think this is because both txs are on the same thread which waits and the unlock(1) instruction is not reached before the deadlock");
			}
			lockManager.UnlockAll(1);
			
		} catch (DeadlockException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	
	public boolean Lock(int xid, String data, TransactionLockObject.LockType lockType) throws DeadlockException
	{
		// if any parameter is invalid, then return false
		if (xid < 0) { 
			return false;
		}

		if (data == null) {
			return false;
		}

		if (lockType == TransactionLockObject.LockType.LOCK_UNKNOWN) { 
			return false;
		}

		Trace.info("LM::lock(" + xid + ", " + data + ", " + lockType + ") called");

		// Two objects in lock table for easy lookup
		TransactionLockObject xLockObject = new TransactionLockObject(xid, data, lockType);
		DataLockObject dataLockObject = new DataLockObject(xid, data, lockType);

		// Return true when there is no lock conflict or throw a deadlock exception
		try {
			boolean bConflict = true;
			BitSet bConvert = new BitSet(1);
			while (bConflict) {
				synchronized (this.lockTable) {
					// Check if this lock request conflicts with existing locks
					bConflict = LockConflict(dataLockObject, bConvert);
					if (!bConflict) {
						// No lock conflict
						synchronized (this.stampTable) {
							// Remove the timestamp (if any) for this lock request
							TimeObject timeObject = new TimeObject(xid);
							this.stampTable.remove(timeObject);
						}
						synchronized (this.waitTable) {
							// Remove the entry for this transaction from waitTable (if it
							// is there) as it has been granted its lock request
							WaitLockObject waitLockObject = new WaitLockObject(xid, data, lockType);
							this.waitTable.remove(waitLockObject);
						}

						if (bConvert.get(0) == true) {
							
							// Lock Conversion
							// We need to change the lock type on both the data item and the lock itself
							DataLockObject fetchData = new DataLockObject(xid, data, TransactionLockObject.LockType.LOCK_READ);
							((DataLockObject)this.lockTable.get(fetchData)).setLockType(TransactionLockObject.LockType.LOCK_WRITE);
							
							TransactionLockObject fetchLock = new TransactionLockObject(xid, data, TransactionLockObject.LockType.LOCK_READ);
							((TransactionLockObject)this.lockTable.get(fetchLock)).setLockType(TransactionLockObject.LockType.LOCK_WRITE);
							
							Trace.info("LM::lock(" + xid + ", " + data + ", " + lockType + ") converted");
						} else {
							// Lock request that is not lock conversion
							this.lockTable.add(xLockObject);
							this.lockTable.add(dataLockObject);

							Trace.info("LM::lock(" + xid + ", " + data + ", " + lockType + ") granted");
						}
					}
				}
				if (bConflict) {
					// Lock conflict exists, wait
					WaitLock(dataLockObject);
				}
			}
		} 
		catch (DeadlockException deadlock) {
			throw deadlock;
		}
		catch (RedundantLockRequestException redundantlockrequest) {
			// Ignore redundant locks
			Trace.info("LM::lock(" + xid + ", " + data + ", " + lockType + ") " + redundantlockrequest.getLocalizedMessage());
			return true;
		} 

		return true;
	}


	// Remove all locks for this transaction in the lock table
	public boolean UnlockAll(int xid)
	{
		// If any parameter is invalid, then return false
		if (xid < 0) {
			return false;
		}

		TransactionLockObject lockQuery = new TransactionLockObject(xid, "", TransactionLockObject.LockType.LOCK_UNKNOWN); // Only used in elements() call below.
		
		synchronized(this.lockTable) {
			Vector vect = this.lockTable.elements(lockQuery);

			TransactionLockObject xLockObject;
			Vector waitVector;
			WaitLockObject waitLockObject;
			int size = vect.size();

			for (int i = (size - 1); i >= 0; i--)
			{
				xLockObject = (TransactionLockObject)vect.elementAt(i);
				this.lockTable.remove(xLockObject);

				Trace.info("LM::unlock(" + xid + ", " + xLockObject.getDataName() + ", " + xLockObject.getLockType() + ") unlocked");

				DataLockObject dataLockObject = new DataLockObject(xLockObject.getXId(), xLockObject.getDataName(), xLockObject.getLockType());
				this.lockTable.remove(dataLockObject);

				// Check if there are any waiting transactions
				synchronized(this.waitTable) {
					// Get all the transactions waiting on this dataLock
					waitVector = this.waitTable.elements(dataLockObject);
					int waitSize = waitVector.size();
					for (int j = 0; j < waitSize; j++)
					{
						waitLockObject = (WaitLockObject)waitVector.elementAt(j);
						if (waitLockObject.getLockType() == TransactionLockObject.LockType.LOCK_WRITE)
						{
							if (j == 0)
							{
								// Get all other transactions which have locks on the
								// data item just unlocked
								Vector vect1 = this.lockTable.elements(dataLockObject);
								int vectlSize = vect1.size();

								boolean free = true;
								for (int k = 0; k < vectlSize; k++)
								{
									DataLockObject l_dl = (DataLockObject)vect1.elementAt(k);
									if (l_dl.getXId() != waitLockObject.getXId())
									{
										// Some other transaction still has a lock on the data item
										// just unlocked. So, WRITE lock cannot be granted
										free = false;
										break;
									}
								}
								// Remove interrupted thread from waitTable only if no
								// other transaction has locked this data item
								if (!free)
								{
									break;
								}

								this.waitTable.remove(waitLockObject);     
								try {
									synchronized(waitLockObject.getThread()) {
										waitLockObject.getThread().notify();
									}    
								}
								catch (Exception e) {
									System.out.println("Exception on unlock\n" + e.getMessage());
								}        
							}

							// Stop granting READ locks as soon as you find a WRITE lock
							// request in the queue of requests
							break;
						}
						else if (waitLockObject.getLockType() == TransactionLockObject.LockType.LOCK_READ)
						{
							// Remove interrupted thread from waitTable
							this.waitTable.remove(waitLockObject);    

							try {
								synchronized (waitLockObject.getThread()) {
									waitLockObject.getThread().notify();
								}    
							}
							catch (Exception e) {
								System.out.println("Exception e\n" + e.getMessage());
							}
						}
					}
				} 
			}
		} 

		return true;
	}


	// Returns true if the lock request on dataObj conflicts with already existing locks. If the lock request is a
	// redundant one (for eg: if a transaction holds a read lock on certain data item and again requests for a read
	// lock), then this is ignored. This is done by throwing RedundantLockRequestException which is handled 
	// appropriately by the caller. If the lock request is a conversion from READ lock to WRITE lock, then bitset 
	// is set. 
	private boolean LockConflict(DataLockObject dataLockObject, BitSet bitset) throws DeadlockException, RedundantLockRequestException
	{
		Vector vect = this.lockTable.elements(dataLockObject);
		int size = vect.size();

		// As soon as a lock that conflicts with the current lock request is found, return true
		for (int i = 0; i < size; i++)
		{
			DataLockObject l_dataLockObject = (DataLockObject)vect.elementAt(i);
			if (dataLockObject.getXId() == l_dataLockObject.getXId())
			{    
				// The transaction already has a lock on this data item which means that it is either
				// relocking it or is converting the lock
				if (dataLockObject.getLockType() == TransactionLockObject.LockType.LOCK_READ)
				{
					// Since transaction already has a lock (may be READ, may be WRITE. we don't
					// care) on this data item and it is requesting a READ lock, this lock request
					// is redundant.
					throw new RedundantLockRequestException(dataLockObject.getXId(), "redundant READ lock request");
				}
				else if (dataLockObject.getLockType() == TransactionLockObject.LockType.LOCK_WRITE)
				{
					// Transaction already has a lock and is requesting a WRITE lock
					// now there are two cases to analyze here
					// (1) transaction already had a READ lock
					// (2) transaction already had a WRITE lock
					// Seeing the comments at the top of this function might be helpful
					
					if (l_dataLockObject.getLockType() == TransactionLockObject.LockType.LOCK_WRITE)
					{
						// Redundant
						throw new RedundantLockRequestException(dataLockObject.getXId(), "redundant WRITE lock request");
					}
					else
					{
						// Else the transaction previously had a READ lock on the item so we want a lock conversion.
						bitset.set(0);
					}
							
				}
			} 
			else if (dataLockObject.getLockType() == TransactionLockObject.LockType.LOCK_READ)
			{
				if (l_dataLockObject.getLockType() == TransactionLockObject.LockType.LOCK_WRITE)
				{
					// Transaction is requesting a READ lock and some other transaction
					// already has a WRITE lock on it ==> conflict
					Trace.info("LM::lockConflict(" + dataLockObject.getXId() + ", " + dataLockObject.getDataName() + ") Want READ, someone has WRITE");
					return true;
				}
			}
		       	else if (dataLockObject.getLockType() == TransactionLockObject.LockType.LOCK_WRITE)
			{
				// Transaction is requesting a WRITE lock and some other transaction has either
				// a READ or a WRITE lock on it ==> conflict
				Trace.info("LM::lockConflict(" + dataLockObject.getXId() + ", " + dataLockObject.getDataName() + ") Want WRITE, someone has READ or WRITE");
				return true;
			}
		}

		// No conflicting lock found, return false
		return false;

	}

	private void WaitLock(DataLockObject dataLockObject) throws DeadlockException
	{
		Trace.info("LM::waitLock(" + dataLockObject.getXId() + ", " + dataLockObject.getDataName() + ", " + dataLockObject.getLockType() + ") called");

		// Check timestamp or add a new one.
		//
		// Will always add new timestamp for each new lock request since
		// the timeObject is deleted each time the transaction succeeds in
		// getting a lock (see Lock())
		TimeObject timeObject = new TimeObject(dataLockObject.getXId());
		TimeObject timestamp = null;
		long timeBlocked = 0;
		Thread thisThread = Thread.currentThread();
		WaitLockObject waitLockObject = new WaitLockObject(dataLockObject.getXId(), dataLockObject.getDataName(), dataLockObject.getLockType(), thisThread);

		synchronized (this.stampTable) {
			Vector vect = this.stampTable.elements(timeObject);
			if (vect.size() == 0)
			{
				// add the time stamp for this lock request to stampTable
				this.stampTable.add(timeObject);
				timestamp = timeObject;
			}
			else if (vect.size() == 1)
			{
				// Lock operation could have timed out; check for deadlock
				TimeObject prevStamp = (TimeObject)vect.firstElement();
				timestamp = prevStamp;
				timeBlocked = timeObject.getTime() - prevStamp.getTime();
				if (timeBlocked >= LockManager.DEADLOCK_TIMEOUT)
				{
					// The transaction has been waiting for a period greater than the timeout period
					cleanupDeadlock(prevStamp, waitLockObject);
				}
			}
			// Shouldn't be more than one time stamp per transaction because the transaction can be blocked
			// on just one lock request
		} 

		// Suspend thread and wait until notified
		synchronized (this.waitTable) {
			if (!this.waitTable.contains(waitLockObject))
		       	{
				// Register this transaction in the waitTable if it is not already there 
				this.waitTable.add(waitLockObject);
			}
			// Else lock manager already knows the transaction is waiting
		}

		synchronized (thisThread) {
			try {
				thisThread.wait(LockManager.DEADLOCK_TIMEOUT - timeBlocked);
				TimeObject currTime = new TimeObject(dataLockObject.getXId());
				timeBlocked = currTime.getTime() - timestamp.getTime();
				// Check if the transaction has been waiting for a period greater than the timeout period
				if (timeBlocked >= LockManager.DEADLOCK_TIMEOUT) {
					cleanupDeadlock(timestamp, waitLockObject);
				} else {
					return;
				}
			}
			catch (InterruptedException e) {
				System.out.println("Thread interrupted");
			}
		}
	}


	// CleanupDeadlock cleans up stampTable and waitTable, and throws DeadlockException
	private void cleanupDeadlock(TimeObject timeObject, WaitLockObject waitLockObject) throws DeadlockException
	{
		Trace.info("LM::deadlock(" + waitLockObject.getXId() + ", " + waitLockObject.getDataName() + ", " + waitLockObject.getLockType() + ") called");
		synchronized (this.stampTable) {
			synchronized (this.waitTable) {
				this.stampTable.remove(timeObject);
				this.waitTable.remove(waitLockObject);
			}
		}
		throw new DeadlockException(waitLockObject.getXId(), "Sleep timeout: deadlocked");
	}
}
