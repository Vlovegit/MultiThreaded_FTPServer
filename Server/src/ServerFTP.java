import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ServerFTP {
    
    private Queue<Integer> putQueue;
	private Set<Integer> abortSet;
    private Map<Path, ReentrantReadWriteLock> fileStatus;
	private Map<Integer, Path> filePathMapper;
	

    public ServerFTP() {
		putQueue = new LinkedList<Integer>();
		abortSet = new HashSet<Integer>();
        fileStatus = new HashMap<Path, ReentrantReadWriteLock>();
		filePathMapper = new HashMap<Integer, Path>();
	}

    public void showStatus() {
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

    public int generateId() {
		return new Random().nextInt(70000) + 10000;
	}
	
	public synchronized boolean remove(Path path) {
		return !fileStatus.containsKey(path);
	}
	
	public synchronized void abort(int commandID) {
		abortSet.add(commandID);
	}

    public synchronized boolean getPutLock(Path path, int commandID) {
                
                if (putQueue.peek() == commandID) {
                    if (fileStatus.containsKey(path)) {
                        if (fileStatus.get(path).writeLock().tryLock()) {
                            showStatus();
                            return true;
                        } else
                            return false;
                    } else {
                        fileStatus.put(path, new ReentrantReadWriteLock());
                        fileStatus.get(path).writeLock().lock();
                        showStatus();
                        return true;
                    }
                }
                return false;
            }
        
        public synchronized int getPutLockId(Path path) {
                int commandID = 0;
                
                while (filePathMapper.containsKey(commandID = generateId()));
                filePathMapper.put(commandID, path);
                
                putQueue.add(commandID);
                
                return commandID;
        }
            
        public synchronized void releasePutLock(Path path, int commandID) {
                
                try {
                    fileStatus.get(path).writeLock().unlock();
                    filePathMapper.remove(commandID);
                    putQueue.poll();
                    
                    if (fileStatus.get(path).getReadLockCount() == 0 && !fileStatus.get(path).isWriteLocked())
                        fileStatus.remove(path);
                    showStatus();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                
        }

            public synchronized int getGetLock(Path path) {
                int commandID = 0;
                
                //if Path is in fileStatus
                if (fileStatus.containsKey(path)) {
                    //try to get read lock
                    if (fileStatus.get(path).readLock().tryLock()) {
                        //generate a unique terminate id
                        while (filePathMapper.containsKey(commandID = generateId()));
                        
                        //add to filePathMapper
                        filePathMapper.put(commandID, path);
                        showStatus();
                        return commandID;
                    }
                    //didn't get lock
                    else
                        return -1;
                
                }
                //acquire get lock
                else {
                    //add to fileStatus and get readLock
                    fileStatus.put(path, new ReentrantReadWriteLock());
                    fileStatus.get(path).readLock().lock();
                    
                    //generate unique terminate id
                    while (filePathMapper.containsKey(commandID = generateId()));
                    
                    //add to filePathMapper
                    filePathMapper.put(commandID, path);
                    showStatus();
                    return commandID;
                }
                
            }

            public synchronized boolean abortGet(Path path, int commandID) {
                try {
                    if (abortSet.contains(commandID)) {
                        abortSet.remove(commandID);
                        filePathMapper.remove(commandID);
                        fileStatus.get(path).readLock().unlock();
                        
                        if (fileStatus.get(path).getReadLockCount() == 0 && !fileStatus.get(path).isWriteLocked())
                            fileStatus.remove(path);
                        showStatus();
                        return true;
                    }
                } catch (Exception e) {
                    e.printStackTrace(); 
                }
                
                return false;
            }
            
            public synchronized boolean abortPut(Path path, int commandID) {
                
                try {
                    if (abortSet.contains(commandID)) {
                        abortSet.remove(commandID);
                        filePathMapper.remove(commandID);
                        fileStatus.get(path).writeLock().unlock();
                        putQueue.poll();
                        Files.deleteIfExists(path);
                        
                        if (fileStatus.get(path).getReadLockCount() == 0 && !fileStatus.get(path).isWriteLocked())
                            fileStatus.remove(path);
                        showStatus();
                        return true;
                    }
                } catch (Exception e) {
                    e.printStackTrace(); 
                }
                
                return false;
            }
            
            public synchronized void releaseGetLock(Path path, int commandID) {
                
                try {
                    //remove locks
                    fileStatus.get(path).readLock().unlock();
                    filePathMapper.remove(commandID);
                    
                    if (fileStatus.get(path).getReadLockCount() == 0 && !fileStatus.get(path).isWriteLocked())
                        fileStatus.remove(path);
                    showStatus();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                
            }


}
