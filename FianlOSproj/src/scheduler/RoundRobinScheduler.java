package scheduler;

import interpreter.Interpreter;
import interpreter.Interpreter.ExecutionResult;
import memory.Memory;
import process.Process;
import process.ProcessState;

public class RoundRobinScheduler extends Scheduler {

    private int timeSlice;

    public RoundRobinScheduler(Interpreter interpreter, Memory memory, int timeSlice) {
        super(interpreter, memory);
        this.timeSlice = timeSlice;
    }

    public RoundRobinScheduler(Interpreter interpreter, Memory memory) {
        this(interpreter, memory, 2);
    }

    public void setTimeSlice(int ts) { this.timeSlice = ts; }
    public int  getTimeSlice()       { return timeSlice; }

    @Override
    protected Process selectProcess() {
        return readyQueue.poll();
    }

    @Override
    protected void runProcess(Process p) {
        p.setState(ProcessState.RUNNING);
        log("=== [RR] P" + p.getProcessID() + " selected  (slice=" + timeSlice + ") ===");
        printQueues();

        int executed = 0;

        while (executed < timeSlice) {

            handleUnblockedProcesses();

            // --- Each instruction owns exactly one clock cycle ----------
            lastExecutedInstruction = p.getCurrentInstruction();
            log("-- Clock " + currentTime
                    + "  [RR] P" + p.getProcessID()
                    + "  instr " + (executed + 1) + "/" + timeSlice
                    + "  PC=" + p.getProgramCounter());

            ExecutionResult result = interpreter.execute(p);

            // Show memory state at end of this clock cycle (before advancing)
            memory.printMemory();
            notifyGUI();          // GUI shows T=currentTime with memory after instruction

            if (result == ExecutionResult.BLOCKED) {
                // Blocking is instantaneous — NO clock cycle consumed by the CPU
                blockProcess(p);
                notifyGUI();
                return;
            }

            // *** CONTINUE and FINISHED both consume exactly 1 clock cycle ***
            executed++;
            currentTime++;
            updateWaitingTimes();
            handleArrivals();

            if (result == ExecutionResult.FINISHED) {
                finishProcess(p);
                notifyGUI();
                return;
            }
        }

        // Time slice expired -> preempt
        if (!p.isFinished()) {
            p.setState(ProcessState.READY);
            readyQueue.add(p);
            log("[PREEMPT] P" + p.getProcessID()
                    + "  slice exhausted -> back of ready queue");
            printQueues();
            notifyGUI();
        }
    }
}
