import java.util.BitSet;

/** A Connected Graph Sequence and its maximal jointly compatible set of start times. */
public final class CGSResult {
    public final CGSSequence sequence;
    private final BitSet starts;

    CGSResult(CGSSequence sequence, BitSet starts) {
        this.sequence = sequence;
        this.starts = (BitSet) starts.clone();
    }

    public int support() { return starts.cardinality(); }
    public BitSet startTimes() { return (BitSet) starts.clone(); }
}
