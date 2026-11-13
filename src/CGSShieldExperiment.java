import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Independent publication pipeline; does not change CGSMiner mining or its original output. */
public class CGSShieldExperiment {
    private double maxMemory;

    public void run(CGSShieldParameters parameters, boolean shield) throws IOException {
        parameters.validate();
        long started=System.nanoTime();
        maxMemory=0;
        Path input=CGSPaths.resolve(parameters.inputPath), output=CGSPaths.resolve(parameters.outputPath);
        Path dataset=CGSPaths.resolve(parameters.inputDirectory);
        if(output.toAbsolutePath().normalize().equals(input.toAbsolutePath().normalize())
                || output.toAbsolutePath().normalize().startsWith(dataset.toAbsolutePath().normalize()))
            throw new IOException("Publication must not overwrite the CGSMiner input or dataset");
        CGSShieldInput.Metadata metadata=CGSShieldInput.metadata(input);
        float threshold=parameters.discretizationThreshold;
        if(Float.isNaN(threshold)) threshold=metadata.threshold;
        if(!Float.isFinite(threshold) || threshold<=0) throw new IOException("The input header has no valid discretization threshold; configure it explicitly");
        if(Float.isFinite(metadata.threshold) && Float.compare(metadata.threshold,threshold)!=0)
            throw new IOException("The configured discretization threshold differs from the original mining header");
        if(metadata.attributes<1)
            throw new IOException("The source header has no valid attribute count; rerun MainTestOriginal before publication");
        CGSData data=CGSData.load(dataset,threshold,metadata.attributes);
        sampleMemory();
        CGSShieldInput source=CGSShieldInput.load(input,data);
        sampleMemory();
        CGSShieldTrendIndex index=new CGSShieldTrendIndex(data);
        sampleMemory();
        CGSShieldMatching matching=new CGSShieldMatching(parameters);
        CGSShieldRisk risk=new CGSShieldRisk(index,matching);
        List<CGSShieldRisk.Scores> scores=new ArrayList<>();
        boolean[] published=new boolean[source.patterns.size()];
        int kept=0,estimatedPatterns=0,uncertainDecisions=0;
        double maximumWidth=0;
        for(int i=0;i<source.patterns.size();i++) {
            CGSShieldRisk.Scores score=risk.compute(source.patterns.get(i));
            scores.add(score);
            published[i]=score.publish(parameters,shield);
            if(published[i]) kept++;
            if(score.estimated) estimatedPatterns++;
            maximumWidth=Math.max(maximumWidth,score.r2Upper-score.r2Lower);
            if(score.estimated && score.r1<=parameters.r1Threshold
                    && score.r2Lower<=parameters.r2Threshold && score.r2Upper>parameters.r2Threshold) uncertainDecisions++;
            if((i+1)%100==0 || i+1==source.patterns.size()) {
                sampleMemory();
            }
        }
        long scoringFinished=System.nanoTime();
        write(output,parameters,source.patterns,scores,published,shield,threshold,data.attributeNames.length);
        Path internal=CGSPaths.resolve(parameters.internalAuditDirectory).resolve(shield ? "shield-risk-scores.csv" : "candidate-k-risk-scores.csv");
        writeRiskAudit(internal,source.patterns,scores,published);
        // Freeze the protection-stage peak before allocating attacker knowledge or simulation caches.
        sampleMemory();
        double protectionMemory=maxMemory;
        CGSShieldEvaluation.Result evaluation=new CGSShieldEvaluation(index,parameters).evaluate(source.patterns,published);
        long finished=System.nanoTime();
        writeRunAudit(internal.resolveSibling(shield ? "shield-run-summary.txt" : "candidate-k-run-summary.txt"),
                parameters,matching,evaluation,estimatedPatterns,uncertainDecisions,maximumWidth,data.attributeNames.length);
        System.out.println("============= " + (shield ? "CGS-Shield" : "Candidate-k Suppression (KAnomaly)") + " =============");
        System.out.println("Source pattern records: " + source.sourceRecords);
        System.out.println("Distinct anonymous patterns: " + source.patterns.size());
        System.out.println("Pattern count (published): " + kept);
        System.out.println("Suppressed patterns: " + (source.patterns.size()-kept));
        printMetrics(evaluation.published);
        System.out.println("Scoring and input time: " + (scoringFinished-started)/1000000 + " ms");
        System.out.println("Total time: " + (finished-started)/1000000 + " ms");
        System.out.printf(Locale.ROOT,"Maximum sampled heap memory usage: %.3f MB%n",protectionMemory);
    }

    private static void printMetrics(CGSShieldEvaluation.Metrics value) {
        System.out.println(" Published:");
        System.out.println(" Complete mapping hits: " + value.hits);
        System.out.println(" RSR: " + number(value.rsr));
        System.out.println(" PRR: " + number(value.prr));
    }

