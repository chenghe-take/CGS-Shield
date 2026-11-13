import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Parse original named CGSMiner records; recover only the mappings actually present in each source. */
public final class CGSShieldInput {
    static final class Metadata {
        float threshold = Float.NaN;
        int attributes;
    }
    final List<CGSShieldPattern> patterns;
    final int sourceRecords;
    final CGSData data;

    private CGSShieldInput(CGSData data, List<CGSShieldPattern> patterns, int count) {
        this.data = data; this.patterns = patterns; sourceRecords = count;
    }

    static Metadata metadata(Path input) throws IOException {
        Metadata metadata = new Metadata();
        Pattern threshold = Pattern.compile("discretizationThreshold=([^\\s]+)");
        Pattern attributes = Pattern.compile("Attributes used:\\s*(\\d+)");
        try (BufferedReader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = clean(line);
                if (line.startsWith("{")) break;
                Matcher a = attributes.matcher(line), d = threshold.matcher(line);
                try {
                    if (a.find()) metadata.attributes = Integer.parseInt(a.group(1));
                    if (d.find()) metadata.threshold = Float.parseFloat(d.group(1));
                } catch (NumberFormatException ex) { throw new IOException("Invalid CGSMiner output metadata", ex); }
            }
        }
        return metadata;
    }

    public static CGSShieldInput load(Path file, CGSData data) throws IOException {
        Map<String, Integer> attributes = new HashMap<>();
        for (int a = 0; a < data.attributeNames.length; a++) {
            if (attributes.put(data.attributeNames[a], a) != null)
                throw new IOException("Ambiguous duplicate attribute name: " + data.attributeNames[a]);
        }
        Map<String, Integer> names = new HashMap<>();
        Set<String> ambiguous = new HashSet<>();
        for (int v = 0; v < data.vertexNames.length; v++)
            if (names.put(data.vertexNames[v], v) != null) ambiguous.add(data.vertexNames[v]);
        Map<String, CGSShieldPattern.Role> roles = new HashMap<>();
        Map<String, CGSShieldPattern.Fragment> fragments = new HashMap<>();
        Map<String, CGSShieldPattern> patterns = new TreeMap<>();
        Pattern header = Pattern.compile("^# CGS\\s+\\d+\\s+support=(\\d+)\\s+starts=\\{([^}]*)\\}.*$");
        BitSet starts = null;
        int count = 0;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            long number = 0;
            while ((line = reader.readLine()) != null) {
                number++;
                line = clean(line);
                if (line.isEmpty()) continue;
                Matcher match = header.matcher(line);
                if (match.matches()) {
                    if (starts != null) throw new IOException("Source header without a pattern at line " + number);
                    starts = parseTimes(match.group(2));
                    if (starts.isEmpty() || starts.cardinality() != Integer.parseInt(match.group(1)))
                        throw new IOException("Source support differs from its start set at line " + number);
                    continue;
                }
                if (line.startsWith("#")) continue;
                if (starts == null) throw new IOException("Missing source support/start metadata at line " + number);
                try {
                    List<CGSShieldPattern.Fragment> sequence = new ArrayList<>();
                    List<Integer> mapping = new ArrayList<>();
                    List<String> rawFragments = splitPattern(line);
                    int offset = 0;
                    for (String rawFragment : rawFragments) {
                        List<NamedRole> values = new ArrayList<>();
                        Set<Integer> used = new HashSet<>();
                        for (String rawVertex : rawFragment.split("\\|", -1)) {
                            int colon = rawVertex.lastIndexOf(':');
                            if (colon < 1) throw new IllegalArgumentException("Source identities are required for complete-mapping hit and RSR provenance");
                            String name = rawVertex.substring(0, colon).trim();
                            if (ambiguous.contains(name)) throw new IllegalArgumentException("Ambiguous source name; an original vertex ID is required: " + name);
                            Integer vertex = names.get(name);
                            if (vertex == null) throw new IllegalArgumentException("Source vertex not in the configured dataset: " + name);
                            if (!used.add(vertex)) throw new IllegalArgumentException("A source fragment repeats a real vertex");
                            SortedSet<String> items = new TreeSet<>();
                            for (String item : rawVertex.substring(colon+1).split(",", -1)) items.add(item.trim());
                            int[] codes = new int[items.size()];
                            int next = 0;
                            for (String item : items) {
                                if (item.length() < 2 || !(item.endsWith("+") || item.endsWith("-")))
                                    throw new IllegalArgumentException("Only +/- source trend items are accepted");
                                Integer attribute = attributes.get(item.substring(0, item.length()-1));
                                if (attribute == null) throw new IllegalArgumentException("Unknown or excluded attribute: " + item);
                                codes[next++] = 2 * attribute + (item.endsWith("+") ? 1 : 0);
                            }
                            String roleKey = CGSShieldPattern.encode(items);
                            CGSShieldPattern.Role role = roles.get(roleKey);
                            if (role == null) { role = new CGSShieldPattern.Role(roleKey, String.join(", ", items), codes); roles.put(roleKey, role); }
                            for (int start = starts.nextSetBit(0); start >= 0; start = starts.nextSetBit(start+1)) {
                                if (start + offset >= data.graphs.size()) throw new IllegalArgumentException("Source extends beyond the trend timeline");
                                CGSGraph graph = data.graphs.get(start+offset);
                                if (!graph.vertices.get(vertex)) throw new IllegalArgumentException("Source vertex has no active trends at its stated time");
                                for (int item : codes) if (!graph.labels[vertex].get(item))
                                    throw new IllegalArgumentException("Source trend is false in the configured data; check dataset, threshold and attribute prefix");
                            }
                            values.add(new NamedRole(role, vertex));
                        }
                        values.sort(Comparator.comparing((NamedRole value) -> value.role.key).thenComparingInt(value -> value.vertex));
                        List<CGSShieldPattern.Role> labels = new ArrayList<>();
                        for (NamedRole value : values) { labels.add(value.role); mapping.add(value.vertex); }
                        CGSShieldPattern.Fragment fragment = new CGSShieldPattern.Fragment(labels);
                        CGSShieldPattern.Fragment known = fragments.get(fragment.key);
                        if (known == null) { fragments.put(fragment.key, fragment); known = fragment; }
                        sequence.add(known);
                        offset++;
                    }
                    CGSShieldPattern candidate = new CGSShieldPattern(sequence);
                    CGSShieldPattern known = patterns.get(candidate.key);
                    if (known == null) { patterns.put(candidate.key, candidate); known = candidate; }
                    known.addSource(mapping.stream().mapToInt(Integer::intValue).toArray(), starts);
                    starts = null;
                    count++;
                } catch (IllegalArgumentException ex) { throw new IOException("Invalid source pattern at " + file + ":" + number + ": " + ex.getMessage(), ex); }
            }
        }
        if (starts != null) throw new IOException("The input ends with a source header but no pattern");
        return new CGSShieldInput(data, new ArrayList<>(patterns.values()), count);
    }

    static List<String> splitPattern(String text) {
        text = text.trim();
        if (!text.startsWith("{") || !text.endsWith("}")) throw new IllegalArgumentException("Expected a braced pattern");
        List<String> result = new ArrayList<>();
        int end = text.length()-1, position = 1;
        while (position < end) {
            while (position < end && Character.isWhitespace(text.charAt(position))) position++;
            if (position == end) break;
            if (text.charAt(position++) != '(') throw new IllegalArgumentException("Expected a fragment");
            int begin = position, depth = 1;
            while (position < end && depth > 0) {
                char value = text.charAt(position++);
                if (value == '(') depth++;
                else if (value == ')') depth--;
            }
            if (depth != 0) throw new IllegalArgumentException("Unbalanced fragment");
            result.add(text.substring(begin, position-1));
        }
        if (result.isEmpty()) throw new IllegalArgumentException("An anonymous pattern cannot be empty");
        return result;
    }

    private static BitSet parseTimes(String text) throws IOException {
        BitSet result = new BitSet();
        for (String token : text.split(",")) {
            token = token.trim();
            if (!token.matches("T\\d+")) throw new IOException("Invalid source start time: " + token);
            try { result.set(Integer.parseInt(token.substring(1))); }
            catch (NumberFormatException ex) { throw new IOException("Invalid start time", ex); }
        }
        return result;
    }
    private static String clean(String line) { return line.replace("\uFEFF", "").trim(); }
    private static final class NamedRole {
        final CGSShieldPattern.Role role;
        final int vertex;
        NamedRole(CGSShieldPattern.Role role, int vertex) { this.role = role; this.vertex = vertex; }
    }
}
