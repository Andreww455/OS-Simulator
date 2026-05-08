package scheduler;

import gui.OSSimulatorGUI;
import interpreter.Interpreter;
import memory.Memory;
import process.Process;
import process.ProcessState;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.*;

public abstract class Scheduler {

    protected List<Process>  allProcesses;
    protected Queue<Process> readyQueue;
    protected Queue<Process> blockedQueue;

    protected Interpreter interpreter;
    protected Memory      memory;

    protected int     currentTime    = 0;
    protected Process currentProcess          = null;
    protected String  lastExecutedInstruction = "";

    protected OSSimulatorGUI gui = null;

    public Scheduler(Interpreter interpreter, Memory memory) {
        this.interpreter  = interpreter;
        this.memory       = memory;
        this.allProcesses = new ArrayList<>();
        this.readyQueue   = new LinkedList<>();
        this.blockedQueue = new LinkedList<>();
    }

    public void addProcess(Process p) { allProcesses.add(p); }

    // -- Connect GUI -----------------------------------------------
    public void setGUI(OSSimulatorGUI gui) {
        this.gui = gui;

        interpreter.setLogger(msg -> {
            System.out.println(msg);
            gui.log(msg);
        });

        interpreter.setInputProvider(prompt -> showInputDialog(prompt));
    }

    /**
     * Lightweight modal input dialog built from plain JavaFX nodes.
     */
    private String showInputDialog(String prompt) {
        final String[] result = {""};

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Process Input");
        dialog.setResizable(false);

        Label lbl = new Label(prompt);
        lbl.setStyle("-fx-text-fill:#dce1f0; -fx-font-size:13px;");

        TextField field = new TextField();
        field.setStyle(
            "-fx-background-color:#242837; -fx-text-fill:#dce1f0;" +
            "-fx-border-color:#5282ff; -fx-border-width:1;" +
            "-fx-border-radius:3; -fx-background-radius:3;" +
            "-fx-font-size:13px; -fx-pref-width:260px;"
        );

        Button btnOk = new Button("OK");
        btnOk.setDefaultButton(true);
        btnOk.setStyle(
            "-fx-background-color:#3ca050; -fx-text-fill:white;" +
            "-fx-font-weight:bold; -fx-border-radius:3;" +
            "-fx-background-radius:3; -fx-padding:4 16 4 16;"
        );
        btnOk.setOnAction(e -> {
            result[0] = field.getText().trim();
            dialog.close();
        });

        HBox buttons = new HBox(btnOk);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(10, lbl, field, buttons);
        root.setPadding(new Insets(18));
        root.setStyle("-fx-background-color:#1c1f2a;");

        dialog.setScene(new Scene(root));
        dialog.showAndWait();
        return result[0];
    }

    // -- One scheduler step ----------------------------------------
    public void step() {
        handleArrivals();
        handleUnblockedProcesses();

        if (readyQueue.isEmpty()) {
            boolean allDone = allProcesses.stream()
                    .allMatch(p -> p.getState() == ProcessState.FINISHED);
            if (!allDone) {
                log("-- Clock " + currentTime + " : idle --");
                currentTime++;
                notifyGUI();   // refresh memory/queues even on idle cycles
            }
            currentProcess = null;
            return;
        }

        Process current = selectProcess();
        if (current == null) return;
        currentProcess = current;

        // FIX: if process is on disk, swap it back in (with eviction if memory is full)
        if (current.isOnDisk()) {
            boolean ok = swapInProcess(current);
            if (!ok) {
                // Could not make room -- put back and wait
                readyQueue.add(current);
                currentProcess = null;
                return;
            }
        }

        printQueues();
        runProcess(current);
    }

