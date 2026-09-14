import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

/**
 * Core wrapper class for FFmpeg CLI operations.
 * Handles installation verification, metadata parsing, spectrogram generation, and advanced audio filtering.
 */
public class FFmpegEngine {

    public static boolean checkFFmpeg() {
        try {
            ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-version");
            Process process = pb.start();
            process.waitFor();
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static void verifyOrInstall(JFrame parentFrame) {
        if (checkFFmpeg()) return;

        Object[] options = {"Automatically Install", "Force Exit"};
        int choice = JOptionPane.showOptionDialog(parentFrame,
                "FFmpeg is required but not found on this system.\nWould you like to install it now via Windows Package Manager (winget)?",
                "Dependency Missing",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null, options, options[0]);

        if (choice == JOptionPane.YES_OPTION) {
            try {
                ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", "start", "cmd.exe", "/k", 
                        "winget install ffmpeg & echo. & echo Installation finished. Please close this console window to continue.");
                pb.start();
                
                JOptionPane.showMessageDialog(parentFrame, 
                        "Installation initiated in a new terminal window.\nPlease complete the installation, close the terminal, and then click OK here.", 
                        "Installing FFmpeg", JOptionPane.INFORMATION_MESSAGE);
                
                if (!checkFFmpeg()) {
                    JOptionPane.showMessageDialog(parentFrame, "FFmpeg still not detected. Application will exit.", "Error", JOptionPane.ERROR_MESSAGE);
                    System.exit(0);
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(parentFrame, "Failed to launch installer. Exiting.", "Error", JOptionPane.ERROR_MESSAGE);
                System.exit(0);
            }
        } else {
            System.exit(0);
        }
    }

    public static String[] getAudioMetadata(File file) {
        String[] meta = {"-- kHz", "--"};
        try {
            ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-i", file.getAbsolutePath());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while((line = r.readLine()) != null) {
                if (line.contains("Audio:")) {
                    if (line.contains("stereo")) meta[1] = "Stereo";
                    else if (line.contains("mono")) meta[1] = "Mono";
                    else meta[1] = "Multi-channel";
                    
                    if (line.contains("Hz")) {
                        String[] parts = line.split(",");
                        for (String pStr : parts) {
                            if (pStr.contains("Hz")) {
                                String hzStr = pStr.replace("Hz", "").trim();
                                try {
                                    double khz = Double.parseDouble(hzStr) / 1000.0;
                                    meta[0] = String.format("%.1f kHz", khz);
                                } catch(Exception e) {
                                    meta[0] = pStr.trim();
                                }
                            }
                        }
                    }
                }
            }
            p.waitFor();
        } catch(Exception e) {}
        return meta;
    }
    
    public static String generateOriginalSpectrogram(File inputFile, File outputImage) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg", "-y", "-i", inputFile.getAbsolutePath(),
                "-lavfi", "showspectrumpic=s=540x240:mode=combined:color=magma:legend=0",
                "-vframes", "1", outputImage.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            
            String line;
            String foundBitrate = "Unknown kbps";
            
            while((line = r.readLine()) != null) {
                if (line.contains("bitrate:")) {
                    String[] parts = line.split("bitrate:");
                    if (parts.length > 1) {
                        String brRaw = parts[1].trim().split(" ")[0]; 
                        try {
                            int brInt = (int) Math.round(Double.parseDouble(brRaw));
                            // Prevents integer overflow bugs from variable bitrate OGG metadata
                            if (brInt <= 0 || brInt > 9000) foundBitrate = "Unknown kbps";
                            else foundBitrate = brInt + " kbps";
                        } catch(Exception ex) {
                            foundBitrate = brRaw + " kbps";
                        }
                    }
                }
            }
            p.waitFor();
            if (p.exitValue() == 0) return foundBitrate;
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public static String generateSpectrogramPreview(File inputFile, File outputImage, String formatTarget, String bitrateValue, double volumeValue) {
        try {
            String currentExt = inputFile.getName().contains(".") ? inputFile.getName().substring(inputFile.getName().lastIndexOf('.') + 1) : "wav";
            String finalExt = formatTarget.equals("Same as original") ? currentExt : formatTarget;
            
            File tempAudio = new File(System.getProperty("java.io.tmpdir"), "mac_prev_audio_" + System.currentTimeMillis() + "." + finalExt);
            
            List<String> audioCmd = new ArrayList<>();
            audioCmd.add("ffmpeg");
            audioCmd.add("-y");
            audioCmd.add("-i");
            audioCmd.add(inputFile.getAbsolutePath());
            audioCmd.add("-b:a"); 
            audioCmd.add(bitrateValue);
            if (volumeValue != 0.0) {
                audioCmd.add("-filter:a");
                audioCmd.add("volume=" + volumeValue + "dB");
            }
            audioCmd.add(tempAudio.getAbsolutePath());

            ProcessBuilder pb1 = new ProcessBuilder(audioCmd);
            pb1.redirectErrorStream(true);
            Process p1 = pb1.start();
            BufferedReader r1 = new BufferedReader(new InputStreamReader(p1.getInputStream()));
            
            String line;
            String foundBitrate = "Unknown kbps";
            String cleanTarget = bitrateValue.replace("k", ""); 
            
            while((line = r1.readLine()) != null) {
                if(line.contains("bitrate=")) {
                    String[] parts = line.split("bitrate=");
                    if (parts.length > 1) {
                        String brRaw = parts[1].trim().split(" ")[0]; 
                        brRaw = brRaw.replace("kbits/s", "").replace("kb/s", "").trim();
                        try {
                            int brInt = (int) Math.round(Double.parseDouble(brRaw));
                            if (brInt <= 0 || brInt > 9000) {
                                foundBitrate = cleanTarget + " kbps";
                            } else {
                                foundBitrate = brInt + " kbps";
                            }
                        } catch(Exception ex) {
                            foundBitrate = cleanTarget + " kbps";
                        }
                    }
                }
            }
            p1.waitFor();
            if (!tempAudio.exists()) return null;

            ProcessBuilder pb2 = new ProcessBuilder(
                "ffmpeg", "-y", "-i", tempAudio.getAbsolutePath(),
                "-lavfi", "showspectrumpic=s=540x240:mode=combined:color=magma:legend=0",
                "-vframes", "1", outputImage.getAbsolutePath()
            );
            pb2.redirectErrorStream(true);
            Process p2 = pb2.start();
            BufferedReader r2 = new BufferedReader(new InputStreamReader(p2.getInputStream()));
            while(r2.readLine() != null) {}
            p2.waitFor();

            tempAudio.delete();
            if (p2.exitValue() == 0) return foundBitrate;
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean processAdvanced(File inputFile, String formatTarget, String bitrateValue, double volumeValue, 
                                          boolean normalize, boolean trimSilence, int metaMode, String[][] customMetaMap, 
                                          boolean overwrite, File outputDir, int fileIndex) {
        try {
            String originalPath = inputFile.getAbsolutePath();
            String directory = inputFile.getParent();
            String originalName = inputFile.getName();
            String nameWithoutExtension = originalName.contains(".") ? originalName.substring(0, originalName.lastIndexOf('.')) : originalName;
            String currentExt = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf('.') + 1) : "";
            
            String targetDir = (overwrite || outputDir == null) ? directory : outputDir.getAbsolutePath();
            String finalExt = formatTarget.equals("Same as original") ? currentExt : formatTarget;
            
            // Dynamic filename parsing for the {count} and {original} variables
            String filePattern = "{original}";
            if (metaMode == 2) {
                for (String[] row : customMetaMap) {
                    if (row[0].equals("Output Filename")) filePattern = row[1];
                }
                if (!filePattern.isEmpty()) {
                    nameWithoutExtension = filePattern.replace("{original}", nameWithoutExtension).replace("{count}", String.valueOf(fileIndex));
                }
            }

            File finalOutputFile = new File(targetDir, nameWithoutExtension + "." + finalExt);
            
            boolean needsTemp = originalPath.equals(finalOutputFile.getAbsolutePath());
            File outputFile = needsTemp ? new File(targetDir, "temp_mac_" + originalName) : finalOutputFile;
            
            List<String> cmd = new ArrayList<>();
            cmd.add("ffmpeg");
            cmd.add("-y"); 
            cmd.add("-i");
            cmd.add(originalPath);
            cmd.add("-b:a"); 
            cmd.add(bitrateValue);
            
            // Apply Metadata Tags
            if (metaMode == 0) {
                cmd.add("-map_metadata"); cmd.add("0"); 
                cmd.add("-id3v2_version"); cmd.add("3"); 
            } else if (metaMode == 1) {
                cmd.add("-map_metadata"); cmd.add("-1"); 
            } else if (metaMode == 2) {
                cmd.add("-map_metadata"); cmd.add("-1"); 
                cmd.add("-id3v2_version"); cmd.add("3"); 
                for (String[] tag : customMetaMap) {
                    String key = tag[0].toLowerCase().replace(" ", "");
                    if (key.equals("outputfilename")) continue; 
                    if (key.equals("tracktitle")) key = "title";
                    if (key.equals("artistname")) key = "artist";
                    if (key.equals("albumtitle")) key = "album";
                    if (key.equals("tracknumber")) key = "track";
                    if (key.equals("year")) key = "date";
                    
                    String val = tag[1];
                    if (!val.isEmpty()) {
                        val = val.replace("{count}", String.valueOf(fileIndex));
                        cmd.add("-metadata"); 
                        cmd.add(key + "=" + val);
                    }
                }
            }

            // Apply Audio Processing Filters
            List<String> filters = new ArrayList<>();
            if (trimSilence) {
                filters.add("silenceremove=start_periods=1:start_duration=0:start_threshold=-50dB:stop_periods=-1:stop_duration=0:stop_threshold=-50dB");
            }
            if (normalize) {
                filters.add("loudnorm=I=-14:TP=-1.0:LRA=11");
            } else if (volumeValue != 0.0) {
                filters.add("volume=" + volumeValue + "dB");
            }
            
            if (!filters.isEmpty()) {
                cmd.add("-filter:a");
                cmd.add(String.join(",", filters));
            }
            
            cmd.add(outputFile.getAbsolutePath());

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true); 
            Process process = pb.start();
            
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                while (reader.readLine() != null) {}
            }
            
            process.waitFor();

            if (process.exitValue() == 0) {
                if (overwrite) {
                    if (needsTemp) {
                        inputFile.delete();
                        outputFile.renameTo(finalOutputFile);
                    } else {
                        inputFile.delete(); 
                    }
                }
                return true;
            }
            return false;

        } catch (Exception e) {
            return false;
        }
    }
}