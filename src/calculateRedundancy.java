import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Count anonymous pattern multiplicities without changing the original mining output. */
public class calculateRedundancy {
    public static void main(String[] args) throws IOException {
        // Change this path to analyze another output file; one optional CLI argument overrides it.
        String inputPath = "output.txt";
        if (args.length > 1) throw new IllegalArgumentException("Usage: calculateRedundancy [inputFile]");
        if (args.length == 1) inputPath = args[0];
        Path input = CGSPaths.resolve(inputPath);
        Statistics statistics = analyze(input);

        System.out.println("=== 匿名模式重复统计 ===");
        System.out.println("输入文件: " + input);
        System.out.println("读取的模式记录总数: " + statistics.totalRecords);
        System.out.println("不同匿名模式总数: " + statistics.distinctPatterns);
        System.out.println("出现 1 次的模式数量: " + statistics.once);
        System.out.println("出现 2 次的模式数量: " + statistics.twice);
        System.out.println("出现 3 次的模式数量: " + statistics.threeTimes);
        System.out.println("出现 4 次及以上的模式数量: " + statistics.fourOrMore);
        System.out.println("重复记录数量（除每类首次外）: " + statistics.extraRecords);
        System.out.println("各档统计的是不同匿名模式的数量；忽略姓名、拓扑和片段内的顶点顺序，保留片段先后顺序。");
    }

    public static Statistics analyze(Path input) throws IOException {
        Map<String, Long> occurrences = new HashMap<>();
        long total = 0;
        try (BufferedReader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8)) {
            String line;
            long lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (lineNumber == 1 && line.startsWith("\uFEFF")) line = line.substring(1);
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String key;
                try { key = canonicalPattern(line); }
                catch (IllegalArgumentException ex) {
                    throw new IOException("Invalid pattern at " + input + ":" + lineNumber + ": " + ex.getMessage(), ex);
                }
                occurrences.merge(key, 1L, Long::sum);
                total++;
            }
        }
        long[] groups = new long[4];
        for (long count : occurrences.values()) groups[(int) Math.min(count, 4) - 1]++;
        return new Statistics(total, occurrences.size(), groups);
    }

    /** Ordered fragments of unordered vertex multisets; every vertex contains a trend set. */
    static String canonicalPattern(String text) {
        text = text.trim();
        if (!text.startsWith("{") || !text.endsWith("}"))
            throw new IllegalArgumentException("Expected {(fragment)(fragment)...}");
        String body = text.substring(1, text.length() - 1);
        List<String> fragments = new ArrayList<>();
        int position = 0;
        while (position < body.length()) {
            while (position < body.length() && Character.isWhitespace(body.charAt(position))) position++;
            if (position == body.length()) break;
            if (body.charAt(position) != '(') throw new IllegalArgumentException("Expected a fragment opening parenthesis");
            int begin = ++position, depth = 1;
            while (position < body.length() && depth > 0) {
                char value = body.charAt(position++);
                if (value == '(') depth++;
                else if (value == ')') depth--;
            }
            if (depth != 0) throw new IllegalArgumentException("Unbalanced fragment parentheses");
            fragments.add(canonicalFragment(body.substring(begin, position - 1)));
        }
        if (fragments.isEmpty()) throw new IllegalArgumentException("A pattern needs at least one fragment");
        // Fragment order is meaningful and must not be sorted.
        return encode(fragments);
    }

    private static String canonicalFragment(String text) {
        List<String> vertices = new ArrayList<>();
        for (String vertex : text.split("\\|", -1)) {
            // Named, anonymous, and mixed-format vertices are accepted.
            int separator = vertex.lastIndexOf(':');
            String attributes = vertex.substring(separator + 1).trim();
            if (attributes.isEmpty()) throw new IllegalArgumentException("Empty vertex trend set");
            SortedSet<String> items = new TreeSet<>();
            for (String token : attributes.split(",", -1)) {
                String item = token.trim();
                if (item.length() < 2 || !(item.endsWith("+") || item.endsWith("-")))
                    throw new IllegalArgumentException("Expected comma-separated attribute+ or attribute- items");
                items.add(item);
            }
            vertices.add(encode(items));
        }
        // Sorting a list preserves repeated vertex labels, unlike converting it into a set.
        Collections.sort(vertices);
        return encode(vertices);
    }

    /** Length-prefixed fields prevent collisions between vertices, attributes, and fragments. */
    private static String encode(Collection<String> values) {
        StringBuilder result = new StringBuilder().append(values.size()).append('[');
        for (String value : values) result.append(value.length()).append(':').append(value);
        return result.append(']').toString();
    }

    public static final class Statistics {
        public final long totalRecords, distinctPatterns, once, twice, threeTimes, fourOrMore, extraRecords;

        private Statistics(long total, long distinct, long[] groups) {
            totalRecords = total;
            distinctPatterns = distinct;
            once = groups[0];
            twice = groups[1];
            threeTimes = groups[2];
            fourOrMore = groups[3];
            extraRecords = total - distinct;
        }
    }
}
