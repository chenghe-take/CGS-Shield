import java.util.*;

/** Immutable active graph with +/- trend itemsets and undirected adjacency. */
public final class CGSGraph {
    final BitSet vertices;
    final BitSet[] labels;
    final BitSet[] neighbors;

    CGSGraph(BitSet vertices, BitSet[] labels, BitSet[] neighbors) {
        this.vertices = (BitSet) vertices.clone();
        this.labels = new BitSet[labels.length];
        this.neighbors = new BitSet[labels.length];
        for (int v = vertices.nextSetBit(0); v >= 0; v = vertices.nextSetBit(v + 1)) {
            if (labels[v] == null || labels[v].isEmpty())
                throw new IllegalArgumentException("Active vertices must have a nonempty trend set");
            this.labels[v] = (BitSet) labels[v].clone();
            this.neighbors[v] = neighbors[v] == null ? new BitSet() : (BitSet) neighbors[v].clone();
            this.neighbors[v].and(vertices);
            this.neighbors[v].clear(v);
        }
    }

    CGSGraph intersect(CGSGraph other) {
        BitSet active = (BitSet) vertices.clone();
        active.and(other.vertices);
        BitSet[] commonLabels = new BitSet[labels.length];
        BitSet[] commonEdges = new BitSet[labels.length];
        for (int v = active.nextSetBit(0); v >= 0; v = active.nextSetBit(v + 1)) {
            commonLabels[v] = (BitSet) labels[v].clone();
            commonLabels[v].and(other.labels[v]);
            if (commonLabels[v].isEmpty()) { active.clear(v); continue; }
            commonEdges[v] = (BitSet) neighbors[v].clone();
            commonEdges[v].and(other.neighbors[v]);
        }
        return new CGSGraph(active, commonLabels, commonEdges);
    }

    List<BitSet> components(int minimumVolume) {
        List<BitSet> result = new ArrayList<>();
        BitSet unseen = (BitSet) vertices.clone();
        int[] queue = new int[vertices.cardinality()];
        while (!unseen.isEmpty()) {
            int first = unseen.nextSetBit(0), head = 0, tail = 0;
            BitSet component = new BitSet();
            unseen.clear(first);
            component.set(first);
            queue[tail++] = first;
            while (head < tail) {
                int v = queue[head++];
                for (int u = neighbors[v].nextSetBit(0); u >= 0; u = neighbors[v].nextSetBit(u + 1)) {
                    if (!unseen.get(u)) continue;
                    unseen.clear(u);
                    component.set(u);
                    queue[tail++] = u;
                }
            }
            if (component.cardinality() >= minimumVolume) result.add(component);
        }
        return result;
    }

    /** Small components cannot grow after further intersections. */
    CGSGraph pruneSmallComponents(int minimumVolume) {
        BitSet retained = new BitSet();
        for (BitSet component : components(minimumVolume)) retained.or(component);
        return new CGSGraph(retained, labels, neighbors);
    }
}
