package gui;

import interpreter.Interpreter;
import memory.Memory;
import mutex.Mutex;
import mutex.MutexManager;
import process.Process;
import process.ProcessState;
import scheduler.*;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class OSSimulatorGUI extends Application {

    // -- Colour palette -------------------------------------------
    private static final String C_BG_DARK   = "#12141c";
    private static final String C_BG_PANEL  = "#1c1f2a";
    private static final String C_BG_CARD   = "#242837";
    private static final String C_BG_HEADER = "#161924";
    private static final String C_BLUE      = "#5282ff";
    private static final String C_CYAN      = "#40c8dc";
    private static final String C_READY     = "#40be78";
    private static final String C_RUNNING   = "#ffbe32";
    private static final String C_BLOCKED   = "#ff6450";
    private static final String C_FINISHED  = "#6e7896";
    private static final String C_NEW       = "#8c78dc";
    private static final String C_DISK      = "#c89b32";
    private static final String C_MUTEX     = "#c896ff";
    private static final String C_TXT_PRI   = "#dce1f0";
    private static final String C_TXT_SEC   = "#828ca5";
    private static final String C_TXT_MUT   = "#4b556e";
    private static final String C_BORDER    = "#32374b";

    // -- Config (set by startup dialog) ---------------------------
    private String schedulerName;
    private int    cfgTimeSlice  = 2;
    private int    cfgArrival1   = 0;
    private int    cfgArrival2   = 1;
    private int    cfgArrival3   = 4;

    // -- Runtime state --------------------------------------------
    private Scheduler    scheduler;
    private MutexManager mutexManager;
    private Timeline     autoTimeline;
    private int          autoSpeedMs = 800;

    // -- UI nodes -------------------------------------------------
    private Label      clockLabel;
    private Label      runningLabel;
    private Label      instrLabel;
    private Label      statusLabel;
    private VBox       readyCard;
    private VBox       blockedCard;
    private VBox       diskCard;
    private VBox       mutexCard;
    private GridPane   memoryGrid;
    private Label      memoryTitleLabel;
    private TextFlow   logFlow;
    private ScrollPane logScroll;
    private Button     btnStart;
    private Button     btnPause;
    private Button     btnStep;
    private Label      sliceValueLabel;
    private int        liveTimeSlice;

    // =============================================================
    @Override
    public void start(Stage stage) throws Exception {

        // Step 1: configuration dialog (algo + arrivals + time slice)
        if (!showConfigDialog()) {
            stage.close();
            return;
        }
        liveTimeSlice = cfgTimeSlice;

        // Step 2: build simulation components
        Memory       memory      = new Memory();
        MutexManager mm          = new MutexManager();
        Interpreter  interpreter = new Interpreter(memory, mm);
        this.mutexManager = mm;

        switch (schedulerName) {
            case "HRRN": scheduler = new HRRNScheduler(interpreter, memory); break;
            case "MLFQ": scheduler = new MLFQScheduler(interpreter, memory); break;
            default:
                RoundRobinScheduler rr = new RoundRobinScheduler(interpreter, memory, cfgTimeSlice);
                scheduler = rr;
        }

        scheduler.addProcess(new Process(1, load("Program 1.txt"), cfgArrival1, 0, 0));
        scheduler.addProcess(new Process(2, load("Program_2.txt"), cfgArrival2, 0, 0));
        scheduler.addProcess(new Process(3, load("Program_3.txt"), cfgArrival3, 0, 0));

        // Step 3: build scene
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color:" + C_BG_DARK + ";");
        root.setTop(buildHeader());
        root.setCenter(buildCenter());
        root.setBottom(buildControls());

        Scene scene = new Scene(root, 1350, 860);
        stage.setScene(scene);
        stage.setTitle("OS Simulator -- " + schedulerName
                + "  |  P1@" + cfgArrival1
                + "  P2@" + cfgArrival2
                + "  P3@" + cfgArrival3
                + "  slice=" + cfgTimeSlice);
        stage.setMinWidth(1100);
        stage.setMinHeight(720);
        stage.show();

        scheduler.setGUI(this);

        logColored("OS Simulator  |  Algorithm: " + schedulerName, C_CYAN);
        logColored("Memory: " + Memory.MEMORY_SIZE + " words  |  "
                + "P1 arrives T=" + cfgArrival1
                + "  P2 arrives T=" + cfgArrival2
                + "  P3 arrives T=" + cfgArrival3
                + "  TimeSlice=" + cfgTimeSlice, C_CYAN);
        logColored("----------------------------------------", C_TXT_MUT);
    }

    // =============================================================
    //  HEADER
    // =============================================================
    private BorderPane buildHeader() {
        BorderPane h = new BorderPane();
        h.setStyle(
            "-fx-background-color:" + C_BG_HEADER + ";" +
            "-fx-border-color: transparent transparent " + C_BORDER + " transparent;" +
            "-fx-border-width:0 0 1 0; -fx-padding:8 18 8 18;"
        );

        // Left: title + badge
        Label title = label("OS Simulator", C_CYAN, 14, true);
        Label badge = label("  " + schedulerName + "  ", C_BLUE, 11, true);
        badge.setStyle(badge.getStyle()
            + "-fx-background-color:" + blend(C_BLUE, C_BG_HEADER, 0.15) + ";"
            + "-fx-border-color:" + darken(C_BLUE) + ";"
            + "-fx-border-width:1; -fx-border-radius:3; -fx-background-radius:3;");
        HBox left = hbox(10, Pos.CENTER_LEFT, title, badge);

        // Center: clock (large)
        clockLabel = label("T = 0", C_TXT_PRI, 26, true);
        clockLabel.setMaxWidth(Double.MAX_VALUE);
        clockLabel.setAlignment(Pos.CENTER);

        // Right: currently running process + instruction
        Label runLbl = label("RUNNING:", C_TXT_SEC, 10, true);
        runningLabel = label("Idle", C_RUNNING, 12, true);
        instrLabel   = label("", C_TXT_SEC, 10, false);
        instrLabel.setStyle(instrLabel.getStyle() + "-fx-font-family:'Courier New';");
        VBox rightBox = new VBox(2, hbox(6, Pos.CENTER_LEFT, runLbl, runningLabel), instrLabel);
        rightBox.setAlignment(Pos.CENTER_RIGHT);

        h.setLeft(left);
        h.setCenter(clockLabel);
        h.setRight(rightBox);
        BorderPane.setAlignment(left,       Pos.CENTER_LEFT);
        BorderPane.setAlignment(clockLabel, Pos.CENTER);
        BorderPane.setAlignment(rightBox,   Pos.CENTER_RIGHT);
        return h;
    }

    // =============================================================
    //  CENTER SPLIT
    // =============================================================
    private SplitPane buildCenter() {
        SplitPane vSplit = new SplitPane();
        vSplit.setOrientation(Orientation.VERTICAL);
        vSplit.getItems().addAll(buildQueuesSection(), buildMemoryPanel());
        vSplit.setDividerPositions(0.50);
        vSplit.setStyle("-fx-background-color:" + C_BG_DARK + ";");

        SplitPane hSplit = new SplitPane();
        hSplit.getItems().addAll(vSplit, buildLogPanel());
        hSplit.setDividerPositions(0.58);
        hSplit.setStyle("-fx-background-color:" + C_BG_DARK + "; -fx-padding:4 8 4 8;");
        return hSplit;
    }

    // =============================================================
    //  QUEUES + DISK + MUTEX + LEGEND
    // =============================================================
    private VBox buildQueuesSection() {
        // Row 1: Ready + Blocked
        readyCard   = queueCard("READY",   C_READY);
        blockedCard = queueCard("BLOCKED", C_BLOCKED);
        HBox.setHgrow(readyCard,   Priority.ALWAYS);
        HBox.setHgrow(blockedCard, Priority.ALWAYS);
        readyCard.setMaxWidth(Double.MAX_VALUE);
        blockedCard.setMaxWidth(Double.MAX_VALUE);
        HBox topRow = new HBox(8, readyCard, blockedCard);
        topRow.setFillHeight(true);
        VBox.setVgrow(topRow, Priority.ALWAYS);

        // Row 2: Disk panel
        diskCard = buildDiskCard();

        // Row 3: Mutex + Legend side-by-side
        mutexCard = buildMutexCard();
        VBox legendCard = buildLegendCard();
        HBox.setHgrow(mutexCard,  Priority.ALWAYS);
        HBox.setHgrow(legendCard, Priority.ALWAYS);
        mutexCard.setMaxWidth(Double.MAX_VALUE);
        legendCard.setMaxWidth(Double.MAX_VALUE);
        HBox bottomRow = new HBox(8, mutexCard, legendCard);

        VBox section = new VBox(6, topRow, diskCard, bottomRow);
        section.setStyle("-fx-background-color:" + C_BG_DARK + "; -fx-padding:6 0 4 0;");
        section.setFillWidth(true);
        return section;
    }

    private VBox queueCard(String title, String accent) {
        VBox card = new VBox(4);
        card.setStyle(cardStyle(accent));
        card.setMaxWidth(Double.MAX_VALUE);
        card.getChildren().add(label(title, accent, 11, true));
        return card;
    }

    // =============================================================
    //  STATE LEGEND  (always visible, clarifies color coding)
    // =============================================================
    private VBox buildLegendCard() {
        VBox card = new VBox(4);
        card.setStyle(cardStyle(C_TXT_SEC));
        card.setMaxWidth(Double.MAX_VALUE);
        card.getChildren().add(label("STATE LEGEND", C_TXT_SEC, 11, true));

        String[][] states = {
            { "NEW",      C_NEW     },
            { "READY",    C_READY   },
            { "RUNNING",  C_RUNNING },
            { "BLOCKED",  C_BLOCKED },
            { "FINISHED", C_FINISHED},
            { "ON DISK",  C_DISK    }
        };

        FlowPane fp = new FlowPane(8, 4);
        for (String[] s : states) {
            Label dot = new Label("  " + s[0] + "  ");
            dot.setStyle(
                "-fx-text-fill:" + s[1] + "; -fx-font-size:10px; -fx-font-weight:bold;" +
                "-fx-background-color:" + blend(s[1], C_BG_CARD, 0.15) + ";" +
                "-fx-border-color:" + darken(s[1]) + ";" +
                "-fx-border-width:1; -fx-border-radius:3; -fx-background-radius:3;" +
                "-fx-padding:2 4 2 4;"
            );
            fp.getChildren().add(dot);
        }
        card.getChildren().add(fp);
        return card;
    }

    // =============================================================
    //  DISK CARD
    // =============================================================
    private VBox buildDiskCard() {
        VBox card = new VBox(4);
        card.setStyle(cardStyle(C_DISK));
        card.setMaxWidth(Double.MAX_VALUE);
        Label hdr  = label("DISK STORAGE", C_DISK, 11, true);
        Label hint = label("  (swap files on disk)", C_TXT_MUT, 10, false);
        HBox header = new HBox(6, hdr, hint);
        header.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().add(header);
        card.getChildren().add(muted("(empty)"));
        return card;
    }

    private void refreshDiskPanel() {
        while (diskCard.getChildren().size() > 1) diskCard.getChildren().remove(1);
        boolean any = false;
        for (Process p : scheduler.getAllProcesses()) {
            if (p.isOnDisk()) { diskCard.getChildren().add(buildDiskRow(p)); any = true; }
        }
        if (!any) diskCard.getChildren().add(muted("(empty)"));
    }

    private VBox buildDiskRow(Process p) {
        VBox row = new VBox(2);
        row.setStyle(
            "-fx-background-color:" + blend(C_DISK, C_BG_CARD, 0.10) + ";" +
            "-fx-border-color:" + darken(C_DISK) + ";" +
            "-fx-border-width:1; -fx-border-radius:3; -fx-background-radius:3;" +
            "-fx-padding:4 6 4 6;"
        );

        String filename = "disk_p" + p.getProcessID() + ".txt";
        Map<String, String> info = scheduler.getMemory().readDiskFileHeader(p.getProcessID());
        String pcStr    = info != null && info.containsKey("PC")    ? info.get("PC")    : String.valueOf(p.getProgramCounter());
        String stateStr = info != null && info.containsKey("STATE") ? info.get("STATE") : p.getState().toString();
        String sizeStr  = info != null && info.containsKey("SIZE")  ? info.get("SIZE") + "w" : "?w";

        Label pidBadge = chip("P" + p.getProcessID(), C_DISK);
        Label fileLbl  = mono(filename, C_TXT_SEC, 10, false);
        Label infoLbl  = mono("PC=" + pcStr + "  " + stateStr + "  " + sizeStr, C_TXT_PRI, 10, false);
        HBox  hline    = new HBox(8, pidBadge, fileLbl, infoLbl);
        hline.setAlignment(Pos.CENTER_LEFT);
        row.getChildren().add(hline);

        // Show memory words from disk file
        if (info != null) {
            File f = new File(filename);
            if (f.exists()) {
                try {
                    List<String> lines = Files.readAllLines(f.toPath());
                    List<String> memLines = lines.stream()
                            .filter(l -> l.matches("\\d+=.*"))
                            .collect(Collectors.toList());
                    if (!memLines.isEmpty()) {
                        FlowPane words = new FlowPane(4, 2);
                        words.setStyle("-fx-padding:2 0 0 0;");
                        for (String ml : memLines) {
                            int eq = ml.indexOf('=');
                            words.getChildren().add(diskWordLabel(ml.substring(0, eq), ml.substring(eq + 1)));
                        }
                        row.getChildren().add(words);
                    }
                } catch (Exception ignored) {}
            }
        }
        return row;
    }

    private Label diskWordLabel(String idx, String word) {
        String fg, bg;
        String display;
        if (word == null || word.equals("null")) {
            fg = C_TXT_MUT; bg = C_BG_PANEL; display = idx + ":(free)";
        } else if (word.startsWith("ins:")) {
            fg = C_BLUE;    bg = "#1e3248";   display = idx + ":" + word.substring(4);
        } else if (word.startsWith("var:")) {
            String v = word.substring(4);
            fg = C_READY;   bg = "#1e3c28";   display = idx + ":" + (v.equals("null") ? "var:-" : v);
        } else if (word.startsWith("pid:") || word.startsWith("state:") ||
                   word.startsWith("pc:")  || word.startsWith("low:")   ||
                   word.startsWith("high:")) {
            fg = C_NEW;     bg = "#322846";   display = idx + ":" + word;
        } else {
            fg = C_TXT_PRI; bg = C_BG_CARD;   display = idx + ":" + word;
        }
        Label l = new Label(trunc(display, 18));
        l.setTooltip(new Tooltip(idx + "=" + word));
        l.setStyle(
            "-fx-text-fill:" + fg + "; -fx-font-family:'Courier New'; -fx-font-size:9px;" +
            "-fx-background-color:" + bg + "; -fx-padding:1 3 1 3;" +
            "-fx-border-color:" + darken(fg) + "; -fx-border-width:1;" +
            "-fx-border-radius:2; -fx-background-radius:2;"
        );
        return l;
    }

    // =============================================================
    //  MUTEX CARD
    // =============================================================
    private VBox buildMutexCard() {
        VBox card = new VBox(5);
        card.setStyle(cardStyle(C_MUTEX));
        card.setMaxWidth(Double.MAX_VALUE);
        card.getChildren().add(label("MUTEXES", C_MUTEX, 11, true));
        return card;
    }

    private void refreshMutexPanel() {
        while (mutexCard.getChildren().size() > 1) mutexCard.getChildren().remove(1);
        mutexManager.getMutexMap().forEach((name, mutex) ->
            mutexCard.getChildren().add(mutexRow(name, mutex)));
    }

    private HBox mutexRow(String name, Mutex mutex) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-padding:2 0 2 0;");
        Label resLbl = mono(name, C_MUTEX, 10, true);
        resLbl.setMinWidth(90);
        row.getChildren().add(resLbl);
        if (!mutex.isLocked()) {
            row.getChildren().add(chip("FREE", C_READY));
        } else {
            Process owner = mutex.getOwner();
            if (owner != null) row.getChildren().add(chip("P" + owner.getProcessID() + " owns", C_RUNNING));
            for (Process w : mutex.getWaitingQueue())
                row.getChildren().add(chip("P" + w.getProcessID() + " waits", C_BLOCKED));
        }
        return row;
    }

    // =============================================================
    //  MEMORY PANEL
    // =============================================================
    private ScrollPane buildMemoryPanel() {
        memoryTitleLabel = label("  MEMORY  (" + Memory.MEMORY_SIZE + " words)  —  T = 0", C_CYAN, 11, true);
        memoryTitleLabel.setMaxWidth(Double.MAX_VALUE);
        memoryTitleLabel.setStyle(memoryTitleLabel.getStyle()
            + "-fx-background-color:" + C_BG_HEADER + "; -fx-padding:5 8 5 8;");

        // Legend for memory word types
        Label memLegend = label(
            "  [BLUE]=instruction   [GREEN]=variable   [PURPLE]=PCB field   [grey]=free",
            C_TXT_MUT, 10, false);
        memLegend.setMaxWidth(Double.MAX_VALUE);
        memLegend.setStyle(memLegend.getStyle()
            + "-fx-background-color:" + C_BG_HEADER + "; -fx-padding:2 8 3 8;");

        memoryGrid = new GridPane();
        memoryGrid.setHgap(2);
        memoryGrid.setVgap(2);
        memoryGrid.setStyle("-fx-background-color:" + C_BG_PANEL + "; -fx-padding:4;");
        for (int i = 0; i < 4; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(25);
            cc.setHgrow(Priority.ALWAYS);
            memoryGrid.getColumnConstraints().add(cc);
        }

        VBox wrap = new VBox(0, memoryTitleLabel, memLegend, memoryGrid);
        wrap.setStyle("-fx-background-color:" + C_BG_PANEL + ";");
        VBox.setVgrow(memoryGrid, Priority.ALWAYS);

        ScrollPane sp = new ScrollPane(wrap);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setStyle("-fx-background:" + C_BG_PANEL
            + "; -fx-border-color:" + C_BORDER + "; -fx-border-width:1;");
        return sp;
    }

    private void refreshMemory() {
        // Update title to show the clock cycle this snapshot belongs to
        memoryTitleLabel.setText("  MEMORY  (" + Memory.MEMORY_SIZE + " words)"
            + "  \u2014  T = " + scheduler.getCurrentTime());
        memoryGrid.getChildren().clear();
        Memory mem = scheduler.getMemory();
        for (int i = 0; i < Memory.MEMORY_SIZE; i++)
            memoryGrid.add(memCell(i, mem.getWord(i), mem.isOccupied(i)), i % 4, i / 4);
    }

    private HBox memCell(int index, String word, boolean occ) {
        String bg, fg, display;
        if (!occ || word == null) {
            bg = C_BG_PANEL; fg = C_TXT_MUT; display = "-";
        } else if (word.startsWith("ins:")) {
            bg = "#1e3248"; fg = C_BLUE;    display = word.substring(4);
        } else if (word.startsWith("var:")) {
            String v = word.substring(4);
            bg = "#1e3c28"; fg = C_READY;
            display = v.equals("null") ? "var: -" : "var: " + v;
        } else if (word.startsWith("pid:")  || word.startsWith("state:") ||
                   word.startsWith("pc:")   || word.startsWith("low:")   ||
                   word.startsWith("high:")) {
            bg = "#322846"; fg = C_NEW;     display = word;
        } else {
            bg = C_BG_CARD; fg = C_TXT_PRI; display = word;
        }
        Label idx     = mono(String.format("%2d", index), C_TXT_MUT, 9, false);
        idx.setMinWidth(18);
        Label content = mono(trunc(display, 14), fg, 10, false);
        content.setTooltip(new Tooltip("addr " + index + ": " + (word != null ? word : "(free)")));
        HBox.setHgrow(content, Priority.ALWAYS);
        HBox cell = new HBox(4, idx, content);
        cell.setMaxWidth(Double.MAX_VALUE);
        cell.setStyle("-fx-background-color:" + bg + "; -fx-padding:3 4 3 4;");
        return cell;
    }

    // =============================================================
    //  LOG PANEL
    // =============================================================
    private VBox buildLogPanel() {
        Label title = label("  EXECUTION LOG", C_CYAN, 11, true);
        title.setMaxWidth(Double.MAX_VALUE);
        title.setStyle(title.getStyle()
            + "-fx-background-color:" + C_BG_HEADER + "; -fx-padding:5 8 5 8;");

        // Log key
        Label logKey = label(
            "  [EXEC]=instruction  [SCREEN]=output  [FILE]=disk I/O  [INPUT]=user  [MEM]=var write  [SWAP]=disk swap",
            C_TXT_MUT, 9, false);
        logKey.setMaxWidth(Double.MAX_VALUE);
        logKey.setStyle(logKey.getStyle()
            + "-fx-background-color:" + C_BG_HEADER + "; -fx-padding:2 8 3 8;");

        logFlow = new TextFlow();
        logFlow.setStyle("-fx-background-color:" + C_BG_PANEL + "; -fx-padding:4;");
        logFlow.setLineSpacing(1.5);

        logScroll = new ScrollPane(logFlow);
        logScroll.setFitToWidth(true);
        logScroll.setStyle("-fx-background:" + C_BG_PANEL
            + "; -fx-border-color:" + C_BORDER + "; -fx-border-width:1;");
        VBox.setVgrow(logScroll, Priority.ALWAYS);

        statusLabel = label(" Ready.", C_TXT_SEC, 12, false);
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        statusLabel.setStyle(statusLabel.getStyle()
            + "-fx-background-color:" + C_BG_HEADER + "; -fx-padding:4 10 4 10;");

        VBox panel = new VBox(0, title, logKey, logScroll, statusLabel);
        panel.setStyle("-fx-background-color:" + C_BG_PANEL + "; -fx-padding:4 0 0 0;");
        VBox.setVgrow(logScroll, Priority.ALWAYS);
        return panel;
    }

    // =============================================================
    //  CONTROLS BAR
    // =============================================================
    private BorderPane buildControls() {
        BorderPane bar = new BorderPane();
        bar.setStyle(
            "-fx-background-color:" + C_BG_HEADER + ";" +
            "-fx-border-color:" + C_BORDER + " transparent transparent transparent;" +
            "-fx-border-width:1 0 0 0; -fx-padding:8 16 8 16;"
        );

        btnStart = btn(">  Start",    "#3ca050");
        btnStep  = btn(">>  Step",    C_BLUE);
        btnPause = btn("||  Pause",   "#b47828");
        Button btnReset = btn("R  Reset",     "#783c3c");
        Button btnClear = btn("X  Clear Log", C_BG_CARD);

        Separator sep = new Separator(Orientation.VERTICAL);
        sep.setStyle("-fx-padding:0 8 0 8;");

        // -- Time slice live control (visible for RR only) ---------
        Label sliceLbl = label("Time Slice:", C_TXT_SEC, 12, false);
        Button sliceMinus = btn("-", C_BG_CARD);
        Button slicePlus  = btn("+", C_BG_CARD);
        sliceMinus.setPrefWidth(32); slicePlus.setPrefWidth(32);
        sliceValueLabel = label(String.valueOf(cfgTimeSlice), C_RUNNING, 13, true);
        sliceValueLabel.setMinWidth(24);
        sliceValueLabel.setAlignment(Pos.CENTER);
        sliceMinus.setOnAction(e -> changeTimeSlice(-1));
        slicePlus.setOnAction(e  -> changeTimeSlice(+1));
        HBox sliceBox = new HBox(4, sliceLbl, sliceMinus, sliceValueLabel, slicePlus);
        sliceBox.setAlignment(Pos.CENTER_LEFT);
        // Only show for schedulers that use a time slice
        boolean showSlice = schedulerName.equals("Round Robin (RR)") || schedulerName.equals("MLFQ");
        sliceBox.setVisible(showSlice);
        sliceBox.setManaged(showSlice);

        HBox left = new HBox(8, btnStart, btnStep, btnPause, sep, btnReset, btnClear, sliceBox);
        left.setAlignment(Pos.CENTER_LEFT);

        // -- Speed slider -----------------------------------------
        Label spdLbl = label("Speed:", C_TXT_SEC, 12, false);
        Slider slider = new Slider(100, 2000, autoSpeedMs);
        slider.setPrefWidth(140);
        Label spdVal = label(autoSpeedMs + " ms", C_TXT_SEC, 12, false);
        slider.valueProperty().addListener((obs, o, n) -> {
            autoSpeedMs = n.intValue();
            spdVal.setText(autoSpeedMs + " ms");
            if (autoTimeline != null) autoTimeline.stop();
            if (btnStart.isDisabled()) startAuto(); // restart with new speed
        });
        HBox right = new HBox(8, spdLbl, slider, spdVal);
        right.setAlignment(Pos.CENTER_RIGHT);

        bar.setLeft(left);
        bar.setRight(right);
        BorderPane.setAlignment(left,  Pos.CENTER_LEFT);
        BorderPane.setAlignment(right, Pos.CENTER_RIGHT);

        btnStart.setOnAction(e -> startAuto());
        btnPause.setOnAction(e -> pauseAuto());
        btnStep.setOnAction(e  -> doStep());
        btnReset.setOnAction(e -> showInfoDialog("Please restart the application to reset."));
        btnClear.setOnAction(e -> logFlow.getChildren().clear());

        return bar;
    }

    private void changeTimeSlice(int delta) {
        liveTimeSlice = Math.max(1, liveTimeSlice + delta);
        sliceValueLabel.setText(String.valueOf(liveTimeSlice));
        if (scheduler instanceof RoundRobinScheduler)
            ((RoundRobinScheduler) scheduler).setTimeSlice(liveTimeSlice);
        log("[CONFIG ] Time slice changed to " + liveTimeSlice);
    }

    // =============================================================
    //  STEP / AUTO
    // =============================================================
    private void doStep() {
        if (scheduler.isFinished()) { stopAuto(); setStatus("All processes finished."); return; }
        scheduler.step();
        refreshUI();
    }

    private void startAuto() {
        if (autoTimeline != null) autoTimeline.stop();
        autoTimeline = new Timeline(new KeyFrame(Duration.millis(autoSpeedMs), e -> {
            if (scheduler.isFinished()) { stopAuto(); setStatus("All processes finished."); }
            else doStep();
        }));
        autoTimeline.setCycleCount(Timeline.INDEFINITE);
        autoTimeline.play();
        setStatus("Running...");
        btnStart.setDisable(true);
    }

    private void pauseAuto() {
        stopAuto();
        setStatus("Paused at T=" + scheduler.getCurrentTime());
    }

    private void stopAuto() {
        if (autoTimeline != null) autoTimeline.stop();
        btnStart.setDisable(false);
    }

    // =============================================================
    //  REFRESH  (called every instruction via notifyGUI / scheduler)
    // =============================================================
    public void refreshUI() {
        // Clock
        clockLabel.setText("T = " + scheduler.getCurrentTime());

        // Running process info — never show "Idle" while a process is tracked
        Process cur = scheduler.getCurrentProcess();
        if (cur != null && cur.getState() == ProcessState.RUNNING) {
            runningLabel.setText("P" + cur.getProcessID() + "  [RUNNING]");
            runningLabel.setStyle("-fx-text-fill:" + C_RUNNING
                + "; -fx-font-weight:bold; -fx-font-size:12px;");
            // Show the instruction that just executed (saved before PC was incremented)
            String lastInstr = scheduler.getLastExecutedInstruction();
            int displayPC = Math.max(0, cur.getProgramCounter() - 1);
            instrLabel.setText("  PC=" + displayPC
                + "  |  " + (lastInstr.isEmpty() ? cur.getCurrentInstruction() : lastInstr));
        } else if (cur != null && cur.getState() == ProcessState.FINISHED) {
            runningLabel.setText("P" + cur.getProcessID() + "  [FINISHED]");
            runningLabel.setStyle("-fx-text-fill:" + C_FINISHED
                + "; -fx-font-weight:bold; -fx-font-size:12px;");
            instrLabel.setText("  Completed at T=" + scheduler.getCurrentTime());
        } else if (cur != null && cur.getState() == ProcessState.BLOCKED) {
            runningLabel.setText("P" + cur.getProcessID() + "  [BLOCKED]");
            runningLabel.setStyle("-fx-text-fill:" + C_BLOCKED
                + "; -fx-font-weight:bold; -fx-font-size:12px;");
            instrLabel.setText("  Waiting for resource");
        } else {
            runningLabel.setText("\u2014  Idle  \u2014");
            runningLabel.setStyle("-fx-text-fill:" + C_TXT_MUT
                + "; -fx-font-weight:bold; -fx-font-size:12px;");
            instrLabel.setText("");
        }

        refreshQueueCards();
        refreshDiskPanel();
        refreshMutexPanel();
        refreshMemory();
    }

    private void refreshQueueCards() {
        fillQueue(readyCard,   C_READY,   scheduler.getReadyQueue());
        fillQueue(blockedCard, C_BLOCKED, scheduler.getBlockedQueue());
    }

    private void fillQueue(VBox card, String accent, Queue<Process> queue) {
        while (card.getChildren().size() > 1) card.getChildren().remove(1);
        List<Process> visible = queue.stream()
                .filter(p -> !p.isOnDisk())
                .collect(Collectors.toList());
        if (visible.isEmpty()) {
            card.getChildren().add(muted("(empty)"));
        } else {
            for (Process p : visible) card.getChildren().add(processChip(p));
        }
    }

    private HBox processChip(Process p) {
        String sc = stateColor(p.getState(), p.isOnDisk());
        Label id   = mono("P" + p.getProcessID(), sc, 11, true);
        Label info = label(
            "PC=" + p.getProgramCounter()
            + "  Wait=" + p.getWaitingTime()
            + "  [" + p.getState() + "]"
            + (p.isOnDisk() ? "  DISK" : ""),
            C_TXT_SEC, 10, false);
        HBox chip = new HBox(6, id, info);
        chip.setMaxWidth(Double.MAX_VALUE);
        chip.setStyle(
            "-fx-background-color:" + blend(sc, C_BG_CARD, 0.12) + ";" +
            "-fx-border-color:" + darken(sc) + ";" +
            "-fx-border-width:1; -fx-border-radius:3; -fx-background-radius:3;" +
            "-fx-padding:3 6 3 6;"
        );
        return chip;
    }

    // =============================================================
    //  LOGGING
    // =============================================================
    public void log(String msg) {
        if (Platform.isFxApplicationThread()) appendLog(msg);
        else Platform.runLater(() -> appendLog(msg));
    }

    private void appendLog(String msg) {
        Text t = new Text(msg + "\n");
        t.setStyle("-fx-fill:" + logColor(msg)
            + "; -fx-font-family:'Courier New'; -fx-font-size:11px;");
        logFlow.getChildren().add(t);
        Platform.runLater(() -> logScroll.setVvalue(1.0));
    }

    private void logColored(String msg, String color) {
        Text t = new Text(msg + "\n");
        t.setStyle("-fx-fill:" + color
            + "; -fx-font-family:'Courier New'; -fx-font-size:11px;");
        logFlow.getChildren().add(t);
    }

    private String logColor(String msg) {
        if (msg.startsWith("-- Clock"))                                         return C_RUNNING;
        if (msg.startsWith("==="))                                              return "#c8a0ff";
        if (msg.startsWith("-- MLFQ"))                                         return C_BLOCKED;
        if (msg.startsWith("[PREEMPT]") || msg.startsWith("[DEMOTE ]")
                          || msg.startsWith("[STAY   ]"))                       return "#ffa550";
        if (msg.startsWith("[ARRIVAL]"))                                        return C_NEW;
        if (msg.startsWith("[FINISH ]"))                                        return C_FINISHED;
        if (msg.startsWith("[BLOCK  ]") || msg.startsWith("[READY  ]")
         || msg.startsWith("[BLOCKED QUEUE]") || msg.startsWith("[READY   QUEUE]")
         || msg.startsWith("  Q") || msg.startsWith("  Blocked"))              return C_BLOCKED;
        if (msg.startsWith("[SWAP") || msg.startsWith("[DISK")
         || msg.startsWith("[ALLOC  ]"))                                        return C_DISK;
        if (msg.startsWith("[EXEC ]"))                                          return "#b4bee0";
        if (msg.startsWith("[SCREEN]"))                                         return "#ffd764";
        if (msg.startsWith("[FILE  ]"))                                         return "#82c8ff";
        if (msg.startsWith("[MEM   ]") || msg.startsWith("[MEMORY]"))          return C_READY;
        if (msg.startsWith("[INPUT ]"))                                         return C_NEW;
        if (msg.startsWith("[CONFIG]"))                                         return C_CYAN;
        if (msg.startsWith("[ERROR ]") || msg.startsWith("[ERROR]"))           return "#ff5050";
        if (msg.startsWith("------") || msg.startsWith("OS Sim")
         || msg.startsWith("Memory:"))                                          return C_TXT_MUT;
        return C_TXT_PRI;
    }

    // =============================================================
    //  HELPERS
    // =============================================================
    private void setStatus(String msg) { statusLabel.setText(" " + msg); }

    private String stateColor(ProcessState st, boolean onDisk) {
        if (onDisk) return C_DISK;
        switch (st) {
            case READY:    return C_READY;
            case RUNNING:  return C_RUNNING;
            case BLOCKED:  return C_BLOCKED;
            case FINISHED: return C_FINISHED;
            default:       return C_NEW;
        }
    }

    private String blend(String h1, String h2, double r) {
        int[] c1 = hexToRgb(h1), c2 = hexToRgb(h2);
        return String.format("#%02x%02x%02x",
            clamp((int)(c1[0]*r + c2[0]*(1-r))),
            clamp((int)(c1[1]*r + c2[1]*(1-r))),
            clamp((int)(c1[2]*r + c2[2]*(1-r))));
    }
    private String darken(String hex)    { return blend(hex, "#000000", 0.7); }
    private int[]  hexToRgb(String h)   {
        return new int[]{ Integer.parseInt(h.substring(1,3),16),
                          Integer.parseInt(h.substring(3,5),16),
                          Integer.parseInt(h.substring(5,7),16) };
    }
    private int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    private String cardStyle(String accent) {
        return "-fx-background-color:" + C_BG_CARD + ";"
            + "-fx-border-color:" + darken(accent) + ";"
            + "-fx-border-width:1; -fx-border-radius:4; -fx-background-radius:4;"
            + "-fx-padding:8 10 8 10;";
    }

    private Label label(String text, String color, int size, boolean bold) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill:" + color + "; -fx-font-size:" + size + "px;"
            + (bold ? " -fx-font-weight:bold;" : ""));
        return l;
    }
    private Label mono(String text, String color, int size, boolean bold) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill:" + color + "; -fx-font-family:'Courier New';"
            + " -fx-font-size:" + size + "px;" + (bold ? " -fx-font-weight:bold;" : ""));
        return l;
    }
    private Label muted(String text) { return label(text, C_TXT_MUT, 11, false); }

    private Label chip(String text, String accent) {
        Label l = new Label(text);
        l.setStyle(
            "-fx-text-fill:" + accent + "; -fx-font-size:10px; -fx-font-weight:bold;"
            + "-fx-font-family:'Courier New';"
            + "-fx-background-color:" + blend(accent, C_BG_CARD, 0.15) + ";"
            + "-fx-border-color:" + darken(accent) + ";"
            + "-fx-border-width:1; -fx-border-radius:2; -fx-background-radius:2;"
            + "-fx-padding:1 5 1 5;"
        );
        return l;
    }

    private HBox hbox(int spacing, Pos align, Node... nodes) {
        HBox h = new HBox(spacing, nodes);
        h.setAlignment(align);
        return h;
    }

    private Button btn(String text, String accent) {
        String bgN  = blend(accent, C_BG_CARD, 0.30);
        String bgH  = blend(accent, C_BG_CARD, 0.55);
        String base = "-fx-text-fill:" + C_TXT_PRI + "; -fx-font-weight:bold; -fx-font-size:12px;"
            + "-fx-border-color:" + darken(accent) + ";"
            + "-fx-border-width:1; -fx-border-radius:3; -fx-background-radius:3;"
            + "-fx-padding:5 14 5 14; -fx-cursor:hand;";
        Button b = new Button(text);
        b.setStyle("-fx-background-color:" + bgN + ";" + base);
        b.setOnMouseEntered(e -> b.setStyle("-fx-background-color:" + bgH + ";" + base));
        b.setOnMouseExited(e  -> b.setStyle("-fx-background-color:" + bgN + ";" + base));
        return b;
    }

    private String trunc(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "~";
    }

    private static List<String> load(String filename) throws Exception {
        return Files.readAllLines(Paths.get(filename))
                .stream().map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    // =============================================================
    //  STARTUP CONFIGURATION DIALOG
    //  Sets: algorithm, arrival times for P1/P2/P3, time slice
    // =============================================================
    private boolean showConfigDialog() {
        final boolean[] confirmed = {false};

        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("OS Simulator - Configuration");
        dialog.setResizable(false);

        // ---- Algorithm selection --------------------------------
        Label algoTitle = label("Scheduling Algorithm", C_CYAN, 13, true);
        String[] algos   = {"Round Robin (RR)", "HRRN", "MLFQ"};
        String[] accents = {C_READY, C_BLUE, C_RUNNING};
        final String[] pickedAlgo = {"Round Robin (RR)"};

        HBox algoBox = new HBox(8);
        algoBox.setAlignment(Pos.CENTER_LEFT);
        Button[] algoBtns = new Button[3];
        for (int i = 0; i < algos.length; i++) {
            final String name = algos[i];
            final String ac   = accents[i];
            final int    idx  = i;
            Button b = new Button(name);
            b.setPrefWidth(140);
            b.setStyle(
                "-fx-background-color:" + (i == 0 ? blend(ac, C_BG_CARD, 0.55) : blend(ac, C_BG_CARD, 0.20)) + ";"
                + "-fx-text-fill:" + C_TXT_PRI + "; -fx-font-weight:bold; -fx-font-size:12px;"
                + "-fx-border-color:" + darken(ac) + ";"
                + "-fx-border-width:1; -fx-border-radius:3; -fx-background-radius:3;"
                + "-fx-padding:6 10 6 10;"
            );
            algoBtns[i] = b;
            b.setOnAction(ev -> {
                pickedAlgo[0] = name;
                // Update button highlights
                for (int j = 0; j < algos.length; j++) {
                    String a2 = accents[j];
                    algoBtns[j].setStyle(
                        "-fx-background-color:" + (j == idx ? blend(a2, C_BG_CARD, 0.55) : blend(a2, C_BG_CARD, 0.20)) + ";"
                        + "-fx-text-fill:" + C_TXT_PRI + "; -fx-font-weight:bold; -fx-font-size:12px;"
                        + "-fx-border-color:" + darken(a2) + ";"
                        + "-fx-border-width:1; -fx-border-radius:3; -fx-background-radius:3;"
                        + "-fx-padding:6 10 6 10;"
                    );
                }
            });
            algoBox.getChildren().add(b);
        }

        // ---- Arrival times --------------------------------------
        Label arrTitle = label("Process Arrival Times", C_CYAN, 13, true);
        TextField tfA1 = configField("0");
        TextField tfA2 = configField("1");
        TextField tfA3 = configField("4");

        HBox arrBox = new HBox(12,
            label("P1:", C_READY,   12, true), tfA1,
            label("P2:", C_BLUE,    12, true), tfA2,
            label("P3:", C_RUNNING, 12, true), tfA3
        );
        arrBox.setAlignment(Pos.CENTER_LEFT);

        // ---- Time slice -----------------------------------------
        Label sliceTitle = label("Time Slice (RR / MLFQ)", C_CYAN, 13, true);
        TextField tfSlice = configField("2");
        HBox sliceRow = new HBox(8,
            label("Instructions per slice:", C_TXT_SEC, 12, false), tfSlice
        );
        sliceRow.setAlignment(Pos.CENTER_LEFT);

        // ---- OK button ------------------------------------------
        Button ok = new Button("Launch Simulation");
        ok.setStyle(
            "-fx-background-color:" + blend("#3ca050", C_BG_CARD, 0.40) + ";"
            + "-fx-text-fill:white; -fx-font-weight:bold; -fx-font-size:13px;"
            + "-fx-border-color:" + darken("#3ca050") + ";"
            + "-fx-border-width:1; -fx-border-radius:3; -fx-background-radius:3;"
            + "-fx-padding:8 24 8 24;"
        );
        ok.setDefaultButton(true);
        ok.setOnAction(e -> {
            schedulerName  = pickedAlgo[0];
            cfgArrival1    = parseInt(tfA1.getText(), 0);
            cfgArrival2    = parseInt(tfA2.getText(), 1);
            cfgArrival3    = parseInt(tfA3.getText(), 4);
            cfgTimeSlice   = Math.max(1, parseInt(tfSlice.getText(), 2));
            confirmed[0]   = true;
            dialog.close();
        });
        HBox okRow = new HBox(ok);
        okRow.setAlignment(Pos.CENTER_RIGHT);

        // ---- Assemble -------------------------------------------
        VBox root = new VBox(14,
            algoTitle, algoBox,
            new Separator(),
            arrTitle, arrBox,
            new Separator(),
            sliceTitle, sliceRow,
            new Separator(),
            okRow
        );
        root.setPadding(new Insets(22));
        root.setStyle("-fx-background-color:" + C_BG_PANEL + ";");

        dialog.setScene(new Scene(root, 500, 380));
        dialog.showAndWait();
        return confirmed[0];
    }

    private TextField configField(String defVal) {
        TextField tf = new TextField(defVal);
        tf.setPrefWidth(60);
        tf.setStyle(
            "-fx-background-color:#242837; -fx-text-fill:#dce1f0;"
            + "-fx-border-color:#5282ff; -fx-border-width:1;"
            + "-fx-border-radius:3; -fx-background-radius:3;"
            + "-fx-font-size:12px;"
        );
        return tf;
    }

    private int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return fallback; }
    }

    // =============================================================
    //  INFO DIALOG
    // =============================================================
    private void showInfoDialog(String message) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Info");
        dialog.setResizable(false);
        Label msg = new Label(message);
        msg.setStyle("-fx-text-fill:" + C_TXT_PRI + "; -fx-font-size:13px;");
        msg.setWrapText(true);
        Button ok = new Button("OK");
        ok.setDefaultButton(true);
        ok.setOnAction(e -> dialog.close());
        ok.setStyle(
            "-fx-background-color:" + blend(C_BLUE, C_BG_CARD, 0.35) + ";"
            + "-fx-text-fill:" + C_TXT_PRI + "; -fx-font-weight:bold;"
            + "-fx-border-color:" + darken(C_BLUE) + ";"
            + "-fx-border-width:1; -fx-border-radius:3; -fx-background-radius:3;"
            + "-fx-padding:5 20 5 20;"
        );
        HBox btnRow = new HBox(ok);
        btnRow.setAlignment(Pos.CENTER_RIGHT);
        VBox root = new VBox(12, msg, btnRow);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color:" + C_BG_PANEL + ";");
        dialog.setScene(new Scene(root, 340, 130));
        dialog.showAndWait();
    }
}
