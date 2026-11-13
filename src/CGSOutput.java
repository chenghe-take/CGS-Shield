import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** One mapped Connected Graph Sequence per line; comments keep support and start times explicit. */
public final class CGSOutput {
    private CGSOutput() { }

    public static void write(Path output, CGSData data, CGSParameters parameters,
            List<CGSResult> results) throws IOException {
        Path normalized = output.toAbsolutePath().normalize();
        Path input = parameters.inputPath().toAbsolutePath().normalize();
        if (normalized.startsWith(input))
            throw new IOException("Output must be outside the input dataset directory");
        Path parent = normalized.getParent();
        if (parent != null) Files.createDirectories(parent);
        try (BufferedWriter writer = Files.newBufferedWriter(normalized, StandardCharsets.UTF_8)) {
            writer.write("# CGSMiner: connected graph sequences of concrete attributed vertices\n");
            writer.write("# Trends: + and - only; empty-trend vertices are removed before connectivity tests\n");
            writer.write("# Attributes used: " + data.attributeNames.length + " (first mapped columns)\n");
            writer.write("# minsup=" + parameters.minsup + " minvol=" + parameters.minvol
                    + " mincom=" + parameters.mincom + " discretizationThreshold="
                    + parameters.discretizationThreshold + "\n");
            writer.write("# Tt denotes the trend from raw Tt to T(t+1), on the topology of raw Tt\n");
            writer.write("# Each fragment has a connected common-edge witness across its reported starts\n");
            if (data.mappingAliases > 0)
                writer.write("# Duplicate vertex mappings=" + data.mappingAliases + "; first name retained\n");
            if (data.skippedTopologyRows > 0 || data.skippedTopologyNeighbors > 0)
                writer.write("# Topology induced on attributed vertices; skipped rows=" + data.skippedTopologyRows
                        + ", neighbor references=" + data.skippedTopologyNeighbors + "\n");
            if (data.ignoredTopologySnapshots > 0)
                writer.write("# Extra topology snapshots beyond the attribute timeline ignored=" + data.ignoredTopologySnapshots + "\n");
            writer.write("# Pattern count: " + results.size() + "\n");
            int number = 0;
            for (CGSResult result : results) {
                writer.write("# CGS " + (++number) + " support=" + result.support() + " starts="
                        + displayTimes(result.startTimes()) + " volume=" + result.sequence.volume()
                        + " common=" + result.sequence.commonVolume() + "\n");
                writer.write(result.sequence.display(data));
                writer.newLine();
            }
        }
    }

    static String displayTimes(BitSet times) {
        StringJoiner text = new StringJoiner(", ", "{", "}");
        for (int t = times.nextSetBit(0); t >= 0; t = times.nextSetBit(t + 1)) text.add("T" + t);
        return text.toString();
    }
}