    // -- Swap-in helper (also handles memory-full by evicting) -----
    /**
     * Loads a process from disk back into memory.
     * If memory is full, evicts the first eligible non-running, non-finished,
     * non-disk process to make room, then retries.
     *
     * Called from both Scheduler.step() and MLFQScheduler.step().
     */
    protected boolean swapInProcess(Process p) {
        log("[SWAP IN ] P" + p.getProcessID() + " <- disk");
        try {
            // First attempt: load directly
            if (memory.loadProcessFromDisk(p)) {
                memory.updatePCB(p);
                log("[SWAP IN ] P" + p.getProcessID()
                        + " loaded at [" + p.getMemoryLow()
                        + "-" + p.getMemoryHigh() + "]");
                return true;
            }

            // Memory full: sort candidates highest-address-first so that freed space
            // stays adjacent to the existing free block at the top of memory,
            // preventing fragmentation that forces unnecessary second evictions.
            List<Process> sorted = new ArrayList<>(allProcesses);
            sorted.sort((a, b) -> Integer.compare(b.getMemoryLow(), a.getMemoryLow()));

            for (Process candidate : sorted) {
                if (candidate != p
                        && !candidate.isOnDisk()
                        && candidate.getState() != ProcessState.RUNNING
                        && candidate.getState() != ProcessState.FINISHED) {
                    memory.saveProcessToDisk(candidate);
                    memory.deallocate(candidate);
                    candidate.setOnDisk(true);
                    log("[SWAP OUT] P" + candidate.getProcessID()
                            + " -> disk (making room for P" + p.getProcessID() + ")");

                    if (memory.loadProcessFromDisk(p)) {
                        memory.updatePCB(p);
                        log("[SWAP IN ] P" + p.getProcessID()
                                + " loaded at [" + p.getMemoryLow()
                                + "-" + p.getMemoryHigh() + "]");
                        return true;
                    }
                }
            }
        } catch (IOException e) {
            log("[ERROR] Swap failed: " + e.getMessage());
        }
        log("[ERROR] Could not swap in P" + p.getProcessID() + " -- no free memory");
        return false;
    }

    // -- Arrivals --------------------------------------------------
    /**
     * FIX: processes were added to readyQueue even when allocation failed.
     * Now we only add the process if memory was successfully allocated.
     */
    protected void handleArrivals() {
        for (Process p : allProcesses) {
            if (!p.hasArrived() && p.getArrivalTime() <= currentTime) {
                p.setState(ProcessState.READY);
                p.setArrived(true);   // mark arrived regardless of allocation outcome

                boolean allocated = memory.allocateWithSwap(p, allProcesses, this::log);
                if (!allocated) {
                    log("[ERROR] Not enough memory for P" + p.getProcessID() + " -- skipping");
                    continue;          // FIX: do NOT add to readyQueue without memory
                }

                readyQueue.add(p);
                log("[ARRIVAL] P" + p.getProcessID()
                        + " at T=" + currentTime
                        + "  mem[" + p.getMemoryLow() + "-" + p.getMemoryHigh() + "]");
            }
        }
    }

    // -- Unblock ---------------------------------------------------
    protected void handleUnblockedProcesses() {
        List<Process> list = interpreter.getUnblockedProcesses();
        for (Process p : list) {
            blockedQueue.remove(p);
            if (!readyQueue.contains(p)) {
                p.setState(ProcessState.READY);
                readyQueue.add(p);
            }
            log("[READY  ] P" + p.getProcessID() + " unblocked -> ready queue");
        }
        list.clear();
    }

    protected void finishProcess(Process p) {
        p.setState(ProcessState.FINISHED);
        memory.deallocate(p);
        log("[FINISH ] P" + p.getProcessID() + " completed at T=" + currentTime);
        printQueues();
    }

    protected void blockProcess(Process p) {
        p.setState(ProcessState.BLOCKED);
        if (!blockedQueue.contains(p)) blockedQueue.add(p);
        log("[BLOCK  ] P" + p.getProcessID() + " -> blocked queue");
        printQueues();
    }

    protected void updateWaitingTimes() {
        for (Process p : readyQueue) p.incrementWaitingTime();
    }

    protected void printQueues() {
        StringBuilder rq = new StringBuilder("[READY   QUEUE] ");
        for (Process p : readyQueue)   rq.append("P").append(p.getProcessID()).append(" ");
        StringBuilder bq = new StringBuilder("[BLOCKED QUEUE] ");
        for (Process p : blockedQueue) bq.append("P").append(p.getProcessID()).append(" ");
        log(rq.toString());
        log(bq.toString());
    }

    protected void log(String msg) {
        if (gui != null) gui.log(msg);
        else System.out.println(msg);
    }

    /**
     * Trigger a full GUI refresh (memory + queues + disk + mutexes).
     * Called after every individual instruction execution so changes appear
     * immediately rather than once per full scheduler step.
     */
    protected void notifyGUI() {
        if (gui != null) gui.refreshUI();
    }

    public boolean isFinished() {
        return allProcesses.stream()
                .allMatch(p -> p.getState() == ProcessState.FINISHED);
    }

    public Queue<Process> getReadyQueue()               { return readyQueue; }
    public Queue<Process> getBlockedQueue()             { return blockedQueue; }
    public int            getCurrentTime()              { return currentTime; }
    public Memory         getMemory()                   { return memory; }
    public Process        getCurrentProcess()           { return currentProcess; }
    public List<Process>  getAllProcesses()             { return allProcesses; }
    public String         getLastExecutedInstruction()  { return lastExecutedInstruction; }

    protected abstract Process selectProcess();
    protected abstract void    runProcess(Process p);
}
