import javax.swing.*;
import java.awt.Component;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileHandler {
    
    // Memory variable for remembering the last folder visited
    private static File lastVisitedDirectory = null;

    public static String chooseDirectory(Component parent) {
        JFileChooser chooser = new JFileChooser();
        
        if (lastVisitedDirectory != null) {
            chooser.setCurrentDirectory(lastVisitedDirectory);
        }
        
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int res = chooser.showOpenDialog(parent);
        
        if (res == JFileChooser.APPROVE_OPTION) {
            lastVisitedDirectory = chooser.getSelectedFile();
            return lastVisitedDirectory.getAbsolutePath();
        }
        return null;
    }

    public static List<File> getAudioFiles(String folderPath, String targetFormat) {
        List<File> audioFiles = new ArrayList<>();
        File folder = new File(folderPath);

        if (folder.exists() && folder.isDirectory()) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        String name = file.getName().toLowerCase();
                        boolean isAudio = name.endsWith(".wav") || name.endsWith(".mp3") || 
                                          name.endsWith(".ogg") || name.endsWith(".flac") || 
                                          name.endsWith(".m4a");

                        if (isAudio) {
                            if (targetFormat.equals("All Audio Files") || name.endsWith("." + targetFormat.toLowerCase())) {
                                audioFiles.add(file);
                            }
                        }
                    }
                }
            }
        }
        return audioFiles;
    }
}