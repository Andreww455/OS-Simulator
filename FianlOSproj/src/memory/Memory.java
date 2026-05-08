package memory;

import process.Process;
import process.ProcessState;

import java.io.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class Memory {

    private String[]  memory;
    private boolean[] occupied;

    public static final int MEMORY_SIZE    = 40;
    public static final int VARIABLE_SLOTS = 3;
    public static final int PCB_SLOTS      = 5;

    public Memory() {
        memory   = new String[MEMORY_SIZE];
        occupied = new boolean[MEMORY_SIZE];
    }

    // --- Allocate -----------------------------------------------
    public boolean allocate(Process p) {
        int totalSize = p.getInstructions().size() + VARIABLE_SLOTS + PCB_SLOTS;
        int start     = findFreeBlock(totalSize);
        if (start == -1) return false;

        int index = start;

        for (String ins : p.getInstructions()) {
            memory[index]   = "ins:" + ins;
            occupied[index] = true;
            index++;
        }
        for (int i = 0; i < VARIABLE_SLOTS; i++) {
            memory[index]   = "var:null";
            occupied[index] = true;
            index++;
        }
        // --- PCB ---
        memory[index] = "pid:" + p.getProcessID();
        occupied[index++] = true;

        memory[index] = "state:" + p.getState();
        occupied[index++] = true;

        memory[index] = "pc:" + p.getProgramCounter();
        occupied[index++] = true;

        memory[index] = "low:" + start;
        occupied[index++] = true;

        memory[index] = "high:" + (start + totalSize - 1);
        occupied[index++] = true;

        p.setMemoryLow(start);
        p.setMemoryHigh(start + totalSize - 1);
        return true;
    }

    // --- Swap-out: save process memory to disk file -------------
    public void saveProcessToDisk(Process p) throws IOException {
        String filename = "disk_p" + p.getProcessID() + ".txt";
        try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {
            pw.println("PID="   + p.getProcessID());
            pw.println("PC="    + p.getProgramCounter());
            pw.println("STATE=" + p.getState());
            pw.println("SIZE="  + (p.getMemoryHigh() - p.getMemoryLow() + 1));
            for (int i = p.getMemoryLow(); i <= p.getMemoryHigh(); i++) {
                pw.println(i + "=" + (memory[i] == null ? "null" : memory[i]));
            }
        }
    }

    // --- Swap-in: restore process memory from disk file ---------
    public boolean loadProcessFromDisk(Process p) throws IOException {
        String filename = "disk_p" + p.getProcessID() + ".txt";
        File f = new File(filename);
        if (!f.exists()) return false;

        List<String> words = new ArrayList<>();
        int savedPC = p.getProgramCounter();

        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("PC="))        savedPC = Integer.parseInt(line.substring(3));
                else if (line.startsWith("SIZE=")) { /* skip */ }
                else if (line.matches("\\d+=.*")) {
                    int eq = line.indexOf('=');
                    words.add(line.substring(eq + 1));
                }
            }
        }

        int totalSize = words.size();
        if (totalSize == 0) return false;

        int start = findFreeBlock(totalSize);
        if (start == -1) return false;   // caller must evict first

        for (int i = 0; i < totalSize; i++) {
            memory[start + i]   = words.get(i);
            occupied[start + i] = true;
        }

        // Rewrite low/high words to reflect the new position in memory
        for (int i = start; i < start + totalSize; i++) {
            if (memory[i] != null && memory[i].startsWith("low:"))
                memory[i] = "low:" + start;
            if (memory[i] != null && memory[i].startsWith("high:"))
                memory[i] = "high:" + (start + totalSize - 1);
        }

        p.setMemoryLow(start);
        p.setMemoryHigh(start + totalSize - 1);
        p.setProgramCounter(savedPC);
        p.setOnDisk(false);
        f.delete();
        return true;
    }

    // --- Allocate with automatic swap-out if full ---------------
    /**
     * Tries to allocate memory for newProcess.
     * If not enough room, evicts the first eligible candidate and retries.
     * Returns true on success.
     *
     * FIX: was logging "[SWAP IN ]" for a brand-new allocation -- corrected to "[ALLOC  ]".
     */
    public boolean allocateWithSwap(Process newProcess, List<Process> candidates,
                                    Consumer<String> logger) {
        if (allocate(newProcess)) return true;

        // Sort candidates so the highest-address process is evicted first.
        // This keeps freed space adjacent to the existing free block at the top
        // of memory, preventing fragmentation that would require multiple evictions.
        List<Process> sorted = new ArrayList<>(candidates);
        sorted.sort((a, b) -> Integer.compare(b.getMemoryLow(), a.getMemoryLow()));

        for (Process candidate : sorted) {
            if (candidate.getState() != ProcessState.RUNNING
                    && candidate.getState() != ProcessState.FINISHED
                    && !candidate.isOnDisk()
                    && candidate != newProcess) {
                try {
                    saveProcessToDisk(candidate);
                    deallocate(candidate);
                    candidate.setOnDisk(true);
                    logger.accept("[SWAP OUT] P" + candidate.getProcessID()
                            + " -> disk (disk_p" + candidate.getProcessID() + ".txt)");

                    if (allocate(newProcess)) {
                        logger.accept("[ALLOC  ] P" + newProcess.getProcessID()
                                + " allocated at ["  + newProcess.getMemoryLow()
                                + "-" + newProcess.getMemoryHigh() + "]");
                        return true;
                    }
                } catch (IOException e) {
                    logger.accept("[ERROR] Swap-out failed: " + e.getMessage());
                }
            }
        }
        return false;
    }

    // --- Free memory block --------------------------------------
    public void deallocate(Process p) {
        for (int i = p.getMemoryLow(); i <= p.getMemoryHigh(); i++) {
            memory[i]   = null;
            occupied[i] = false;
        }
    }

    // --- Variable access ----------------------------------------
    public void setVariable(Process p, String var, String value) {
        String prefix = "var:" + var + "=";
        for (int i = p.getMemoryLow(); i <= p.getMemoryHigh(); i++) {
            if (memory[i] == null) continue;
            if (memory[i].startsWith(prefix) || memory[i].equals("var:null")) {
                memory[i] = prefix + value;
                return;
            }
        }
    }

    public String getVariable(Process p, String var) {
        String prefix = "var:" + var + "=";
        for (int i = p.getMemoryLow(); i <= p.getMemoryHigh(); i++) {
            if (memory[i] != null && memory[i].startsWith(prefix)) {
                return memory[i].substring(prefix.length());
            }
        }
        return null;
    }

    // --- PCB update ---------------------------------------------
    public void updatePCB(Process p) {
        for (int i = p.getMemoryLow(); i <= p.getMemoryHigh(); i++) {
            if (memory[i] == null) continue;
            if (memory[i].startsWith("pc:"))    memory[i] = "pc:"    + p.getProgramCounter();
            if (memory[i].startsWith("state:")) memory[i] = "state:" + p.getState();
        }
    }

    // --- Free block finder --------------------------------------
    private int findFreeBlock(int size) {
        int count = 0;
        for (int i = 0; i < MEMORY_SIZE; i++) {
            count = occupied[i] ? 0 : count + 1;
            if (count == size) return i - size + 1;
        }
        return -1;
    }

    // --- Console print ------------------------------------------
    public void printMemory() {
        System.out.println("\n===== MEMORY =====");
        for (int i = 0; i < MEMORY_SIZE; i++)
            System.out.printf("%2d: %s%n", i, memory[i] == null ? "(free)" : memory[i]);
        System.out.println("==================");
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < MEMORY_SIZE; i++) {
            sb.append(String.format("%2d: ", i));
            sb.append(memory[i] == null ? "(free)" : memory[i]);
            sb.append("\n");
        }
        return sb.toString();
    }

    // --- Raw access for GUI cell coloring -----------------------
    public String  getWord(int index)    { return memory[index];   }
    public boolean isOccupied(int index) { return occupied[index]; }

    // --- Disk file header reader (for GUI disk panel) -----------
    public Map<String, String> readDiskFileHeader(int pid) {
        File f = new File("disk_p" + pid + ".txt");
        if (!f.exists()) return null;
        Map<String, String> info = new LinkedHashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("PID=") || line.startsWith("PC=")
                        || line.startsWith("STATE=") || line.startsWith("SIZE=")) {
                    int eq = line.indexOf('=');
                    info.put(line.substring(0, eq), line.substring(eq + 1));
                }
            }
        } catch (IOException ignored) {}
        return info;
    }
}
