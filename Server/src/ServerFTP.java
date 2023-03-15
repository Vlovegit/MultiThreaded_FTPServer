import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ServerFTP {
    private Map<Path, ReentrantReadWriteLock> fileStatus;
	private Map<Integer, Path> filePathMapper;
	private Queue<Integer> putQueue;
	private Set<Integer> abortSet;

    public ServerFTP() {
		fileStatus = new HashMap<Path, ReentrantReadWriteLock>();
		filePathMapper = new HashMap<Integer, Path>();
		putQueue = new LinkedList<Integer>();
		abortSet = new HashSet<Integer>();
	}

    public void status() {
		System.out.println("ServerFTP: fileStatus-filePathMapper-putQueue-abortSet");
		System.out.println(fileStatus.toString());
		System.out.println(filePathMapper.toString());
		System.out.println(putQueue.toString());
		System.out.println(abortSet.toString());
	}
	
	@Override
	public String toString() {
		return "ServerFTP [fileStatus=" + fileStatus + ", filePathMapper="
				+ filePathMapper + ", putQueue=" + putQueue
				+ ", abortSet=" + abortSet + "]";
	}

    public int generateID() {
		return new Random().nextInt(90000) + 10000;
	}
	
	public synchronized boolean delete(Path path) {
		return !fileStatus.containsKey(path);
	}
	
	public synchronized void terminate(int commandID) {
		abortSet.add(commandID);
	}

    public synchronized boolean putIN(Path path, int commandID) {
                
                if (putQueue.peek() == commandID) {
                    if (fileStatus.containsKey(path)) {
                        if (fileStatus.get(path).writeLock().tryLock()) {
                            
                            return true;
                        } else
                            return false;
                    } else {
                        fileStatus.put(path, new ReentrantReadWriteLock());
                        fileStatus.get(path).writeLock().lock();
                        
                        return true;
                    }
                }
                return false;
            }
        
        public synchronized int putIN_ID(Path path) {
                int commandID = 0;
                
                while (filePathMapper.containsKey(commandID = generateID()));
                filePathMapper.put(commandID, path);
                
                putQueue.add(commandID);
                
                return commandID;
        }
            
        public synchronized void putOUT(Path path, int commandID) {
                
                try {
                    fileStatus.get(path).writeLock().unlock();
                    filePathMapper.remove(commandID);
                    putQueue.poll();
                    
                    if (fileStatus.get(path).getReadLockCount() == 0 && !fileStatus.get(path).isWriteLocked())
                        fileStatus.remove(path);
                } catch (Exception e) {
                    e.printStackTrace(); //TODO
                }
                
        }

            public synchronized int getIN(Path path) {
                int commandID = 0;
                
                //if Path is in fileStatus
                if (fileStatus.containsKey(path)) {
                    //try to get read lock
                    if (fileStatus.get(path).readLock().tryLock()) {
                        //generate unique 5 digit number
                        while (filePathMapper.containsKey(commandID = generateID()));
                        
                        //add to filePathMapper
                        filePathMapper.put(commandID, path);
                        
                        return commandID;
                    }
                    //didn't get lock
                    else
                        return -1;
                }
                //acquire lock
                else {
                    //add to fileStatus and get readLock
                    fileStatus.put(path, new ReentrantReadWriteLock());
                    fileStatus.get(path).readLock().lock();
                    
                    //generate unique 5 digit number
                    while (filePathMapper.containsKey(commandID = generateID()));
                    
                    //add to filePathMapper
                    filePathMapper.put(commandID, path);
                    
                    return commandID;
                }
            }

            public synchronized boolean terminateGET(Path path, int commandID) {
                try {
                    if (abortSet.contains(commandID)) {
                        abortSet.remove(commandID);
                        filePathMapper.remove(commandID);
                        fileStatus.get(path).readLock().unlock();
                        
                        if (fileStatus.get(path).getReadLockCount() == 0 && !fileStatus.get(path).isWriteLocked())
                            fileStatus.remove(path);
                        return true;
                    }
                } catch (Exception e) {
                    e.printStackTrace(); //TODO
                }
                
                return false;
            }
            
            public synchronized boolean terminatePUT(Path path, int commandID) {
                
                try {
                    if (abortSet.contains(commandID)) {
                        abortSet.remove(commandID);
                        filePathMapper.remove(commandID);
                        fileStatus.get(path).writeLock().unlock();
                        putQueue.poll();
                        Files.deleteIfExists(path);
                        
                        if (fileStatus.get(path).getReadLockCount() == 0 && !fileStatus.get(path).isWriteLocked())
                            fileStatus.remove(path);
                        
                        return true;
                    }
                } catch (Exception e) {
                    e.printStackTrace(); //TODO
                }
                
                return false;
            }
            
            public synchronized void getOUT(Path path, int commandID) {
                
                try {
                    //remove locks
                    fileStatus.get(path).readLock().unlock();
                    filePathMapper.remove(commandID);
                    
                    if (fileStatus.get(path).getReadLockCount() == 0 && !fileStatus.get(path).isWriteLocked())
                        fileStatus.remove(path);
                } catch (Exception e) {
                    e.printStackTrace(); //TODO
                }
                
            }


}
