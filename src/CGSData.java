import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Read mapped numeric graphs and apply the same absolute-difference rule as DP-FGSMiner. */
public final class CGSData {
    public final int[] vertexIds;
    public final String[] vertexNames;
    public final String[] attributeNames;
    public final List<CGSGraph> graphs;
    public final int rawTimeCount;
    public final int mappingAliases;
    public final int skippedTopologyRows;
    public final int skippedTopologyNeighbors;
    public final int ignoredTopologySnapshots;

    CGSData(int[] ids, String[] names, String[] attributes, List<CGSGraph> graphs, int rawTimes) {
        this(ids, names, attributes, graphs, rawTimes, new int[4]);
    }

    private CGSData(int[] ids, String[] names, String[] attributes, List<CGSGraph> graphs,
            int rawTimes, int[] diagnostics) {
        this.vertexIds = ids.clone();
        this.vertexNames = names.clone();
        this.attributeNames = attributes.clone();
        this.graphs = Collections.unmodifiableList(new ArrayList<>(graphs));
        this.rawTimeCount = rawTimes;
        this.mappingAliases = diagnostics[0];
        this.skippedTopologyRows = diagnostics[1];
        this.skippedTopologyNeighbors = diagnostics[2];
        this.ignoredTopologySnapshots = diagnostics[3];
    }

    public static CGSData load(Path directory, double threshold) throws IOException {
        return load(directory, threshold, -1);
    }

    public static CGSData load(Path directory, double threshold, int attributeCount) throws IOException {
        if (attributeCount != -1 && attributeCount < 1)
            throw new IllegalArgumentException("attributeCount must be -1 (dataset default) or a positive column count");
        List<String> mappings = Files.readAllLines(directory.resolve("attributes_mapping.txt"), StandardCharsets.UTF_8);
        int columns = attributeCount;
        if (columns == -1) {
            Path name = directory.toAbsolutePath().normalize().getFileName();
            String dataset = name == null ? "" : name.toString();
            columns = dataset.equalsIgnoreCase("DBLP") ? 43
                    : dataset.equalsIgnoreCase("USFlight") ? 8 : mappings.size();
        }
        if (columns < 1 || columns > mappings.size())
            throw new IOException("Selected attribute count " + columns + " exceeds the available mapping or is empty");
        List<String> attributeNames = new ArrayList<>();
        for (int a = 0; a < columns; a++) {
            String name = clean(mappings.get(a));
            if (name.isEmpty()) throw new IOException("Empty attribute mapping entry");
            attributeNames.add(name);
        }
        if (attributeNames.isEmpty()) throw new IOException("No attributes in attributes_mapping.txt");
        int[] diagnostics = new int[4];
        Map<Integer, String> mappedNames = readVertices(directory.resolve("vertices_mapping.txt"), diagnostics);
        List<Map<Integer, double[]>> raw = readAttributes(directory.resolve("attributes.txt"), attributeNames.size());
        if (raw.size() < 2) throw new IOException("At least two raw snapshots are required");
        TreeSet<Integer> ids = new TreeSet<>();
        for (Map<Integer, double[]> snapshot : raw) ids.addAll(snapshot.keySet());
        int[] vertexIds = new int[ids.size()];
        String[] names = new String[ids.size()];
        Map<Integer, Integer> dense = new HashMap<>();
        int index = 0;
        for (int id : ids) {
            if (!mappedNames.containsKey(id)) throw new IOException("Missing vertex mapping for ID " + id);
            vertexIds[index] = id;
            names[index] = mappedNames.get(id);
            dense.put(id, index++);
        }
        List<BitSet[]> topology = readTopology(directory.resolve("graph.txt"), raw, dense, diagnostics);
        List<CGSGraph> trends = new ArrayList<>();
        for (int t = 0; t + 1 < raw.size(); t++) {
            BitSet active = new BitSet();
            BitSet[] labels = new BitSet[ids.size()];
            for (Map.Entry<Integer, double[]> entry : raw.get(t).entrySet()) {
                double[] next = raw.get(t + 1).get(entry.getKey());
                if (next == null) continue;
                BitSet items = new BitSet(2 * attributeNames.size());
                for (int a = 0; a < attributeNames.size(); a++) {
                    int sign = trend(entry.getValue()[a], next[a], threshold);
                    if (sign != 0) items.set(2 * a + (sign > 0 ? 1 : 0));
                }
                if (items.isEmpty()) continue;
                int v = dense.get(entry.getKey());
                labels[v] = items;
                active.set(v);
            }
            // Trend t describes raw Tt -> T(t+1), on the topology of raw Tt.
            trends.add(new CGSGraph(active, labels, topology.get(t)));
        }
        return new CGSData(vertexIds, names, attributeNames.toArray(new String[0]), trends, raw.size(), diagnostics);
    }

