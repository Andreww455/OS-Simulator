package mutex;

import process.Process;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class MutexManager {

    // LinkedHashMap keeps insertion order so GUI always shows:
    // userInput -> userOutput -> file
    private final Map<String, Mutex> mutexes = new LinkedHashMap<>();

    public MutexManager() {
        mutexes.put("userInput",  new Mutex());
        mutexes.put("userOutput", new Mutex());
        mutexes.put("file",       new Mutex());
    }

    public boolean semWait(String resource, Process p) {
        return mutexes.get(resource).semWait(p);
    }

    public Process semSignal(String resource, Process p) {
        return mutexes.get(resource).semSignal(p);
    }

    /** Exposed for GUI mutex panel -- read-only view of all mutexes */
    public Map<String, Mutex> getMutexMap() {
        return Collections.unmodifiableMap(mutexes);
    }
}
