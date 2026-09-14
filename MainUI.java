import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;

import com.formdev.flatlaf.themes.FlatMacDarkLaf;

/**
 * Main GUI configuration for MAC.
 * Bootstraps the FlatLaf theme and handles all user interactions and state management.
 */
public class MainUI extends JFrame {
    
    public JTextField folderPathField;
    public JButton browseButton, selectButton, startButton, metadataButton;
    public JComboBox<String> targetFormatBox, exportFormatBox;
    public JSlider qualitySlider; 
    public JSpinner volumeSpinner;
    public JCheckBox overwriteCheck, normalizeCheck, trimSilenceCheck;
    public JProgressBar progressBar;
    public JLabel statusLabel, spectrogramLabel, statsLabel, metadataLabel, totalSizeLabel;
    public JPanel previewPanel; 
    
    public Set<String> skippedFiles = new HashSet<>(); 
    
    public int currentMetaMode = 0; 
    public String[][] currentMetaMap = new String[][] {
        {"Output Filename", "{original}"},
        {"Artist Name", ""},
        {"Track Title", ""},
        {"Album Title", ""},
        {"Track Number", "{count}"},
        {"Year", ""},
        {"Genre", ""},
        {"Comments", ""},
        {"Copyright", ""}
    };
    
    private Timer previewTimer;
    private SwingWorker<PreviewResult, Void> currentPreviewWorker;
    
    private BufferedImage originalSpectrogramImage = null;
    private BufferedImage ghostOriginal = null;
    private int averageOriginalPeakY = -1; 
    private File benchmarkFile = null;
    
    private String currentBitrate = "-- kbps";
    private int baselineBitrate = -1; 
    private String currentPeak = "-- kHz";
    private String currentSampleRate = "-- kHz";
    private String currentChannels = "--";
    
    private final int[] kbpsMap = {64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 500};

    class OriginalResult {
        BufferedImage img;
        String bitrate;
        int bitrateInt;
        String peak;
        String sampleRate;
        String channels;
    }

    class PreviewResult {
        ImageIcon icon;
        String bitrate;
        String peak;
    }

    class FileSize implements Comparable<FileSize> {
        long bytes;
        public FileSize(long bytes) { this.bytes = bytes; }
        @Override public int compareTo(FileSize o) { return Long.compare(this.bytes, o.bytes); }
        @Override public String toString() { return formatSize(this.bytes); }
    }

    public MainUI() {
        // macOS UI overrides to push content into the window frame
        getRootPane().putClientProperty("apple.awt.fullWindowContent", true);
        getRootPane().putClientProperty("apple.awt.transparentTitleBar", true);
        getRootPane().putClientProperty("TitlePane.showIcon", false);

        setTitle("MAC Multi Audio Converter");
        setSize(780, 750); 
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(15, 15));

        JPanel topPanel = new JPanel(new GridLayout(2, 1, 5, 10));
        topPanel.setBorder(BorderFactory.createEmptyBorder(25, 15, 5, 15)); 
        
