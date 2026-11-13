import java.util.*;

/** Anonymous structure and private source evidence. Only display(), starts and support are released. */
public final class CGSShieldPattern {
    static final class Role {
        final String key;
        final String display;
        final int[] items;
        Role(String key, String display, int[] items) { this.key = key; this.display = display; this.items = items; }
    }

    static final class Fragment {
        final String key;
        final Role[] roles;
        final double logSymmetry;
        Fragment(List<Role> values) {
            roles = values.toArray(new Role[0]);
            List<String> keys = new ArrayList<>();
            double symmetry = 0;
            for (int i = 0; i < roles.length;) {
                int end = i + 1;
                while (end < roles.length && roles[end].key.equals(roles[i].key)) end++;
                symmetry += CGSShieldMatching.logFactorial(end-i);
                i = end;
            }
            for (Role role : roles) keys.add(role.key);
            key = encode(keys);
            logSymmetry = symmetry;
        }
    }

    static final class Source {
        final int[] vertices;
        final BitSet starts = new BitSet();
        private final int hash;
        Source(int[] vertices) { this.vertices = vertices.clone(); hash = Arrays.hashCode(vertices); }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            return other instanceof Source && Arrays.equals(vertices, ((Source) other).vertices);
        }
    }

    final String key;
    final Fragment[] fragments;
    final BitSet starts = new BitSet();
    final Map<Source, Source> sources = new LinkedHashMap<>();
    final int roleCount;
    final double logSymmetry;
    final long stableHash;
    private int[] startArray;

    CGSShieldPattern(List<Fragment> values) {
        fragments = values.toArray(new Fragment[0]);
        List<String> keys = new ArrayList<>();
        int total = 0;
        double symmetry = 0;
        for (Fragment fragment : fragments) {
            keys.add(fragment.key);
            total += fragment.roles.length;
            symmetry += fragment.logSymmetry;
        }
        key = encode(keys);
        roleCount = total;
        logSymmetry = symmetry;
        stableHash = CGSShieldMatching.hash(key);
    }

    void addSource(int[] vertices, BitSet times) {
        Source source = new Source(vertices);
        Source known = sources.get(source);
        if (known == null) { sources.put(source, source); known = source; }
        known.starts.or(times);
        starts.or(times);
    }

    int[] startArray() {
        if (startArray == null) startArray = starts.stream().toArray();
        return startArray;
    }

    double logTruthCount(int start) {
        int count = 0;
        for (Source source : sources.values()) if (source.starts.get(start)) count++;
        if (count == 0) throw new IllegalStateException("No private source mapping for a released start");
        // Equal anonymous roles have no observable ordering: include every within-group permutation.
        return Math.log(count) + logSymmetry;
    }

    String display() {
        StringBuilder text = new StringBuilder("{");
        for (Fragment fragment : fragments) {
            text.append('(');
            for (int r = 0; r < fragment.roles.length; r++) {
                if (r > 0) text.append("| ");
                text.append(fragment.roles[r].display);
            }
            text.append(')');
        }
        return text.append('}').toString();
    }

    static String encode(Collection<String> values) {
        StringBuilder text = new StringBuilder().append(values.size()).append('[');
        for (String value : values) text.append(value.length()).append(':').append(value);
        return text.append(']').toString();
    }
}