    /** Keep numerical diagnostics available without cluttering the entry-point console. */
    private static void writeRunAudit(Path file,CGSShieldParameters parameters,CGSShieldMatching matching,
            CGSShieldEvaluation.Result evaluation,int estimatedPatterns,int uncertainDecisions,double maximumWidth,int attributes) throws IOException {
        try(BufferedWriter writer=Files.newBufferedWriter(file,StandardCharsets.UTF_8)) {
            writer.write("Internal diagnostics; not a public pattern release.\n");
            writer.write("attack_simulations="+parameters.attackSimulations+"\nattack_seed="+parameters.attackSeed+"\n");
            writer.write("total_attributes="+attributes+"\nknown_attributes_per_vertex_time="+((attributes+1)/2)+"\n");
            writer.write("complete_mapping_hits="+evaluation.published.hits+"\n");
            writer.write("counting_samples="+parameters.countingSamples+"\ncounting_seed="+parameters.countingSeed+"\n");
            writer.write("patterns_with_estimated_R2="+estimatedPatterns+"\n");
            writer.write("risk_count_estimates="+matching.estimatedComponents+"\n");
            writer.write("risk_count_max_relative_SE="+matching.maxRelativeSE+"\n");
            writer.write("risk_count_zero_weight_fallbacks="+matching.zeroSampleFallbacks+"\n");
            writer.write("max_certified_R2_interval_width="+maximumWidth+"\n");
            writer.write("R2_threshold_straddling_decisions="+uncertainDecisions+"\n");
            writer.write("attack_bound_resolved_outcomes="+evaluation.boundedDecisions+"\n");
            writer.write("attack_estimate_required_outcomes="+evaluation.estimatedDecisions+"\n");
            writer.write("attack_count_max_relative_SE="+evaluation.matching.maxRelativeSE+"\n");
            writer.write("attack_count_zero_weight_fallbacks="+evaluation.matching.zeroSampleFallbacks+"\n");
            writer.write("RSR_simulation_SE="+evaluation.published.rsrSE+"\n");
            writer.write("Estimated counts and empirical standard errors are not exact error guarantees.\n");
        }
    }

    private static void write(Path file,CGSShieldParameters parameters,List<CGSShieldPattern> patterns,
            List<CGSShieldRisk.Scores> scores,boolean[] published,boolean shield,float threshold,int attributes) throws IOException {
        Path parent=file.toAbsolutePath().getParent();
        if(parent!=null) Files.createDirectories(parent);
        try(BufferedWriter writer=Files.newBufferedWriter(file,StandardCharsets.UTF_8)) {
            writer.write("# " + (shield ? "CGS-Shield" : "Candidate-k Suppression") + " anonymous publication\n");
            writer.write("# No names, topology, source records, or cross-fragment identity links are released\n");
            writer.write("# Starts are the union over deduplicated sources; support is the size of that union\n");
            writer.write("# Attributes used: " + attributes + "; discretizationThreshold=" + threshold + "\n");
            writer.write("# Thresholds: " + (shield ? "R1<=" + parameters.r1Threshold + " R2<=" + parameters.r2Threshold : "K_min>=" + parameters.kMin) + "\n");
            writer.write("# Matching-count mode: " + (parameters.exactOnly ? "EXACT" : "AUTO: exact reductions and fixed-seed SIS estimates") + "\n");
            int count=0;
            for(int i=0;i<patterns.size();i++) if(published[i]) {
                CGSShieldPattern pattern=patterns.get(i);
                writer.write("# Anonymous pattern " + (++count) + " support=" + pattern.starts.cardinality()
                        + " starts=" + CGSOutput.displayTimes(pattern.starts) + "\n");
                writer.write(pattern.display());
                writer.newLine();
            }
            writer.write("# Pattern count: " + count + "\n");
        }
    }

    private static void writeRiskAudit(Path file,List<CGSShieldPattern> patterns,List<CGSShieldRisk.Scores> scores,boolean[] published) throws IOException {
        Files.createDirectories(file.getParent());
        try(BufferedWriter writer=Files.newBufferedWriter(file,StandardCharsets.UTF_8)) {
            writer.write("anonymous_index,fragment_count,role_count,start_count,R1,R2_estimate,R2_certified_lower,R2_certified_upper,K_min,estimated,published\n");
            for(int i=0;i<patterns.size();i++) {
                CGSShieldPattern p=patterns.get(i); CGSShieldRisk.Scores s=scores.get(i);
                writer.write((i+1)+","+p.fragments.length+","+p.roleCount+","+p.starts.cardinality()+","+s.r1+","+s.r2+","+s.r2Lower+","+s.r2Upper+","+s.kMin+","+s.estimated+","+published[i]+"\n");
            }
        }
    }
    private void sampleMemory() {
        Runtime runtime=Runtime.getRuntime();
        maxMemory=Math.max(maxMemory,(runtime.totalMemory()-runtime.freeMemory())/1048576.0);
    }
    private static String number(double value) {
        return Double.isNaN(value) ? "N/A" : Double.isInfinite(value) ? "unbounded" : String.format(Locale.ROOT,"%.8f",value);
    }
}
