import java.io.IOException;

public class MainTestOriginal {
    public static void main(String[] args) throws IOException {
        // Relative paths are resolved from the CGS-Shield project root.
        String inputDirectory = "Data/USFlight/";
        String outputPath = "output.txt";
        int minsup = 2;
        int minvol = 1;
        int mincom = 1;
        float discretizationThreshold = 0.1f;
        // -1: DBLP first 43 attributes, USFlight first 8, all mapped attributes otherwise.
        // Set a positive count explicitly if the dataset folder has been renamed.
        int attributeCount = -1;

        CGSParameters parameters = new CGSParameters();
        parameters.inputDirectory = inputDirectory;
        parameters.outputPath = outputPath;
        parameters.minsup = minsup;
        parameters.minvol = minvol;
        parameters.mincom = mincom;
        parameters.discretizationThreshold = discretizationThreshold;
        parameters.attributeCount = attributeCount;

        AlgoCGSMiner algorithm = new AlgoCGSMiner();
        algorithm.runAlgorithm(parameters);
        algorithm.printStats();
    }
}
