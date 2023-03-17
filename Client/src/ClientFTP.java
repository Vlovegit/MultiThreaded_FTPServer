import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ClientFTP {

    private Set<Path> moveSet;
	private Set<Integer> abortSet;
	private Map<Integer, Path> filePathMapper;

    public ClientFTP() {
		moveSet = new HashSet<Path>();
		abortSet = new HashSet<Integer>();
		filePathMapper = new HashMap<Integer, Path>();
	}

    public synchronized boolean move(Path path) {
		return !moveSet.contains(path);
	}
	
    public synchronized void moveOut(Path path, int commandID) {
		try {
			moveSet.remove(path);
			filePathMapper.remove(commandID);
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

	public synchronized void moveIn(Path path, int commandID) {
		moveSet.add(path);
		filePathMapper.put(commandID, path);
	}

    public synchronized boolean abortAppend(int commandID) {
		if (filePathMapper.containsKey(commandID)) {
			abortSet.add(commandID);
			return true;
		} else
			return false;
	}

    public synchronized boolean abortPut(Path path, int commandID) {
		try {
			if (abortSet.contains(commandID)) {
				filePathMapper.remove(commandID);
				moveSet.remove(path);
				abortSet.remove(commandID);
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return false;
	}

    public synchronized boolean abortGet(Path path, Path serverPath, int commandID) {
		try {
			if (abortSet.contains(commandID)) {
				filePathMapper.remove(commandID);
				moveSet.remove(serverPath);
				abortSet.remove(commandID);
				Files.deleteIfExists(path);
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return false;
	}

    public synchronized boolean quit() {
		return moveSet.isEmpty();
	}

}
