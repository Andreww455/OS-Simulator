package interpreter;

import memory.Memory;
import mutex.MutexManager;
import process.Process;
import process.ProcessState;
import systemcalls.SystemCalls;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class Interpreter {

    public enum ExecutionResult { CONTINUE, BLOCKED, FINISHED }

    private Memory           memory;
    private MutexManager     mutexManager;
    private SystemCalls      systemCalls;
    private Consumer<String> logger = System.out::println;
    private List<Process>    unblockedProcesses = new ArrayList<>();

    public Interpreter(Memory memory, MutexManager mutexManager) {
        this.memory       = memory;
        this.mutexManager = mutexManager;
        this.systemCalls  = new SystemCalls(memory);
    }

    public void setLogger(Consumer<String> logger)              { this.logger = logger; }
    public void setInputProvider(SystemCalls.InputProvider p)   { systemCalls.setInputProvider(p); }
    public List<Process> getUnblockedProcesses()                { return unblockedProcesses; }

    // -- Execute one instruction -----------------------------------
    public ExecutionResult execute(Process p) {

        if (p.isFinished()) {
            p.setState(ProcessState.FINISHED);
            return ExecutionResult.FINISHED;
        }

        String instruction = p.getCurrentInstruction();
        logger.accept(String.format("[EXEC ] P%d | PC:%-2d | %s",
                p.getProcessID(), p.getProgramCounter(), instruction));

        String[] parts  = instruction.trim().split("\\s+");
        String   cmd    = parts[0];

        try {
            switch (cmd) {

                case "assign":
                    handleAssign(p, parts);
                    break;

                case "print":
                    String val = resolveValue(p, parts[1]);
                    logger.accept("[SCREEN] " + val);
                    System.out.println("[SCREEN] " + val);
                    break;

                case "writeFile":
                    handleWriteFile(p, parts);
                    break;

                case "readFile":
                    logger.accept("[FILE  ] read standalone -> " + resolveValue(p, parts[1]));
                    break;

                case "printFromTo":
                    handlePrintFromTo(p, parts);
                    break;

                // -- Mutex ops: handled silently -- state shown in GUI mutex panel --
                case "semWait":
                    boolean acquired = mutexManager.semWait(parts[1], p);
                    if (!acquired) {
                        // Will appear as BLOCKED in the scheduler log + mutex panel
                        return ExecutionResult.BLOCKED;
                    }
                    break;

                case "semSignal":
                    Process unblocked = mutexManager.semSignal(parts[1], p);
                    if (unblocked != null) {
                        unblockedProcesses.add(unblocked);
                    }
                    break;

                default:
                    logger.accept("[ERROR ] Unknown command: " + cmd);
            }

        } catch (Exception e) {
            logger.accept("[ERROR ] " + e.getMessage());
        }

        p.incrementPC();
        memory.updatePCB(p);

        if (p.isFinished()) {
            p.setState(ProcessState.FINISHED);
            memory.updatePCB(p);
            return ExecutionResult.FINISHED;
        }

        return ExecutionResult.CONTINUE;
    }

    // -- assign ----------------------------------------------------
    private void handleAssign(Process p, String[] parts) throws Exception {
        String varName = parts[1];
        String value;

        if (parts[2].equals("input")) {
            value = systemCalls.input(varName);
            logger.accept("[INPUT ] " + varName + " = \"" + value + "\"");

        } else if (parts[2].equals("readFile")) {
            String filename = resolveValue(p, parts[3]);
            value = systemCalls.readFile(filename);
            logger.accept("[FILE  ] read \"" + filename + "\" -> \"" + value + "\"");

        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 2; i < parts.length; i++) {
                if (i > 2) sb.append(" ");
                sb.append(parts[i]);
            }
            value = resolveValue(p, sb.toString());
        }

        systemCalls.writeMemory(p, varName, value);
        logger.accept("[MEM   ] " + varName + " <- \"" + value + "\"");
    }

    // -- writeFile -------------------------------------------------
    private void handleWriteFile(Process p, String[] parts) throws Exception {
        String filename = resolveValue(p, parts[1]);
        String data     = resolveValue(p, parts[2]);
        systemCalls.writeFile(filename, data);
        logger.accept("[FILE  ] wrote \"" + data + "\" -> " + filename);
    }

    // -- printFromTo -----------------------------------------------
    private void handlePrintFromTo(Process p, String[] parts) {
        int from = Integer.parseInt(resolveValue(p, parts[1]));
        int to   = Integer.parseInt(resolveValue(p, parts[2]));
        StringBuilder sb = new StringBuilder();
        for (int i = from; i <= to; i++) {
            sb.append(i);
            if (i < to) sb.append(" ");
        }
        logger.accept("[SCREEN] " + sb);
        System.out.println("[SCREEN] " + sb);
    }

    // -- Resolve variable -> value or literal ----------------------
    private String resolveValue(Process p, String name) {
        String v = systemCalls.readMemory(p, name);
        return (v != null) ? v : name;
    }
}
