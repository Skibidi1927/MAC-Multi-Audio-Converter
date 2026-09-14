import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;

public class ProcessWorker extends SwingWorker<Void, Integer> {
    private MainUI ui;
    private List<File> files;
    private String targetFormat;
    private String bitrate;
    private double volume;
    private boolean normalize;
    private boolean trimSilence;
    private int metaMode; 
    private String[][] customMetaMap;
    private boolean overwrite;
    private Set<String> skippedFiles;
    private File outputDir;

    public ProcessWorker(MainUI ui, List<File> files, String targetFormat, String bitrate, double volume, 
                         boolean normalize, boolean trimSilence, int metaMode, String[][] customMetaMap,
                         boolean overwrite, Set<String> skippedFiles, File outputDir) {
        this.ui = ui;
        this.files = files;
        this.targetFormat = targetFormat;
        this.bitrate = bitrate;
        this.volume = volume;
        this.normalize = normalize;
        this.trimSilence = trimSilence;
        this.metaMode = metaMode;
        this.customMetaMap = customMetaMap;
        this.overwrite = overwrite;
        this.skippedFiles = skippedFiles;
        this.outputDir = outputDir;
    }

    @Override
    protected Void doInBackground() throws Exception {
        int cores = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(cores);
        AtomicInteger progress = new AtomicInteger(0);
        AtomicInteger fileCounter = new AtomicInteger(1); 

        for (File f : files) {
            final int currentFileIndex = fileCounter.getAndIncrement();
            
            executor.submit(() -> {
                FFmpegEngine.processAdvanced(f, targetFormat, bitrate, volume, 
                                             normalize, trimSilence, metaMode, customMetaMap, 
                                             overwrite, outputDir, currentFileIndex);
                int current = progress.incrementAndGet();
                int percent = (int) ((current / (double) files.size()) * 100);
                publish(percent);
            });
        }

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.HOURS);
        return null;
    }

    @Override
    protected void process(List<Integer> chunks) {
        int latest = chunks.get(chunks.size() - 1);
        ui.progressBar.setValue(latest);
        ui.statusLabel.setText("Processing with Multi-Core... " + latest + "%");
    }

    @Override
    protected void done() {
        ui.startButton.setEnabled(true);
        ui.progressBar.setValue(100);
        ui.statusLabel.setText("Completed. Processed " + files.size() + " files.");
        JOptionPane.showMessageDialog(ui, "Batch Processing Complete!", "Done", JOptionPane.INFORMATION_MESSAGE);
    }
}