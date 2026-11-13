import java.util.*;

/** Deterministic full-data risk computation; no background-knowledge simulation occurs here. */
public final class CGSShieldRisk {
    static final class Scores {
        final double r1, r2, r2Lower, r2Upper;
        final int kMin;
        final boolean estimated;
        Scores(double r1, double r2, double lower, double upper, int kMin, boolean estimated) {
            this.r1=r1; this.r2=r2; r2Lower=lower; r2Upper=upper; this.kMin=kMin; this.estimated=estimated;
        }
        boolean publish(CGSShieldParameters parameters, boolean shield) {
            return shield ? r1 <= parameters.r1Threshold && r2 <= parameters.r2Threshold
                    : kMin >= parameters.kMin;
        }
    }
    final CGSShieldTrendIndex index;
    final CGSShieldMatching matching;
    private final Map<CGSShieldPattern.Fragment,CGSShieldMatching.Count[]> cache = new IdentityHashMap<>();

    CGSShieldRisk(CGSShieldTrendIndex index, CGSShieldMatching matching) { this.index=index; this.matching=matching; }

    Scores compute(CGSShieldPattern pattern) {
        if (index.population <= 1) throw new IllegalArgumentException("Risk normalization requires more than one active real identity");
        long positions = 0;
        int minimum = Integer.MAX_VALUE;
        double entropy = 0, logCount = 0, lowerCount = 0, upperCount = 0;
        boolean estimated = false;
        for (int start : pattern.startArray()) {
            for (int j = 0; j < pattern.fragments.length; j++) {
                CGSShieldPattern.Fragment fragment = pattern.fragments[j];
                for (CGSShieldPattern.Role role : fragment.roles) {
                    int count = index.candidates(role,start+j).cardinality();
                    if (count == 0) throw new IllegalStateException("A source-supported role has no true candidate");
                    positions++;
                    minimum = Math.min(minimum,count);
                    entropy += Math.log(count);
                }
                CGSShieldMatching.Count count = count(fragment,start+j);
                logCount += count.logCount;
                lowerCount += count.logLower;
                upperCount += count.logUpper;
                estimated |= !count.exact;
            }
        }
        double denominator = positions * Math.log(index.population);
        return new Scores(clamp(1-entropy/denominator), clamp(1-logCount/denominator),
                clamp(1-upperCount/denominator), clamp(1-lowerCount/denominator), minimum, estimated);
    }

    CGSShieldMatching.Count count(CGSShieldPattern.Fragment fragment, int time) {
        CGSShieldMatching.Count[] times = cache.computeIfAbsent(fragment, f -> new CGSShieldMatching.Count[index.data.graphs.size()]);
        if (times[time] == null) {
            BitSet[] candidates = new BitSet[fragment.roles.length];
            for (int r = 0; r < candidates.length; r++) candidates[r] = index.candidates(fragment.roles[r],time);
            times[time] = matching.count(candidates, CGSShieldMatching.mix(CGSShieldMatching.hash(fragment.key),time));
        }
        return times[time];
    }
    static double clamp(double value) { return Math.max(0,Math.min(1,value)); }
}
