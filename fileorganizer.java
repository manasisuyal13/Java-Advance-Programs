/**
 * FileOrganizer.java
 *
 * SMALL UTILITY: Organize files in a directory into folders by file type.
 *
 * AUTHOR: Manasi
 * PURPOSE: Useful, reusable utility for organizing messy folders.
 *
 * FEATURES:
 * - Classifies files into categories by extension (Images, Documents, Videos, Audio, Archives, Others)
 * - Dry-run mode: shows what would be moved without changing files
 * - Safe moves: avoids overwriting existing files by appending a numeric suffix
 * - Creates a simple undo log (organizer.log) with original and new paths
 *
 * USAGE:
 * 1) Compile:
 *    javac FileOrganizer.java
 *
 * 2) Run (organize current directory):
 *    java FileOrganizer .
 *
 * 3) Run with dry-run (preview only):
 *    java FileOrganizer /path/to/folder --dry
 *
 * 4) Run and actually move:
 *    java FileOrganizer /path/to/folder --run
 *
 * 5) Run default (same as --run):
 *    java FileOrganizer /path/to/folder
 *
 * NOTE:
 * - The program writes an "organizer.log" (in the target directory) that lists moves:
 *   original_path -> new_path
 *
 * - To undo, you could manually move files back using the log or implement a small parser.
 *   (Undo is left manual to avoid accidental destructive actions.)
 *
 * LICENSE:
 * - Free to use and modify. Good for including as a handy script or contribution to a repo.
 */

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class FileOrganizer {

    // Map categories to file extensions (lowercase, without dot)
    private static final Map<String, List<String>> CATEGORY_MAP = new LinkedHashMap<>();

    static {
        CATEGORY_MAP.put("Images", Arrays.asList("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "heic"));
        CATEGORY_MAP.put("Videos", Arrays.asList("mp4", "mkv", "mov", "avi", "flv", "wmv", "webm"));
        CATEGORY_MAP.put("Audio", Arrays.asList("mp3", "wav", "aac", "flac", "ogg", "m4a"));
        CATEGORY_MAP.put("Documents", Arrays.asList("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "odt"));
        CATEGORY_MAP.put("Archives", Arrays.asList("zip", "rar", "7z", "tar", "gz", "bz2", "xz"));
        // "Others" category will hold everything else
    }

    private final Path targetDir;
    private final boolean dryRun;
    private final List<String> logLines = new ArrayList<>();

    public FileOrganizer(Path targetDir, boolean dryRun) {
        this.targetDir = targetDir;
        this.dryRun = dryRun;
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsageAndExit();
        }

        Path dir = Paths.get(args[0]);
        boolean dry = false;

        // parse optional flags
        for (int i = 1; i < args.length; i++) {
            String a = args[i].toLowerCase();
            if (a.equals("--dry") || a.equals("-d") || a.equals("--preview")) dry = true;
            if (a.equals("--run") || a.equals("-r") || a.equals("--apply")) dry = false; // explicit run
        }

        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            System.err.println("Error: Provided path is not a directory or does not exist: " + dir.toAbsolutePath());
            System.exit(2);
        }

        FileOrganizer organizer = new FileOrganizer(dir, dry);
        try {
            organizer.organize();
        } catch (IOException e) {
            System.err.println("An error occurred while organizing: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void printUsageAndExit() {
        System.out.println("FileOrganizer - Organize files into type-based folders");
        System.out.println("Usage: java FileOrganizer <directory> [--dry | --run]");
        System.out.println("  --dry    : preview changes without moving files");
        System.out.println("  --run    : actually move files (default if not specified)");
        System.exit(1);
    }

    public void organize() throws IOException {
        System.out.println("Target directory: " + targetDir.toAbsolutePath());
        System.out.println(dryRun ? "Mode: DRY RUN (no files will be moved)" : "Mode: RUN (files will be moved)");
        System.out.println();

        // Walk the directory non-recursively: only organize top-level files.
        // If you want recursive behavior, change to Files.walkFileTree and handle directories.
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(targetDir)) {
            for (Path entry : stream) {
                // skip directories and the log file itself
                if (Files.isDirectory(entry)) continue;
                if (entry.getFileName().toString().equalsIgnoreCase("organizer.log")) continue;

                String filename = entry.getFileName().toString();
                String ext = getFileExtension(filename).toLowerCase();

                String category = categoryOfExtension(ext);

                if (category == null) category = "Others";

                Path categoryDir = targetDir.resolve(category);
                Path dest = categoryDir.resolve(filename);

                // Ensure unique destination
                dest = resolveUniquePath(dest);

                // Add to log
                logLines.add(entry.toAbsolutePath().toString() + " -> " + dest.toAbsolutePath().toString());

                // Create category dir if required
                if (!Files.exists(categoryDir)) {
                    if (dryRun) {
                        System.out.println("[DRY] Create directory: " + categoryDir);
                    } else {
                        Files.createDirectories(categoryDir);
                        System.out.println("Created directory: " + categoryDir.getFileName());
                    }
                }

                // Move file
                if (dryRun) {
                    System.out.println("[DRY] Move: " + entry.getFileName() + "  ->  " + category + "/" + dest.getFileName());
                } else {
                    try {
                        Files.move(entry, dest, StandardCopyOption.REPLACE_EXISTING);
                        System.out.println("Moved: " + entry.getFileName() + "  ->  " + category + "/" + dest.getFileName());
                    } catch (IOException e) {
                        System.err.println("Failed to move " + entry.getFileName() + ": " + e.getMessage());
                    }
                }
            }
        }

        // Write the log (only in run mode; in dry-run, also create a preview log in memory)
        Path logPath = targetDir.resolve("organizer.log");
        if (dryRun) {
            System.out.println();
            System.out.println("Dry-run preview complete. The following actions WOULD be performed:");
            logLines.forEach(System.out::println);
        } else {
            writeLog(logPath);
            System.out.println();
            System.out.println("Organization complete. Log written to: " + logPath.toAbsolutePath());
        }
    }

    // Find category for the given extension
    private String categoryOfExtension(String ext) {
        if (ext.isEmpty()) return null;
        for (Map.Entry<String, List<String>> e : CATEGORY_MAP.entrySet()) {
            if (e.getValue().contains(ext)) return e.getKey();
        }
        return null;
    }

    // Get extension (without dot). Returns empty string if none.
    private static String getFileExtension(String filename) {
        int idx = filename.lastIndexOf('.');
        if (idx == -1 || idx == filename.length() - 1) return "";
        return filename.substring(idx + 1);
    }

    // Avoid overwriting by appending (1), (2), etc.
    private Path resolveUniquePath(Path dest) {
        if (!Files.exists(dest)) return dest;

        String name = dest.getFileName().toString();
        int idx = name.lastIndexOf('.');
        String base = (idx == -1) ? name : name.substring(0, idx);
        String ext = (idx == -1) ? "" : name.substring(idx);

        int counter = 1;
        Path parent = dest.getParent();
        Path candidate;
        do {
            String newName = base + " (" + counter + ")" + ext;
            candidate = parent.resolve(newName);
            counter++;
        } while (Files.exists(candidate));
        return candidate;
    }

    // Write log of moves
    private void writeLog(Path logPath) {
        try (BufferedWriter writer = Files.newBufferedWriter(logPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            writer.write("# FileOrganizer log - " + new Date().toString());
            writer.newLine();
            for (String line : logLines) {
                writer.write(line);
                writer.newLine();
            }
        } catch (IOException e) {
            System.err.println("Failed to write log: " + e.getMessage());
        }
    }
}
