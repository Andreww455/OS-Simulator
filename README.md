# 🖥️ OS Simulator

A Java-based simulation of a real operating system. The simulator models core OS concepts including process management, memory allocation with disk swapping, mutex-based mutual exclusion, and multiple CPU scheduling algorithms — all visualized through a JavaFX GUI.

---

## 📸 Screenshot

![OS Simulator GUI](screenshots/OS%20Simulator%20GUI)
*Round Robin scheduling at T=37 — execution log, mutex states, memory view, and process queues*

---

## 📋 Table of Contents

- [Features](#features)
- [Project Structure](#project-structure)
- [Architecture Overview](#architecture-overview)
- [Scheduling Algorithms](#scheduling-algorithms)
- [Memory Management](#memory-management)
- [Mutual Exclusion (Mutexes)](#mutual-exclusion-mutexes)
- [System Calls](#system-calls)
- [Sample Programs](#sample-programs)
- [How to Run](#how-to-run)
- [GUI](#gui)

---

## ✨ Features

- **Process lifecycle management** — NEW → READY → RUNNING → BLOCKED → FINISHED
- **Fixed-size memory (40 words)** with PCB, instructions, and variable storage per process
- **Disk swapping** — processes are swapped to/from disk when memory is full
- **Three scheduling algorithms** — HRRN, Round Robin, and MLFQ
- **Three mutexes** — `userInput`, `userOutput`, `file` — enforcing mutual exclusion
- **JavaFX GUI** — real-time visualization of queues, memory, process states, and clock cycles
- **Step-through execution** — pause, step, or run continuously

---

## 📁 Project Structure

```
OS-Simulator/
├── src/
│   ├── main/
│   │   └── Main.java                  # Entry point (launches JavaFX app)
│   ├── gui/
│   │   └── OSSimulatorGUI.java        # JavaFX GUI — full simulation interface
│   ├── process/
│   │   ├── Process.java               # PCB + process state + scheduling metadata
│   │   └── ProcessState.java          # Enum: NEW, READY, RUNNING, BLOCKED, FINISHED
│   ├── memory/
│   │   └── Memory.java                # 40-word memory, allocation, disk swap
│   ├── scheduler/
│   │   ├── Scheduler.java             # Abstract base scheduler
│   │   ├── HRRNScheduler.java         # Highest Response Ratio Next (non-preemptive)
│   │   ├── RoundRobinScheduler.java   # Round Robin (2 instructions per quantum)
│   │   └── MLFQScheduler.java         # Multi-Level Feedback Queue (4 levels)
│   ├── interpreter/
│   │   └── Interpreter.java           # Executes program instructions one by one
│   ├── mutex/
│   │   ├── Mutex.java                 # Single mutex with blocked queue
│   │   └── MutexManager.java          # Manages all 3 mutexes
│   └── systemcalls/
│       └── SystemCalls.java           # OS services: file I/O, screen, user input
├── programs/
│   ├── Program1.txt                   # Print numbers between two user-input values
│   ├── Program2.txt                   # Write user-input data to a file
│   └── Program3.txt                   # Read and print contents of a file
├── screenshots/
│   └── gui.png
├── .gitignore
└── README.md
```

---

## 🏗️ Architecture Overview

```
Main
 └── OSSimulatorGUI (JavaFX)
      └── Scheduler  (HRRN / RR / MLFQ)
           ├── Interpreter          ← executes one instruction per clock cycle
           │    ├── SystemCalls     ← file I/O, screen output, user input
           │    └── MutexManager    ← semWait / semSignal
           │         └── Mutex ×3  ← userInput, userOutput, file
           ├── Memory               ← 40-word RAM + disk swap
           └── Process ×N           ← PCB, instructions, state
```

Each clock cycle:
1. The scheduler picks the next process from the Ready Queue.
2. The interpreter executes one instruction.
3. Memory is printed/displayed.
4. Waiting times are updated, new arrivals are checked, blocked processes are unblocked if their mutex is released.

---

## ⏱️ Scheduling Algorithms

### 1. Highest Response Ratio Next (HRRN) — Non-preemptive
Selects the process with the highest **Response Ratio**:

```
Response Ratio = (Waiting Time + Burst Time) / Burst Time
```

A process runs to completion (or until it blocks) before the scheduler re-evaluates.

### 2. Round Robin (RR) — Preemptive
Each process executes **2 instructions per time slice**. If it doesn't finish, it goes to the back of the Ready Queue.

### 3. Multi-Level Feedback Queue (MLFQ) — 4 Levels
- Processes start in the highest-priority queue (Q0, quantum = 1).
- Quantum per level: `2^i` where `i` is the queue index (0–3).
- If a process exhausts its quantum, it moves down one level.
- The scheduler always picks from the highest non-empty queue.
- The lowest-priority queue (Q3) uses Round Robin.

> **Default Process Arrival Order:**
> - Process 1 → time 0
> - Process 2 → time 1
> - Process 3 → time 4

---

## 🧠 Memory Management

- **Total size:** 40 memory words
- **Per process:** instructions + 3 variable slots + PCB (ProcessID, State, PC, Memory Boundaries)
- When a new process arrives and memory is full, an existing process is **swapped to disk**.
- On the swapped process's next scheduled turn, it is **swapped back into memory** before execution resumes.
- Each process can only access its own allocated memory block.

---

## 🔒 Mutual Exclusion (Mutexes)

Three mutexes protect shared resources:

| Resource      | Mutex Name   | Used when...                                |
|---------------|--------------|---------------------------------------------|
| Screen output | `userOutput` | Any process calls `print` or `printFromTo`  |
| User input    | `userInput`  | Any process calls `assign x input`          |
| File access   | `file`       | Any process calls `readFile` or `writeFile` |

- `semWait <resource>` — acquires the mutex; if unavailable, the process is **BLOCKED** and added to that mutex's blocked queue.
- `semSignal <resource>` — releases the mutex; the first process in the blocked queue is unblocked and moved to the Ready Queue.

---

## 📞 System Calls

| Call          | Syntax               | Description                                                         |
|---------------|----------------------|---------------------------------------------------------------------|
| `assign`      | `assign x y`         | Assign value `y` to variable `x`. If `y = input`, prompt the user. |
| `print`       | `print x`            | Print value of variable `x` to screen.                             |
| `printFromTo` | `printFromTo x y`    | Print all integers from `x` to `y` on screen.                      |
| `writeFile`   | `writeFile x y`      | Write value of `y` to file named `x`.                              |
| `readFile`    | `readFile x`         | Read contents of file `x` into memory.                             |
| `semWait`     | `semWait resource`   | Acquire mutex for `resource`.                                       |
| `semSignal`   | `semSignal resource` | Release mutex for `resource`.                                       |

---

## 📄 Sample Programs

**Program 1** — Print numbers between two user-given values:
```
semWait userInput
assign x input
assign y input
semSignal userInput
semWait userOutput
printFromTo x y
semSignal userOutput
```

**Program 2** — Write user input to a file:
```
semWait userInput
assign a input
assign b input
semSignal userInput
semWait file
writeFile a b
semSignal file
```

**Program 3** — Read a file and print its contents:
```
semWait userInput
assign a input
semSignal userInput
semWait file
assign b readFile a
semSignal file
semWait userOutput
print b
semSignal userOutput
```

---

## ▶️ How to Run

### Prerequisites
- Java 17+ with JavaFX (bundled, or add JavaFX SDK to module path)
- Eclipse / IntelliJ IDEA, **or** compile manually

### Option A — Eclipse
1. Import: `File → Import → Existing Projects into Workspace`
2. Make sure JavaFX is on the build path.
3. Run `main.Main` as a Java application.

### Option B — Command Line
```bash
# Compile (adjust javafx path as needed)
javac --module-path /path/to/javafx-sdk/lib \
      --add-modules javafx.controls,javafx.fxml \
      -d bin \
      $(find src -name "*.java")

# Run
java --module-path /path/to/javafx-sdk/lib \
     --add-modules javafx.controls,javafx.fxml \
     -cp bin main.Main
```

---

## 🖼️ GUI

The JavaFX GUI provides:

- **Ready Queue & Blocked Queue** — live process lists updated every clock cycle
- **Running process indicator** — top-right shows the currently executing process
- **Memory view** — all 40 words displayed each clock cycle
- **Mutex panel** — live status (`FREE` / `LOCKED`) for `userInput`, `userOutput`, and `file`
- **Execution log** — color-coded scrollable log of every event (`[EXEC]`, `[FINISH]`, `[PREEMPT]`, `[ERROR]`, etc.)
- **Clock display** — current simulation time shown prominently at the top
- **Algorithm selector** — choose HRRN, Round Robin, or MLFQ at startup
- **Controls** — Start, Step, Pause, Reset, Clear Log, and adjustable speed slider
