import java.nio.file.Path;

/** Configuration for identity-preserving Connected Graph Sequence mining. */
public class CGSParameters {
    public String inputDirectory = "Data/DBLP/";
    public String outputPath = "output.txt";
    public int minsup = 3;
    public int minvol = 2;
    public int mincom = 1;
    public float discretizationThreshold = 0.1f;
    // -1 selects dataset defaults: DBLP first 43, USFlight first 8, all mapped columns otherwise.
    public int attributeCount = -1;

    public void validate() {
        if (minsup < 1 || minvol < 1 || mincom < 1)
            throw new IllegalArgumentException("minsup, minvol and mincom must be positive integers");
        if (attributeCount != -1 && attributeCount < 1)
            throw new IllegalArgumentException("attributeCount must be -1 (dataset default) or a positive column count");
        if (!Float.isFinite(discretizationThreshold) || discretizationThreshold <= 0)
            throw new IllegalArgumentException("discretizationThreshold must be finite and positive");
        if (inputDirectory == null || outputPath == null)
            throw new IllegalArgumentException("Input and output paths are required");
    }

    public Path inputPath() { return CGSPaths.resolve(inputDirectory); }
    public Path outputPath() { return CGSPaths.resolve(outputPath); }
}
