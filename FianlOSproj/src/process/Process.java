package process;

import java.util.ArrayList;
import java.util.List;

public class Process {

    // --- PCB Fields ---------------------------------------------
    private int processID;
    private ProcessState state;
    private int programCounter;
    private int memoryLow;
    private int memoryHigh;

    // --- Lifecycle flags ----------------------------------------
    private boolean arrived = false;
    private boolean onDisk  = false;   // tracks whether process is swapped to disk

    // --- Scheduling info ----------------------------------------
    private List<String> instructions;
    private int arrivalTime;
    private int burstTime;
    private int waitingTime;

    // --- Constructor --------------------------------------------
    public Process(int processID, List<String> instructions, int arrivalTime,
                   int memoryLow, int memoryHigh) {
        this.processID    = processID;
        this.instructions = new ArrayList<>(instructions);
        this.arrivalTime  = arrivalTime;
        this.memoryLow    = memoryLow;
        this.memoryHigh   = memoryHigh;
        this.state          = ProcessState.NEW;
        this.programCounter = 0;
        this.burstTime      = instructions.size();
        this.waitingTime    = 0;
    }

    // --- Core Methods -------------------------------------------
    public String getCurrentInstruction() {
        if (programCounter < instructions.size())
            return instructions.get(programCounter);
        return "";
    }

    public void incrementPC() { programCounter++; }

    public boolean isFinished() {
        return programCounter >= instructions.size();
    }

    public double getResponseRatio() {
        if (burstTime == 0) return 0;
        return (double)(waitingTime + burstTime) / burstTime;
    }

    // --- Getters & Setters --------------------------------------
    public int          getProcessID()               { return processID; }
    public ProcessState getState()                   { return state; }
    public void         setState(ProcessState s)     { state = s; }
    public int          getProgramCounter()          { return programCounter; }
    public void         setProgramCounter(int pc)    { programCounter = pc; }
    public int          getMemoryLow()               { return memoryLow; }
    public int          getMemoryHigh()              { return memoryHigh; }
    public int          getArrivalTime()             { return arrivalTime; }
    public int          getBurstTime()               { return burstTime; }
    public int          getWaitingTime()             { return waitingTime; }
    public void         incrementWaitingTime()       { waitingTime++; }
    public List<String> getInstructions()            { return instructions; }
    public void         setInstructions(List<String> ins) { instructions = ins; }
    public void         setMemoryLow(int low)        { memoryLow = low; }
    public void         setMemoryHigh(int high)      { memoryHigh = high; }
    public boolean      hasArrived()                 { return arrived; }
    public void         setArrived(boolean a)        { arrived = a; }
    public boolean      isOnDisk()                   { return onDisk; }
    public void         setOnDisk(boolean d)         { onDisk = d; }

    @Override
    public String toString() {
        return String.format(
            "P%d[%-8s|PC=%d/%d|Mem=%d-%d|Wait=%d%s]",
            processID, state,
            programCounter, Math.max(0, instructions.size() - 1),
            memoryLow, memoryHigh, waitingTime,
            onDisk ? "|DISK" : ""
        );
    }
}
