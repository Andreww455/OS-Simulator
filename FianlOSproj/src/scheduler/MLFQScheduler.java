package scheduler;

import interpreter.Interpreter;
import interpreter.Interpreter.ExecutionResult;
import memory.Memory;
import process.Process;
import process.ProcessState;

import java.util.*;

public class MLFQScheduler extends Scheduler {

    private static final int LEVELS = 4;

    private List<Queue<Process>>  queues       = new ArrayList<>();
    private Map<Integer, Integer> processLevel = new HashMap<>();

    public MLFQScheduler(Interpreter interpreter, Memory memory) {
        super(interpreter, memory);
        for (int i = 0; i < LEVELS; i++) queues.add(new LinkedList<>());
    }

    // -- Override step(): check MLFQ queues, not base readyQueue --
    @Override
    public void step() {
        handleArrivals();
        handleUnblockedProcesses();

        boolean anyReady = queues.stream().anyMatch(q -> !q.isEmpty());
        if (!anyReady) {
            boolean allDone = allProcesses.stream()
                    .allMatch(p -> p.getState() == ProcessState.FINISHED);
            if (!allDone) {
                log("-- Clock " + currentTime + " : idle --");
                currentTime++;
            }
            currentProcess = null;
            return;
        }

        Process current = selectProcess();
        if (current == null) return;
        currentProcess = current;

        if (current.isOnDisk()) {
            boolean ok = swapInProcess(current);
            if (!ok) {
                int lvl = processLevel.getOrDefault(current.getProcessID(), 0);
                queues.get(lvl).add(current);
                currentProcess = null;
                return;
            }
        }

        printQueues();
        runProcess(current);
    }

    @Override
    protected void handleArrivals() {
        for (Process p : allProcesses) {
            if (!p.hasArrived() && p.getArrivalTime() <= currentTime) {
                p.setState(ProcessState.READY);
                p.setArrived(true);
                boolean ok = memory.allocateWithSwap(p, allProcesses, this::log);
                if (!ok) { log("[ERROR] No memory for P" + p.getProcessID()); continue; }
                queues.get(0).add(p);
                processLevel.put(p.getProcessID(), 0);
                log("[ARRIVAL] P" + p.getProcessID()
                        + " -> Q0 at T=" + currentTime
                        + "  mem[" + p.getMemoryLow() + "-" + p.getMemoryHigh() + "]");
            }
        }
    }

    @Override
    protected void handleUnblockedProcesses() {
        List<Process> list = interpreter.getUnblockedProcesses();
        for (Process p : list) {
            blockedQueue.remove(p);
            p.setState(ProcessState.READY);
            int level = processLevel.getOrDefault(p.getProcessID(), 0);
            if (!queues.get(level).contains(p)) queues.get(level).add(p);
            log("[READY  ] P" + p.getProcessID() + " unblocked -> Q" + level);
        }
        list.clear();
    }

    @Override
    protected void updateWaitingTimes() {
        for (Queue<Process> q : queues)
            for (Process p : q) p.incrementWaitingTime();
    }

    @Override
    public Queue<Process> getReadyQueue() {
        Queue<Process> combined = new LinkedList<>();
        for (Queue<Process> q : queues) combined.addAll(q);
        return combined;
    }

    @Override
    protected Process selectProcess() {
        for (int i = 0; i < LEVELS; i++)
            if (!queues.get(i).isEmpty()) return queues.get(i).poll();
        return null;
    }

    @Override
    protected void runProcess(Process p) {
        int level   = processLevel.getOrDefault(p.getProcessID(), 0);
        int quantum = (level < LEVELS - 1)
                ? (int) Math.pow(2, level)
                : (int) Math.pow(2, LEVELS - 1);

        p.setState(ProcessState.RUNNING);
        log("=== [MLFQ] P" + p.getProcessID()
                + "  Q" + level + "  quantum=" + quantum + " ===");
        printQueues();

        int executed = 0;

        while (executed < quantum) {
            handleUnblockedProcesses();
            handleArrivals();

            // --- Each instruction owns exactly one clock cycle ----------
            lastExecutedInstruction = p.getCurrentInstruction();
            log("-- Clock " + currentTime
                    + "  [MLFQ] P" + p.getProcessID()
                    + "  Q" + level
                    + "  instr " + (executed + 1) + "/" + quantum
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
            executed++;
            currentTime++;
            updateWaitingTimes();

            if (result == ExecutionResult.FINISHED) { finishProcess(p); notifyGUI(); return; }
        }

        // Demote only if another process exists
        boolean otherExists = allProcesses.stream()
                .filter(proc -> proc != p)
                .anyMatch(proc -> proc.getState() != ProcessState.FINISHED);

        int targetLevel = otherExists ? Math.min(level + 1, LEVELS - 1) : level;
        if (otherExists)
            log("[DEMOTE ] P" + p.getProcessID() + "  Q" + level + " -> Q" + targetLevel);
        else
            log("[STAY   ] P" + p.getProcessID() + " stays Q" + level + " (only active)");

        processLevel.put(p.getProcessID(), targetLevel);
        p.setState(ProcessState.READY);
        queues.get(targetLevel).add(p);
        printQueues();
        notifyGUI();
    }

    @Override
    protected void printQueues() {
        log("-- MLFQ State @ T=" + currentTime + " ------------------");
        for (int i = 0; i < LEVELS; i++) {
            int qs = (int) Math.pow(2, Math.min(i, LEVELS - 1));
            StringBuilder q = new StringBuilder("  Q" + i + " [q=" + qs + "]: ");
            if (queues.get(i).isEmpty()) q.append("(empty)");
            else for (Process proc : queues.get(i)) q.append("P").append(proc.getProcessID()).append(" ");
            log(q.toString());
        }
        StringBuilder bq = new StringBuilder("  Blocked : ");
        if (blockedQueue.isEmpty()) bq.append("(empty)");
        else for (Process p : blockedQueue) bq.append("P").append(p.getProcessID()).append(" ");
        log(bq.toString());
        log("--------------------------------------------------");
    }
}
