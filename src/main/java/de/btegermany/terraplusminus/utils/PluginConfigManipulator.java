package de.btegermany.terraplusminus.utils;

import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.function.Function;


public class PluginConfigManipulator {
    private final Plugin plugin;

    public PluginConfigManipulator(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Remove all lines in plugin's config file that contain the given needle.
     *
     * @param needle the needle to search for in lines
     */
    public void deleteLine(String needle) {
        this.transformLinesContaining(needle, l -> new String[0]);
    }

    /**
     * Add given content above all lines in plugin's config file that contain the given needle.
     *
     * @param needle the needle to search for in lines
     */
    public void addLineAbove(String needle, String content) {
        this.transformLinesContaining(needle, l -> new String[] {content, l});
    }

    /**
     * Add given content below all lines in plugin's config file that contain the given needle.
     *
     * @param needle the needle to search for in lines
     */
    public void addLineBelow(String needle, String content) {
        this.transformLinesContaining(needle, l -> new String[] {l, content});
    }

    private void transformLinesContaining(String needle, Function<@NotNull String, @NotNull String[]> transformer) {
        File inputFile = new File(this.plugin.getDataFolder() + File.separator + "config.yml");
        File tempFile = new File(this.plugin.getDataFolder() + File.separator + "temp.yml");
        try (
                BufferedReader reader = new BufferedReader(new FileReader(inputFile));
                BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))
        ) {

            String currentLine;
            while ((currentLine = reader.readLine()) != null) {
                if (!currentLine.contains(needle)) {
                    writer.write(currentLine + "\n");
                } else {
                    String[] transformed = transformer.apply(currentLine);
                    for (String line: transformed) {
                        writer.write(line + "\n");
                    }
                }
            }

            boolean deleted = inputFile.delete();
            boolean renamed = tempFile.renameTo(inputFile);
            if (!deleted || !renamed) {
                this.plugin.getComponentLogger().warn("Failed to swap temporary file with config");
            }
        } catch (IOException e) {
            this.plugin.getComponentLogger().error("Failed to transform config file {} with needle '{}'", inputFile, needle, e);
        }
    }

}