        JPanel folderPanel = new JPanel(new BorderLayout(5, 5));
        folderPanel.add(new JLabel("Folder: "), BorderLayout.WEST);
        folderPathField = new JTextField();
        folderPanel.add(folderPathField, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 5, 0));
        browseButton = new JButton("Browse...");
        selectButton = new JButton("Select Files...");
        buttonPanel.add(browseButton);
        buttonPanel.add(selectButton);
        folderPanel.add(buttonPanel, BorderLayout.EAST);
        
        JPanel formatPanel = new JPanel(new BorderLayout(5, 5));
        formatPanel.add(new JLabel("Input Format: "), BorderLayout.WEST);
        targetFormatBox = new JComboBox<>(new String[]{"All Audio Files", "wav", "mp3", "ogg", "flac", "m4a"});
        formatPanel.add(targetFormatBox, BorderLayout.CENTER);
        
        totalSizeLabel = new JLabel("Selected: 0 KB");
        totalSizeLabel.setFont(totalSizeLabel.getFont().deriveFont(Font.BOLD, 13f));
        totalSizeLabel.setForeground(new Color(150, 180, 255));
        totalSizeLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        formatPanel.add(totalSizeLabel, BorderLayout.EAST);
        
        topPanel.add(folderPanel);
        topPanel.add(formatPanel);

        // Core processing options setup
        JPanel middlePanel = new JPanel(new GridLayout(4, 2, 10, 10));
        middlePanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(5, 15, 5, 15),
                BorderFactory.createTitledBorder("Processing Options")
        ));
        
        JPanel qualityPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        qualityPanel.add(new JLabel("Quality (0-10):"));
        qualitySlider = new JSlider(JSlider.HORIZONTAL, 0, 10, 5); 
        qualitySlider.setMajorTickSpacing(5);
        qualitySlider.setMinorTickSpacing(1);
        qualitySlider.setPaintTicks(true);
        qualitySlider.setPaintLabels(true);
        qualitySlider.setPreferredSize(new Dimension(140, 55));
        qualityPanel.add(qualitySlider);
        
        JPanel volumePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        volumePanel.add(new JLabel("Volume (dB):"));
        volumeSpinner = new JSpinner(new SpinnerNumberModel(0.0, -10.0, 10.0, 0.1));
        JSpinner.NumberEditor volEditor = new JSpinner.NumberEditor(volumeSpinner, "0.0");
        volumeSpinner.setEditor(volEditor);
        volumePanel.add(volumeSpinner);
        
        JPanel exportPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        exportPanel.add(new JLabel("Export As:"));
        exportFormatBox = new JComboBox<>(new String[]{"Same as original", "wav", "mp3", "ogg", "flac", "m4a"});
        exportPanel.add(exportFormatBox);
        
        JPanel safetyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        overwriteCheck = new JCheckBox("Overwrite original files");
        safetyPanel.add(overwriteCheck);

        JPanel normalizePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        normalizeCheck = new JCheckBox("Auto-Balance Volume (Make all equally loud)");
        normalizePanel.add(normalizeCheck);

        JPanel trimPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        trimSilenceCheck = new JCheckBox("Clean up dead air (Auto-trim silence)");
        trimPanel.add(trimSilenceCheck);

        JPanel metaPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        metadataButton = new JButton("Edit Metadata Tags...");
        metadataButton.setPreferredSize(new Dimension(180, 30));
        metaPanel.add(metadataButton);
        
        metadataButton.addActionListener(e -> openMetadataDialog());

        middlePanel.add(qualityPanel);
        middlePanel.add(volumePanel);
        middlePanel.add(exportPanel);
        middlePanel.add(safetyPanel);
        middlePanel.add(normalizePanel);
        middlePanel.add(trimPanel);
        middlePanel.add(metaPanel);
        middlePanel.add(new JPanel()); 

        JPanel centerWrapper = new JPanel(new BorderLayout(10, 10));
        centerWrapper.add(middlePanel, BorderLayout.NORTH);
        
        previewPanel = new JPanel(new BorderLayout(0, 5));
        previewPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(5, 15, 5, 15),
                BorderFactory.createTitledBorder("Audio Analysis")
        ));
        
        JPanel statsContainer = new JPanel(new GridLayout(2, 1, 0, 3));
        
        statsLabel = new JLabel("Current Peak: -- | Current Bitrate: --   ||   New Peak: -- | New Bitrate: --", SwingConstants.CENTER);
        statsLabel.setFont(statsLabel.getFont().deriveFont(Font.BOLD, 12f));
        
        metadataLabel = new JLabel("Channels: --   |   Sample Rate: --   |   Codec: --", SwingConstants.CENTER);
        metadataLabel.setFont(metadataLabel.getFont().deriveFont(11f));
        metadataLabel.setForeground(new Color(180, 190, 200));

        statsContainer.add(statsLabel);
        statsContainer.add(metadataLabel);
        previewPanel.add(statsContainer, BorderLayout.NORTH);

        spectrogramLabel = new JLabel("Select a folder to generate spectrogram", SwingConstants.CENTER);
        previewPanel.add(spectrogramLabel, BorderLayout.CENTER);
        spectrogramLabel.setPreferredSize(new Dimension(660, 280));
        
        centerWrapper.add(previewPanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout(10, 10));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 15, 15, 15));
        
        JPanel progressPanel = new JPanel(new BorderLayout(5, 5));
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        statusLabel = new JLabel("Ready.");
        progressPanel.add(progressBar, BorderLayout.CENTER);
        progressPanel.add(statusLabel, BorderLayout.SOUTH);
        
        startButton = new JButton("Convert");
        startButton.setFont(startButton.getFont().deriveFont(Font.BOLD, 14f));
        startButton.setPreferredSize(new Dimension(150, 40));
        
        bottomPanel.add(progressPanel, BorderLayout.CENTER);
        bottomPanel.add(startButton, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);
        add(centerWrapper, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        selectButton.setEnabled(false);
        startButton.setEnabled(false);

        folderPathField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { checkFolder(); }
            public void removeUpdate(DocumentEvent e) { checkFolder(); }
            public void changedUpdate(DocumentEvent e) { checkFolder(); }
            private void checkFolder() {
                boolean hasText = folderPathField.getText() != null && !folderPathField.getText().trim().isEmpty();
                selectButton.setEnabled(hasText);
                startButton.setEnabled(hasText);
            }
        });

        MouseWheelListener scrollListener = e -> {
            if (e.getSource() instanceof JComboBox) {
                JComboBox<?> box = (JComboBox<?>) e.getSource();
                int dir = e.getWheelRotation();
                int index = box.getSelectedIndex() + dir;
                if (index >= 0 && index < box.getItemCount()) {
                    box.setSelectedIndex(index);
                }
            } else if (e.getSource() instanceof JSlider) {
                JSlider slider = (JSlider) e.getSource();
                slider.setValue(slider.getValue() - e.getWheelRotation());
            }
        };
        
        targetFormatBox.addMouseWheelListener(scrollListener);
        exportFormatBox.addMouseWheelListener(scrollListener);
        qualitySlider.addMouseWheelListener(scrollListener);
        
        previewTimer = new Timer(500, e -> triggerPreviewUpdate());
        previewTimer.setRepeats(false);

        targetFormatBox.addActionListener(e -> {
            updateTotalSize();
            loadBenchmarkAndOriginal(folderPathField.getText());
        });
        
        exportFormatBox.addActionListener(e -> {
            updateTotalSize();
            previewTimer.restart();
        });
        qualitySlider.addChangeListener(e -> {
            updateTotalSize();
            previewTimer.restart();
        });
        volumeSpinner.addChangeListener(e -> previewTimer.restart());

        FFmpegEngine.verifyOrInstall(this);

        browseButton.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            String currentPath = folderPathField.getText();
            if (currentPath != null && !currentPath.isEmpty()) {
                File dir = new File(currentPath);
                if (dir.exists()) {
                    chooser.setCurrentDirectory(dir);
                }
            }
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle("Select Audio Folder");
            chooser.setPreferredSize(new Dimension(850, 600));
            
            Action details = chooser.getActionMap().get("viewTypeDetails");
            if (details != null) {
                details.actionPerformed(null);
            }
            
            SwingUtilities.invokeLater(() -> stretchNameColumn(chooser));
            
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                folderPathField.setText(chooser.getSelectedFile().getAbsolutePath());
                skippedFiles.clear(); 
                updateTotalSize();
                loadBenchmarkAndOriginal(chooser.getSelectedFile().getAbsolutePath());
            }
        });

        selectButton.addActionListener(e -> openSelectionDialog());

        startButton.addActionListener(e -> {
            String path = folderPathField.getText();
            String targetFormat = (String) targetFormatBox.getSelectedItem();
            
            List<File> filesToProcess = FileHandler.getAudioFiles(path, targetFormat);
            
            if (filesToProcess.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No matching audio files found in folder.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            List<File> actualFilesToProcess = new ArrayList<>();
            for (File f : filesToProcess) {
                if (!skippedFiles.contains(f.getName())) {
                    actualFilesToProcess.add(f);
                }
            }

            if (actualFilesToProcess.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No files selected to convert! Please check your selection.", "Warning", JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            if (currentMetaMode == 1 || currentMetaMode == 2) {
                int res = JOptionPane.showConfirmDialog(this, 
                    "WARNING: You have selected to Strip or Overwrite metadata.\n\n" +
                    "This will permanently erase all original tags, including any Album Art images embedded in the files!\n\n" +
                    "Are you sure you want to proceed?", 
                    "Metadata Loss Warning", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (res != JOptionPane.YES_OPTION) return;
            }

            String exportFormat = (String) exportFormatBox.getSelectedItem();
            String finalBitrate = kbpsMap[qualitySlider.getValue()] + "k";
            double finalVolume = (Double) volumeSpinner.getValue();
            boolean overwrite = overwriteCheck.isSelected();
            boolean doNormalize = normalizeCheck.isSelected();
            boolean doTrim = trimSilenceCheck.isSelected();

            File outputDir = null;
            
            if (overwrite) {
                int confirm = JOptionPane.showConfirmDialog(this, "This will PERMANENTLY OVERWRITE your original files.\nAre you absolutely sure you want to proceed?", "Confirm Overwrite", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (confirm != JOptionPane.YES_OPTION) {
                    return; 
                }
            } else {
                JFileChooser chooser = new JFileChooser();
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                chooser.setDialogTitle("Select Export Destination");
                chooser.setPreferredSize(new Dimension(850, 600));
                
                if (path != null && !path.isEmpty()) chooser.setCurrentDirectory(new File(path));

                Action details = chooser.getActionMap().get("viewTypeDetails");
                if (details != null) {
                    details.actionPerformed(null);
                }
                
                SwingUtilities.invokeLater(() -> stretchNameColumn(chooser));

                if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                    outputDir = chooser.getSelectedFile();
                } else {
                    int cancelChoice = JOptionPane.showConfirmDialog(this, "No export folder selected.\nDo you want to cancel the conversion entirely?", "Cancel Export?", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                    if (cancelChoice == JOptionPane.YES_OPTION) {
                        return; 
                    } else {
                        String safeFormatName = exportFormat.equals("Same as original") ? "modified" : exportFormat;
                        String autoFolderName = "MAC_Export_" + safeFormatName;
                        outputDir = new File(path, autoFolderName);
                        
                        if (!outputDir.exists()) {
                            outputDir.mkdirs(); 
                        }
                        JOptionPane.showMessageDialog(this, "Files will safely save to:\n" + autoFolderName, "Auto-Folder Created", JOptionPane.INFORMATION_MESSAGE);
                    }
                }
            }

            startButton.setEnabled(false);
            ProcessWorker worker = new ProcessWorker(this, actualFilesToProcess, exportFormat, finalBitrate, finalVolume, doNormalize, doTrim, currentMetaMode, currentMetaMap, overwrite, skippedFiles, outputDir);
            worker.execute();
        });
    }
    
    // Custom external dialog for Audacity-style metadata tag editing
    private void openMetadataDialog() {
        JDialog dialog = new JDialog(this, "Edit Metadata Tags", true);
        dialog.setSize(550, 480);
        dialog.setLayout(new BorderLayout(10, 10));

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        
        JPanel radioPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JRadioButton keepRadio = new JRadioButton("Keep Original");
        JRadioButton stripRadio = new JRadioButton("Strip All");
        JRadioButton overRadio = new JRadioButton("Overwrite All");
        
        ButtonGroup group = new ButtonGroup();
        group.add(keepRadio);
        group.add(stripRadio);
        group.add(overRadio);
        
        if (currentMetaMode == 0) keepRadio.setSelected(true);
        else if (currentMetaMode == 1) stripRadio.setSelected(true);
        else overRadio.setSelected(true);

        radioPanel.add(keepRadio);
        radioPanel.add(stripRadio);
        radioPanel.add(overRadio);
        
        JLabel hintLabel = new JLabel("Hint: Type {count} in any field to auto-number your files! (e.g. 'Song {count}')", SwingConstants.CENTER);
        hintLabel.setFont(new Font("Tahoma", Font.ITALIC, 11));
        hintLabel.setForeground(new Color(150, 180, 255));
        
        topPanel.add(radioPanel, BorderLayout.NORTH);
        topPanel.add(hintLabel, BorderLayout.SOUTH);

        String[] columnNames = {"Tag", "Value"};
        DefaultTableModel model = new DefaultTableModel(columnNames, 0) {
            @Override public boolean isCellEditable(int row, int column) { 
                return column == 1;
            }
        };

        for (String[] row : currentMetaMap) {
            model.addRow(new Object[]{row[0], row[1]});
        }

        JTable table = new JTable(model);
        table.setRowHeight(25);
        table.putClientProperty("terminateEditOnFocusLost", true);
        table.setFont(new Font("Tahoma", Font.PLAIN, 14));

        JScrollPane scrollPane = new JScrollPane(table);
        
        Runnable updateTableState = () -> {
            table.setEnabled(overRadio.isSelected());
            if (!overRadio.isSelected()) {
                table.setForeground(Color.GRAY);
            } else {
                table.setForeground(UIManager.getColor("Table.foreground"));
            }
        };
        
        keepRadio.addActionListener(e -> updateTableState.run());
        stripRadio.addActionListener(e -> updateTableState.run());
        overRadio.addActionListener(e -> updateTableState.run());
        updateTableState.run();

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okBtn = new JButton("OK");
        JButton cancelBtn = new JButton("Cancel");
        
        okBtn.addActionListener(e -> {
            if (keepRadio.isSelected()) currentMetaMode = 0;
            else if (stripRadio.isSelected()) currentMetaMode = 1;
            else currentMetaMode = 2;
            
            for (int i = 0; i < model.getRowCount(); i++) {
                currentMetaMap[i][1] = (String) model.getValueAt(i, 1);
            }
            dialog.dispose();
        });
        
        cancelBtn.addActionListener(e -> dialog.dispose());
        
        btnPanel.add(okBtn);
        btnPanel.add(cancelBtn);

        dialog.add(topPanel, BorderLayout.NORTH);
        dialog.add(scrollPane, BorderLayout.CENTER);
        dialog.add(btnPanel, BorderLayout.SOUTH);

        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }
    
    // Recursive UI scanner to manipulate native FileChooser component widths
    private void stretchNameColumn(Container c) {
        for (Component comp : c.getComponents()) {
            if (comp instanceof JTable) {
                JTable table = (JTable) comp;
                table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
                if (table.getColumnModel().getColumnCount() > 0) {
                    table.getColumnModel().getColumn(0).setPreferredWidth(450); 
                }
                return;
            }
            if (comp instanceof Container) {
                stretchNameColumn((Container) comp);
            }
        }
    }

    private String getSelectedCodecs() {
        Set<String> codecs = new HashSet<>();
        String path = folderPathField.getText();
        if (path == null || path.isEmpty()) return "--";
        
        List<File> allFiles = FileHandler.getAudioFiles(path, (String) targetFormatBox.getSelectedItem());
        for (File f : allFiles) {
            if (!skippedFiles.contains(f.getName())) {
                String name = f.getName().toLowerCase();
                if (name.endsWith(".mp3")) codecs.add("MP3");
                else if (name.endsWith(".ogg")) codecs.add("Ogg Vorbis");
                else if (name.endsWith(".flac")) codecs.add("FLAC");
                else if (name.endsWith(".wav")) codecs.add("WAV");
                else if (name.endsWith(".m4a")) codecs.add("M4A (AAC)");
            }
        }
        if (codecs.isEmpty()) return "--";
        return String.join(", ", codecs);
    }

    private void updateMetadataLabel() {
        metadataLabel.setText("Channels: " + currentChannels + "   |   Sample Rate: " + currentSampleRate + "   |   Codec: " + getSelectedCodecs());
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        else if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        else if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        else return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private void updateTotalSize() {
        String path = folderPathField.getText();
        if (path == null || path.isEmpty()) {
            totalSizeLabel.setText("Selected: 0 KB");
            updateMetadataLabel();
            return;
        }
        
        List<File> allFiles = FileHandler.getAudioFiles(path, (String) targetFormatBox.getSelectedItem());
        long totalBytes = 0;
        
        for (File f : allFiles) {
            if (!skippedFiles.contains(f.getName())) {
                totalBytes += f.length();
            }
        }
        
        String selectedStr = "Selected: " + formatSize(totalBytes);
        
        if (baselineBitrate > 0 && totalBytes > 0) {
            String exportFmt = (String) exportFormatBox.getSelectedItem();
            int targetKbps = kbpsMap[qualitySlider.getValue()];
            
            if (exportFmt.equals("wav")) targetKbps = 1411;
            else if (exportFmt.equals("flac")) targetKbps = 900;
            
            long estBytes = (long) (totalBytes * ((double) targetKbps / baselineBitrate));
            totalSizeLabel.setText(selectedStr + "   ->   Est. Export: ~" + formatSize(estBytes));
        } else {
            totalSizeLabel.setText(selectedStr);
        }
        
        updateMetadataLabel();
    }

    private void loadBenchmarkAndOriginal(String folderPath) {
        if (folderPath == null || folderPath.isEmpty()) return;
        
        spectrogramLabel.setIcon(null);
        spectrogramLabel.setText("Scanning current track baseline...");
        
        new SwingWorker<OriginalResult, Void>() {
            @Override
            protected OriginalResult doInBackground() throws Exception {
                List<File> files = FileHandler.getAudioFiles(folderPath, (String) targetFormatBox.getSelectedItem());
                if (files.isEmpty()) return null;
                
                benchmarkFile = files.get(0);
                for (File f : files) {
                    if (f.length() > benchmarkFile.length()) benchmarkFile = f;
                }
                
                File tempImg = new File(System.getProperty("java.io.tmpdir"), "mac_orig_" + System.currentTimeMillis() + ".png");
                
                String[] metaData = FFmpegEngine.getAudioMetadata(benchmarkFile);
                String foundBitrate = FFmpegEngine.generateOriginalSpectrogram(benchmarkFile, tempImg);
                
                if (foundBitrate != null && tempImg.exists()) {
                    BufferedImage bimg = ImageIO.read(tempImg);
                    tempImg.delete();
                    
                    int w = bimg.getWidth();
                    int h = bimg.getHeight();
                    ghostOriginal = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                    
                    int sumY = 0;
                    int countY = 0;

                    for (int x = 0; x < w; x++) {
                        boolean foundPeak = false;
                        for (int y = 0; y < h; y++) {
                            int rgb = bimg.getRGB(x, y);
                            int r = (rgb >> 16) & 0xFF;
                            int g = (rgb >> 8) & 0xFF;
                            int b = rgb & 0xFF;

                            boolean isBlackish = (r + g + b) < 60;

                            if (isBlackish) {
                                ghostOriginal.setRGB(x, y, 0x00000000);
                            } else {
                                ghostOriginal.setRGB(x, y, (90 << 24) | (rgb & 0x00FFFFFF)); 
                            }

                            if (!foundPeak && (r + g + b) > 150) {
                                sumY += y;
                                countY++;
                                foundPeak = true;
                            }
                        }
                    }
                    
                    averageOriginalPeakY = countY > 0 ? (sumY / countY) : -1;
                    
                    double origKhz = 0;
                    if (averageOriginalPeakY != -1) {
                        origKhz = 22.0 * ((h - averageOriginalPeakY) / (double)h);
                        if (origKhz < 0) origKhz = 0;
                        if (origKhz > 22) origKhz = 22;
                    }

                    OriginalResult res = new OriginalResult();
                    res.img = bimg;
                    res.bitrate = foundBitrate;
                    
                    try {
                        res.bitrateInt = Integer.parseInt(foundBitrate.replace(" kbps", "").trim());
                    } catch(Exception ex) {
                        res.bitrateInt = -1;
                    }
                    
                    res.peak = String.format("%.1f kHz", origKhz);
                    res.sampleRate = metaData[0];
                    res.channels = metaData[1];
                    return res;
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    OriginalResult res = get();
                    if (res != null) {
                        originalSpectrogramImage = res.img;
                        currentBitrate = res.bitrate;
                        currentPeak = res.peak;
                        currentSampleRate = res.sampleRate;
                        currentChannels = res.channels;
                        baselineBitrate = res.bitrateInt; 
                        
                        if (baselineBitrate > 0) {
                            int closestIdx = 4; 
                            int minDiff = Integer.MAX_VALUE;
                            for (int i = 0; i < kbpsMap.length; i++) {
                                int diff = Math.abs(kbpsMap[i] - baselineBitrate);
                                if (diff < minDiff) {
                                    minDiff = diff;
                                    closestIdx = i;
                                }
                            }
                            qualitySlider.setValue(closestIdx); 
                        }
                        
                        updateTotalSize(); 
                        updateMetadataLabel();
                        
                        statsLabel.setText("Current Peak: " + currentPeak + " | Current Bitrate: " + currentBitrate + "   ||   Generating preview...");
                        triggerPreviewUpdate(); 
                    } else {
                        spectrogramLabel.setText("Failed to load baseline.");
                    }
                } catch (Exception e) {
                    spectrogramLabel.setText("Error reading current file.");
                }
            }
        }.execute();
    }

    private void triggerPreviewUpdate() {
        if (benchmarkFile == null || originalSpectrogramImage == null || ghostOriginal == null) return;
        
        String exportFmt = (String) exportFormatBox.getSelectedItem();
        String kbpsVal = kbpsMap[qualitySlider.getValue()] + "k";
        double vol = (Double) volumeSpinner.getValue();
        
        if (currentPreviewWorker != null && !currentPreviewWorker.isDone()) {
            currentPreviewWorker.cancel(true); 
        }
        
        spectrogramLabel.setIcon(null);
        spectrogramLabel.setText("Rendering overlay preview...");
        
        currentPreviewWorker = new SwingWorker<PreviewResult, Void>() {
            @Override
            protected PreviewResult doInBackground() throws Exception {
                File tempImg = new File(System.getProperty("java.io.tmpdir"), "mac_prev_" + System.currentTimeMillis() + ".png");
                
                String newBitrateStr = FFmpegEngine.generateSpectrogramPreview(benchmarkFile, tempImg, exportFmt, kbpsVal, vol);
                
                if (newBitrateStr != null && tempImg.exists()) {
                    BufferedImage previewImg = ImageIO.read(tempImg);
                    tempImg.delete(); 
                    
                    int w = originalSpectrogramImage.getWidth();
                    int h = originalSpectrogramImage.getHeight();
                    
                    int prevSumY = 0;
                    int prevCountY = 0;
                    for (int x = 0; x < w; x++) {
                        boolean foundPeak = false;
                        for (int y = 0; y < h; y++) {
                            int rgb = previewImg.getRGB(x, y);
                            int r = (rgb >> 16) & 0xFF;
                            int g = (rgb >> 8) & 0xFF;
                            int b = rgb & 0xFF;

                            if (!foundPeak && (r + g + b) > 150) {
                                prevSumY += y;
                                prevCountY++;
                                foundPeak = true;
                            }
                        }
                    }
                    
                    int averagePreviewPeakY = prevCountY > 0 ? (prevSumY / prevCountY) : -1;
                    double newKhz = 0;
                    if (averagePreviewPeakY != -1) {
                        newKhz = 22.0 * ((h - averagePreviewPeakY) / (double)h);
                        if (newKhz < 0) newKhz = 0;
                        if (newKhz > 22) newKhz = 22;
                    }
                    
                    int canvasWidth = 660; 
                    BufferedImage combined = new BufferedImage(canvasWidth, h, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = combined.createGraphics();
                    
                    Color panelBg = UIManager.getColor("Panel.background");
                    if (panelBg == null) panelBg = new Color(30, 32, 36); 
                    g.setColor(panelBg);
                    g.fillRect(0, 0, canvasWidth, h);
                    
                    g.setColor(new Color(110, 120, 130));
                    g.setFont(new Font("Tahoma", Font.PLAIN, 11)); 
                    g.drawString("22 kHz -", 3, 15);
                    g.drawString("20 kHz -", 3, 35);
                    g.drawString("15 kHz -", 3, 85);
                    g.drawString("10 kHz -", 3, 135);
                    g.drawString(" 5.0 kHz-", 2, 185);
                    g.drawString(" 2.0 kHz-", 2, 235);
                    
                    int gradX = 600;
                    LinearGradientPaint lgp = new LinearGradientPaint(
                        gradX, 10, gradX, 230,
                        new float[]{0.0f, 0.2f, 0.4f, 0.6f, 0.8f, 1.0f},
                        new Color[]{
                            new Color(0xfcfdbf), 
                            new Color(0xfe9f6d), 
                            new Color(0xde4968), 
                            new Color(0x8c2981), 
                            new Color(0x3b0f70), 
                            new Color(0x000004)  
                        }
                    );
                    g.setPaint(lgp);
                    g.fillRect(gradX, 10, 10, 220);
                    
                    g.setColor(new Color(110, 120, 130));
                    g.drawString("-   0 dB", 613, 15);
                    g.drawString("- -20 dB", 613, 55);
                    g.drawString("- -40 dB", 613, 95);
                    g.drawString("- -60 dB", 613, 135);
                    g.drawString("- -80 dB", 613, 175);
                    g.drawString("- -100dB", 611, 215);
                    g.drawString("- -120dB", 611, 235);

                    g.drawImage(previewImg, 50, 0, null);
                    g.drawImage(ghostOriginal, 50, 0, null);
                    
                    if (averageOriginalPeakY != -1) {
                        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g.setColor(new Color(255, 40, 40));
                        g.setStroke(new BasicStroke(2.5f));
                        g.drawLine(50, averageOriginalPeakY, w + 50, averageOriginalPeakY);
                    }
                    
                    g.dispose();
                    
                    PreviewResult res = new PreviewResult();
                    res.icon = new ImageIcon(combined);
                    res.bitrate = newBitrateStr;
                    res.peak = String.format("%.1f kHz", newKhz);
                    return res;
                }
                return null;
            }

            @Override
            protected void done() {
                if (isCancelled()) return;
                try {
                    PreviewResult res = get();
                    if (res != null) {
                        spectrogramLabel.setText("");
                        spectrogramLabel.setIcon(res.icon);
                        
                        statsLabel.setText("Current Peak: " + currentPeak + " | Current Bitrate: " + currentBitrate + 
                                           "   ||   New Peak: " + res.peak + " | New Bitrate: " + res.bitrate);
                    } else {
                        spectrogramLabel.setText("No preview available.");
                    }
                } catch (Exception e) {
                    spectrogramLabel.setText("Error generating preview.");
                }
            }
        };
        currentPreviewWorker.execute();
    }

    // Handles the native File Explorer pop-up for audio selection
    private void openSelectionDialog() {
        String path = folderPathField.getText();
        if (path == null || path.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select a folder first!");
            return;
        }

        String targetFormat = (String) targetFormatBox.getSelectedItem();
        List<File> allFiles = FileHandler.getAudioFiles(path, targetFormat);
        
        if (allFiles.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No files found!");
            return;
        }

        JDialog dialog = new JDialog(this, "Select Files to Process", true);
        dialog.setSize(550, 600);
        dialog.setLayout(new BorderLayout(10, 10));

        JPanel headerWrapper = new JPanel(new BorderLayout());
        headerWrapper.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        
        JPanel searchPanel = new JPanel(new BorderLayout(5, 5));
        searchPanel.add(new JLabel("Search File:"), BorderLayout.WEST);
        JTextField searchField = new JTextField();
        searchPanel.add(searchField, BorderLayout.CENTER);

        JLabel subtitle = new JLabel("Ctrl+Click to Toggle || Shift+Click for Range || Double-Click to Play Audio", SwingConstants.CENTER);
        subtitle.setFont(new Font("Tahoma", Font.PLAIN, 11)); 
        subtitle.setForeground(new Color(150, 160, 170));
        subtitle.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
        
        headerWrapper.add(searchPanel, BorderLayout.NORTH);
        headerWrapper.add(subtitle, BorderLayout.SOUTH);

        String[] columnNames = {"Name", "Type", "Size"};
        DefaultTableModel model = new DefaultTableModel(columnNames, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };

        for (File f : allFiles) {
            String name = f.getName();
            String type = name.contains(".") ? name.substring(name.lastIndexOf('.')).toUpperCase() : "FILE";
            model.addRow(new Object[]{name, type, new FileSize(f.length())});
        }

        JTable table = new JTable(model);
        
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setRowHeight(30);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setSelectionBackground(new Color(41, 74, 110));
        table.setSelectionForeground(Color.WHITE);
        table.setFont(new Font("Tahoma", Font.PLAIN, 14)); 
        table.getTableHeader().setFont(new Font("Tahoma", Font.BOLD, 12));

        table.getColumnModel().getColumn(0).setPreferredWidth(300);
        table.getColumnModel().getColumn(1).setPreferredWidth(80);
        table.getColumnModel().getColumn(2).setPreferredWidth(100);

        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);

        JScrollPane scrollPane = new JScrollPane(table);

        for (int i = 0; i < model.getRowCount(); i++) {
            String name = (String) model.getValueAt(i, 0);
            if (!skippedFiles.contains(name)) {
                int viewRow = table.convertRowIndexToView(i);
                if (viewRow != -1) {
                    table.addRowSelectionInterval(viewRow, viewRow);
                }
            }
        }

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filter(); }
            public void removeUpdate(DocumentEvent e) { filter(); }
            public void changedUpdate(DocumentEvent e) { filter(); }
            private void filter() {
                String text = searchField.getText();
                if (text.trim().length() == 0) sorter.setRowFilter(null);
                else sorter.setRowFilter(RowFilter.regexFilter("(?i)" + text, 0));
            }
        });

        table.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (row == -1) {
                    table.clearSelection();
                } else if (SwingUtilities.isRightMouseButton(e)) {
                    if (!table.isRowSelected(row)) {
                        table.setRowSelectionInterval(row, row);
                    }
                }
            }
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                    int row = table.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        int modelRow = table.convertRowIndexToModel(row);
                        String fileName = (String) model.getValueAt(modelRow, 0);
                        File f = new File(folderPathField.getText(), fileName);
                        try {
                            Desktop.getDesktop().open(f);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            }
        });

        scrollPane.getViewport().addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                table.clearSelection();
            }
        });

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        JButton selectAllBtn = new JButton("Select All");
        JButton deselectAllBtn = new JButton("Deselect All");
        JButton doneBtn = new JButton("Done");
        
        selectAllBtn.addActionListener(e -> {
            for (int i = 0; i < table.getRowCount(); i++) {
                table.addRowSelectionInterval(i, i);
            }
        });

        deselectAllBtn.addActionListener(e -> table.clearSelection());
        
        doneBtn.addActionListener(e -> {
            for (int i = 0; i < model.getRowCount(); i++) {
                String fileName = (String) model.getValueAt(i, 0);
                int viewRow = table.convertRowIndexToView(i);
                if (viewRow != -1) {
                    if (table.isRowSelected(viewRow)) {
                        skippedFiles.remove(fileName);
                    } else {
                        skippedFiles.add(fileName);
                    }
                }
            }
            updateTotalSize();
            dialog.dispose();
        });
        
        btnPanel.add(selectAllBtn);
        btnPanel.add(deselectAllBtn);
        btnPanel.add(doneBtn);

        dialog.add(headerWrapper, BorderLayout.NORTH);
        dialog.add(scrollPane, BorderLayout.CENTER);
        dialog.add(btnPanel, BorderLayout.SOUTH);

        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    public static void main(String[] args) {
        System.setProperty("file.encoding", "UTF-8");
        
        JFrame.setDefaultLookAndFeelDecorated(true);
        System.setProperty("flatlaf.useWindowDecorations", "true");
        System.setProperty("flatlaf.useNativeWindowDecorations", "false");
        System.setProperty("flatlaf.animation", "true");

        try {
            UIManager.put("TitlePane.buttonStyle", "mac");
            UIManager.put("defaultFont", new java.awt.Font("Tahoma", java.awt.Font.PLAIN, 14)); 
            
            UIManager.put("ComboBox.selectionArc", 8); 
            UIManager.put("Component.focusWidth", 1); 
            UIManager.put("PopupMenu.dropShadow", true); 
            
            UIManager.put("Slider.thumbColor", new Color(60, 130, 200));
            UIManager.put("Slider.thumbHoverColor", new Color(90, 160, 230));
            UIManager.put("Slider.thumbPressedColor", new Color(140, 190, 255));
            UIManager.put("Spinner.buttonHoverBackground", new Color(70, 75, 80));
            UIManager.put("Spinner.buttonPressedBackground", new Color(90, 95, 100));
            UIManager.put("Button.hoverBackground", new Color(70, 75, 80));
            UIManager.put("Button.pressedBackground", new Color(90, 95, 100));
            
            UIManager.put("FileChooser.noPlacesBar", true);

            FlatMacDarkLaf.setup();
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            new MainUI().setVisible(true);
        });
    }
}