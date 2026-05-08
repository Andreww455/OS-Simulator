package mutex;

import process.Process;

import java.util.LinkedList;
import java.util.Queue;

public class Mutex {

    private boolean locked = false;
    private Process owner  = null;
    private Queue<Process> waitingQueue = new LinkedList<>();

    // -- Acquire ---------------------------------------------------
    public boolean semWait(Process p) {
        if (locked && owner == p) return true;   // re-entrant same owner
        if (!locked) {
            locked = true;
            owner  = p;
            return true;
        }
        waitingQueue.add(p);
        return false;
    }

    // -- Release ---------------------------------------------------
    public Process semSignal(Process p) {
        if (owner != p) return null;             // only owner may release
        if (waitingQueue.isEmpty()) {
            locked = false;
            owner  = null;
            return null;
        }
        Process next = waitingQueue.poll();
        owner = next;                            // transfer ownership
        return next;
    }

    // -- Getters (used by GUI mutex panel) -------------------------
    public boolean       isLocked()      { return locked; }
    public Process       getOwner()      { return owner; }
    public Queue<Process> getWaitingQueue() { return waitingQueue; }
}
