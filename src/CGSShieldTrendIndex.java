import java.util.*;

/** Full-data candidate index and separately sampled attacker knowledge. Topology is never matched. */
public final class CGSShieldTrendIndex {
    final CGSData data;
    final int population;
    private final BitSet[][] inverted;
    private final Map<CGSShieldPattern.Role, BitSet[]> truthCache = new IdentityHashMap<>();

    CGSShieldTrendIndex(CGSData data) {
        this.data = data;
        BitSet union = new BitSet();
        inverted = new BitSet[data.graphs.size()][2 * data.attributeNames.length];
        for (int t = 0; t < inverted.length; t++) {
            for (int a = 0; a < inverted[t].length; a++) inverted[t][a] = new BitSet();
            CGSGraph graph = data.graphs.get(t);
            union.or(graph.vertices);
            for (int v = graph.vertices.nextSetBit(0); v >= 0; v = graph.vertices.nextSetBit(v+1))
                for (int item = graph.labels[v].nextSetBit(0); item >= 0; item = graph.labels[v].nextSetBit(item+1))
                    inverted[t][item].set(v);
        }
        population = union.cardinality();
    }

    BitSet candidates(CGSShieldPattern.Role role, int time) {
        BitSet[] times = truthCache.computeIfAbsent(role, r -> new BitSet[data.graphs.size()]);
        if (times[time] == null) {
            BitSet result = (BitSet) data.graphs.get(time).vertices.clone();
            for (int item : role.items) result.and(inverted[time][item]);
            times[time] = result;
        }
        return times[time];
    }

    Background sample(long seed) {
        int attributes = data.attributeNames.length;
        int known = (attributes + 1) / 2;
        BitSet[][] contradictions = new BitSet[inverted.length][2 * data.attributeNames.length];
        for (int t = 0; t < contradictions.length; t++) {
            for (int item = 0; item < contradictions[t].length; item++) contradictions[t][item] = new BitSet();
            CGSGraph graph = data.graphs.get(t);
            for (int v = graph.vertices.nextSetBit(0); v >= 0; v = graph.vertices.nextSetBit(v+1)) {
                // A partial Fisher-Yates permutation samples ceil(d/2) different attribute names.
                // Per-position seeds keep both publication policies on identical background knowledge.
                Random random = new Random(CGSShieldMatching.mix(seed, ((long) t << 32) ^ v));
                int[] order = new int[attributes];
                for (int a = 0; a < attributes; a++) order[a] = a;
                for (int j = 0; j < known; j++) {
                    int selected = j + random.nextInt(attributes-j);
                    int a = order[selected]; order[selected] = order[j]; order[j] = a;
                    // An observed absent +/- trend contradicts both signs for that sampled attribute.
                    if (!graph.labels[v].get(2*a)) contradictions[t][2*a].set(v);
                    if (!graph.labels[v].get(2*a+1)) contradictions[t][2*a+1].set(v);
                }
            }
        }
        return new Background(contradictions);
    }

    final class Background {
        private final BitSet[][] contradictions;
        private final Map<CGSShieldPattern.Role, BitSet[]> cache = new IdentityHashMap<>();
        Background(BitSet[][] contradictions) { this.contradictions = contradictions; }
        BitSet candidates(CGSShieldPattern.Role role, int time) {
            BitSet[] times = cache.computeIfAbsent(role, r -> new BitSet[data.graphs.size()]);
            if (times[time] == null) {
                BitSet possible = (BitSet) data.graphs.get(time).vertices.clone();
                for (int item : role.items) possible.andNot(contradictions[time][item]);
                times[time] = possible;
            }
            return times[time];
        }
    }
}