    public static int trend(double first, double second, double threshold) {
        if (second - first >= threshold) return 1;
        if (second - first <= -threshold) return -1;
        return 0;
    }

    private static Map<Integer, String> readVertices(Path file, int[] diagnostics) throws IOException {
        Map<Integer, String> result = new HashMap<>();
        int number = 0;
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            number++;
            line = clean(line);
            if (line.isEmpty()) continue;
            int comma = line.indexOf(',');
            if (comma < 1) throw new IOException("Expected id,name at " + file + ":" + number);
            int id = integer(line.substring(0, comma).trim(), file, number);
            String name = line.substring(comma + 1).trim();
            if (name.startsWith("\"") && name.endsWith("\"") && name.length() >= 2)
                name = name.substring(1, name.length() - 1).replace("\"\"", "\"");
            if (name.isEmpty()) throw new IOException("Empty vertex mapping at " + file + ":" + number);
            // USFlight includes multiple historical names for some airport IDs.
            if (result.putIfAbsent(id, name) != null) diagnostics[0]++;
        }
        return result;
    }

    private static List<Map<Integer, double[]>> readAttributes(Path file, int columns) throws IOException {
        List<Map<Integer, double[]>> result = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int number = 0;
            Map<Integer, double[]> snapshot = null;
            while ((line = reader.readLine()) != null) {
                number++;
                line = clean(line);
                if (line.isEmpty()) continue;
                if (line.startsWith("T")) {
                    requireTime(line, result.size(), file, number);
                    snapshot = new TreeMap<>();
                    result.add(snapshot);
                    continue;
                }
                if (snapshot == null) throw new IOException("Missing timestamp at " + file + ":" + number);
                String[] fields = line.split("\\s+");
                if (fields.length < columns + 1)
                    throw new IOException("Too few selected attribute columns at " + file + ":" + number);
                int id = integer(fields[0], file, number);
                double[] values = new double[columns];
                // Trailing columns are deliberately not parsed or used for vertex activation.
                try {
                    for (int a = 0; a < columns; a++) {
                        values[a] = Double.parseDouble(fields[a + 1]);
                        if (!Double.isFinite(values[a])) throw new NumberFormatException("Nonfinite value");
                    }
                } catch (NumberFormatException ex) {
                    throw new IOException("Invalid numeric attribute at " + file + ":" + number, ex);
                }
                if (snapshot.put(id, values) != null)
                    throw new IOException("Duplicate vertex at " + file + ":" + number);
            }
        }
        return result;
    }

    private static List<BitSet[]> readTopology(Path file, List<Map<Integer, double[]>> raw,
            Map<Integer, Integer> dense, int[] diagnostics) throws IOException {
        List<BitSet[]> result = new ArrayList<>();
        BitSet[] snapshot = null;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int number = 0;
            int seenTimes = 0;
            while ((line = reader.readLine()) != null) {
                number++;
                line = clean(line);
                if (line.isEmpty()) continue;
                if (line.startsWith("T")) {
                    requireTime(line, seenTimes++, file, number);
                    if (seenTimes > raw.size()) {
                        diagnostics[3]++;
                        snapshot = null;
                        continue;
                    }
                    snapshot = new BitSet[dense.size()];
                    for (int v = 0; v < snapshot.length; v++) snapshot[v] = new BitSet();
                    result.add(snapshot);
                    continue;
                }
                if (snapshot == null) {
                    if (seenTimes > raw.size()) continue;
                    throw new IOException("Missing topology timestamp");
                }
                String[] fields = line.split("\\s+");
                int id = integer(fields[0], file, number);
                // Some supplied synthetic datasets use a topology larger than their attribute table.
                // Mining is on the induced graph of vertices with numeric records at this time.
                if (!raw.get(result.size() - 1).containsKey(id)) { diagnostics[1]++; continue; }
                int v = dense.get(id);
                for (int i = 1; i < fields.length; i++) {
                    int neighborId = integer(fields[i], file, number);
                    if (!raw.get(result.size() - 1).containsKey(neighborId)) { diagnostics[2]++; continue; }
                    int u = dense.get(neighborId);
                    if (u == v) continue;
                    snapshot[v].set(u);
                    snapshot[u].set(v);
                }
            }
        }
        if (result.size() != raw.size()) throw new IOException("Topology and attribute snapshot counts differ");
        return result;
    }

    private static void requireTime(String text, int expected, Path file, int line) throws IOException {
        if (integer(text.substring(1).trim(), file, line) != expected)
            throw new IOException("Expected consecutive T0, T1, ... at " + file + ":" + line);
    }

    private static int integer(String text, Path file, int line) throws IOException {
        try { return Integer.parseInt(text); }
        catch (NumberFormatException ex) { throw new IOException("Invalid integer at " + file + ":" + line, ex); }
    }

    private static String clean(String line) { return line.replace("\uFEFF", "").trim(); }
}
