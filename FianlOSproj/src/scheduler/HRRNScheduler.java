package scheduler;

import interpreter.Interpreter;
import interpreter.Interpreter.ExecutionResult;
import memory.Memory;
import process.Process;
import process.ProcessState;

public class HRRNScheduler extends Scheduler {

    public HRRNScheduler(Interpreter interpreter, Memory memory) {
        super(interpreter, memory);
    }

    @Override
    protected Process selectProcess() {
        Process best     = null;
        double  maxRatio = -1;
        for (Process p : readyQueue) {
            double ratio = p.getResponseRatio();
            if (ratio > maxRatio) { maxRatio = ratio; best = p; }
        }
        readyQueue.remove(best);
        return best;
    }

    @Override
    protected void runProcess(Process p) {
        p.setState(ProcessState.RUNNING);
        log("=== [HRRN] P" + p.getProcessID()
                + " selected  (ratio=" + String.format("%.2f", p.getResponseRatio()) + ") ===");
        printQueues();

        while (true) {

            handleUnblockedProcesses();

            // --- Each instruction owns exactly one clock cycle ----------
            lastExecutedInstruction = p.getCurrentInstruction();
            log("-- Clock " + currentTime
                    + "  [HRRN] P" + p.getProcessID()
                    + "  PC=" + p.getProgramCounter());

            ExecutionResult result = interpreter.execute(p);

            // Show memory state at END of this clock cycle (before advancing)
            memory.printMemory();
            notifyGUI();          // GUI shows T=currentTime with memory after instruction

            if (result == ExecutionResult.BLOCKED) {
                // Blocking is instantaneous — NO clock cycle consumed by the CPU
                blockProcess(p);
                notifyGUI();
                return;
            }

            // *** CONTINUE and FINISHED both consume exactly 1 clock cycle ***
            currentTime++;
            updateWaitingTimes();
            handleArrivals();

            if (result == ExecutionResult.FINISHED) {
                finishProcess(p);
                notifyGUI();
                return;
            }
        }
    }
}
