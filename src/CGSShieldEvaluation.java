import java.util.*;

/** Coupled random-knowledge evaluation with fixed pre-suppression denominators. */
public final class CGSShieldEvaluation {
    static final class Metrics {
        final double rsr, rsrSE, prr, conditionalRSR;
        final long hits;
        Metrics(long hits, int trials, int total, int released, double sumR2) {
            this.hits=hits;
            prr = total == 0 ? Double.NaN : (double) released/total;
            rsr = trials == 0 || total == 0 ? Double.NaN : (double) hits/trials/total;
            conditionalRSR = trials == 0 || released == 0 ? Double.NaN : (double) hits/trials/released;
            rsrSE = standardError(rsr, sumR2, trials);
        }
        private static double standardError(double mean, double sumSquares, int trials) {
            if (trials < 2 || !Double.isFinite(mean)) return Double.NaN;
            return Math.sqrt(Math.max(0, sumSquares-trials*mean*mean)/(trials-1)/trials);
        }
    }
    static final class Result {
        final Metrics published, baseline;
        final long estimatedDecisions, boundedDecisions;
        final CGSShieldMatching matching;
        Result(Metrics published, Metrics baseline, long estimated, long bounded, CGSShieldMatching matching) {
            this.published=published; this.baseline=baseline; estimatedDecisions=estimated; boundedDecisions=bounded; this.matching=matching;
        }
    }
    private final CGSShieldTrendIndex index;
    private final CGSShieldParameters parameters;
    private final CGSShieldMatching matching;
    private long estimatedDecisions, boundedDecisions;

    CGSShieldEvaluation(CGSShieldTrendIndex index, CGSShieldParameters parameters) {
        this.index=index; this.parameters=parameters; matching=new CGSShieldMatching(parameters);
    }

    Result evaluate(List<CGSShieldPattern> patterns, boolean[] published) {
        int total = patterns.size(), released = 0;
        for (boolean keep : published) if (keep) released++;
        long hits=0, baseHits=0;
        double rsr2=0, baseRsr2=0;
        for (int trial = 0; trial < parameters.attackSimulations && total > 0; trial++) {
            long backgroundSeed = CGSShieldMatching.mix(parameters.attackSeed, trial ^ 0x42474cL);
            CGSShieldTrendIndex.Background background = index.sample(backgroundSeed);
            Map<CGSShieldPattern.Fragment,CGSShieldMatching.Count[]> counts = new IdentityHashMap<>();
            Map<CGSShieldPattern.Fragment,BitSet[][]> candidates = new IdentityHashMap<>();
            Map<CGSShieldPattern.Fragment,CGSShieldMatching.Bounds[]> bounds = new IdentityHashMap<>();
            int trialHits=0, trialBaseHits=0;
            for (int p = 0; p < patterns.size(); p++) {
                CGSShieldPattern pattern = patterns.get(p);
                Random draw = new Random(CGSShieldMatching.mix(parameters.attackSeed,
                        CGSShieldMatching.mix(pattern.stableHash, trial ^ 0x515253L)));
                int start = pattern.startArray()[draw.nextInt(pattern.startArray().length)];
                double uniform;
                do { uniform=draw.nextDouble(); } while (uniform==0);
                double logDraw = Math.log(uniform), logTruth = pattern.logTruthCount(start);
                double lower=0, upper=0;
                for (int j = 0; j < pattern.fragments.length; j++) {
                    CGSShieldPattern.Fragment fragment=pattern.fragments[j];
                    CGSShieldMatching.Bounds[] times = bounds.computeIfAbsent(fragment, f -> new CGSShieldMatching.Bounds[index.data.graphs.size()]);
                    if (times[start+j] == null) {
                        BitSet[] matrix = candidates(fragment,start+j,background,candidates);
                        CGSShieldMatching.Bounds bound = CGSShieldMatching.quickBounds(matrix);
                        times[start+j] = new CGSShieldMatching.Bounds(Math.max(bound.lower,fragment.logSymmetry),bound.upper);
                    }
                    lower += times[start+j].lower;
                    upper += times[start+j].upper;
                }
                boolean hit;
                if (logDraw >= Math.min(0,logTruth-lower)+1e-10) {
                    hit=false;
                    boundedDecisions++;
                } else if (logDraw < Math.min(0,logTruth-upper)-1e-10) {
                    hit=true;
                    boundedDecisions++;
                } else {
                    double value=0, certifiedLower=0, certifiedUpper=0;
                    boolean exact=true;
                    for (int j = 0; j < pattern.fragments.length; j++) {
                        CGSShieldPattern.Fragment fragment=pattern.fragments[j];
                        CGSShieldMatching.Count[] times = counts.computeIfAbsent(fragment, f -> new CGSShieldMatching.Count[index.data.graphs.size()]);
                        if (times[start+j] == null) times[start+j] = matching.count(candidates(fragment,start+j,background,candidates),
                                CGSShieldMatching.mix(backgroundSeed, CGSShieldMatching.hash(fragment.key) ^ (start+j)));
                        CGSShieldMatching.Count count=times[start+j];
                        value+=count.logCount; certifiedLower+=count.logLower; certifiedUpper+=count.logUpper; exact &= count.exact;
                    }
                    if (logTruth > certifiedUpper+1e-7) throw new IllegalStateException("Source truth count exceeds all attacker-compatible mappings");
                    // Refining certified bounds may settle the draw even if a count estimate was needed elsewhere.
                    if (logDraw >= Math.min(0,logTruth-certifiedLower)+1e-10) { hit=false; boundedDecisions++; }
                    else if (logDraw < Math.min(0,logTruth-certifiedUpper)-1e-10) { hit=true; boundedDecisions++; }
                    else { hit=logDraw < Math.min(0,logTruth-value); if (!exact) estimatedDecisions++; }
                }
                if (hit) { trialBaseHits++; if (published[p]) trialHits++; }
            }
            hits+=trialHits; baseHits+=trialBaseHits;
            rsr2+=square((double)trialHits/total);
            baseRsr2+=square((double)trialBaseHits/total);
        }
        return new Result(new Metrics(hits,parameters.attackSimulations,total,released,rsr2),
                new Metrics(baseHits,parameters.attackSimulations,total,total,baseRsr2),
                estimatedDecisions,boundedDecisions,matching);
    }

    private BitSet[] candidates(CGSShieldPattern.Fragment fragment, int time, CGSShieldTrendIndex.Background background,
            Map<CGSShieldPattern.Fragment,BitSet[][]> cache) {
        BitSet[][] times = cache.computeIfAbsent(fragment, f -> new BitSet[index.data.graphs.size()][]);
        if (times[time] == null) {
            BitSet[] matrix = new BitSet[fragment.roles.length];
            for (int r=0;r<matrix.length;r++) matrix[r]=background.candidates(fragment.roles[r],time);
            times[time]=matrix;
        }
        return times[time];
    }

    private static double square(double value) { return value*value; }
}
