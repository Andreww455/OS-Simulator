package systemcalls;

import memory.Memory;
import process.Process;

import java.io.*;
import java.util.Scanner;

public class SystemCalls {

    // --- InputProvider: swappable between console and GUI -------
    public interface InputProvider {
        String getInput(String prompt);
    }

    private Memory        memory;
    private InputProvider inputProvider;

    // Default: console input
    public SystemCalls(Memory memory) {
        this.memory = memory;
        Scanner scanner = new Scanner(System.in);
        this.inputProvider = prompt -> {
            System.out.print(prompt + " ");
            return scanner.nextLine().trim();
        };
    }

    /** Called by GUI to switch to dialog-based input (FIX: prevents EDT block) */
    public void setInputProvider(InputProvider provider) {
        this.inputProvider = provider;
    }

    // --- 1. Print to screen -------------------------------------
    public void print(Process p, String varName) {
        String value = memory.getVariable(p, varName);
        System.out.println("[SCREEN] " + (value != null ? value : varName));
    }

    // --- 2. Take user input -------------------------------------
    public String input(String varName) {
        return inputProvider.getInput("Please enter a value for " + varName + ":");
    }

    // --- 3. Write file ------------------------------------------
    public void writeFile(String filename, String data) throws IOException {
        try (BufferedWriter w = new BufferedWriter(new FileWriter(filename))) {
            w.write(data);
        }
    }

    // --- 4. Read file -------------------------------------------
    public String readFile(String filename) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = r.readLine()) != null)
                sb.append(line).append("\n");
        }
        return sb.toString().trim();
    }

    // --- 5. Read from memory ------------------------------------
    public String readMemory(Process p, String var) {
        return memory.getVariable(p, var);
    }

    // --- 6. Write to memory -------------------------------------
    public void writeMemory(Process p, String var, String value) {
        memory.setVariable(p, var, value);
    }
}
