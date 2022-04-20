import com.github.psambit9791.jdsp.signal.peaks.FindPeak;
import com.github.psambit9791.jdsp.signal.peaks.Peak;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;

import java.util.*;
import java.util.stream.IntStream;

public class FeatureCalc {
    Instances dataset;
    List<String> classLabels;
    int nfeatures;
    int nPeaks = 10;
    boolean isFirstInstance = true;

    public FeatureCalc(List<String> classLabels) {
        this.classLabels = classLabels;
    }

    private Instance instanceFromArray(double[] valueArray, String label) {
        Instance instance = new DenseInstance(1.0, valueArray);

        instance.setDataset(dataset);
        if (label != null) {
            instance.setClassValue(label);
        } else {
            instance.setClassMissing();
        }

        return instance;
    }

    private int[] findPeaks(double[] measurements) {
        FindPeak fp = new FindPeak(measurements);

        Peak out = fp.detectPeaks();
        Double[] heights = Arrays.stream(out.getHeights()).boxed().toArray(Double[]::new); //To get height of all peaks

        int[] sortedPeaks = IntStream.range(0, heights.length)
            .boxed()
            .sorted(Comparator.comparing(i -> (heights[i])))
            .mapToInt(ele -> ele).toArray();

        return Arrays.copyOfRange(sortedPeaks, 0, nPeaks);
    }

    private double[] getFreqRange(double[] measurements) {
        return new double[]{
            Arrays.stream(measurements).min().orElse(0),
            Arrays.stream(measurements).max().orElse(0)
        };
    }

    private double calcSpectralCentroid(double[] measurements) {
        double total = 0;
        double totalMagnitude = 0;
        for (int i=0; i < measurements.length; i++) {
            total += i * measurements[i];
            totalMagnitude += measurements[i];
        }
        return total/totalMagnitude;
    }

    private double calcSpectralBandwidth(double[] measurements, double spectralCentroid) {
        double totalDiff = 0;
        double totalMagnitude = 0;
        for (int i=0; i < measurements.length; i++) {
            totalDiff += (spectralCentroid - i) * measurements[i];
            totalMagnitude += measurements[i];
        }
        return totalDiff / totalMagnitude;
    }

    private double calcBandEnergyRatio(double[] measurements) {
        int thres = measurements.length / 2;

        double lowFreqPower = 0;
        for (int i=0; i<thres; i++) {
            lowFreqPower += measurements[i] * measurements[i];
        }

        double highFreqPower = 0;
        for (int i=thres; i<measurements.length; i++) {
            highFreqPower += measurements[i] * measurements[i];
        }

        return lowFreqPower / highFreqPower;
    }

    private Instance calcFirstInstance(DataInstance data) {
        final ArrayList<Attribute> attrs = new ArrayList<>();
        final ArrayList<Double> values = new ArrayList<>();

        double[] measurements = IntStream.range(0, data.measurements.length).mapToDouble(i -> data.measurements[i]).toArray();

        // add fft bin measurements
        for(int i = 0; i < measurements.length; i++){
            attrs.add(new Attribute("bin"+i, i));
            values.add(measurements[i]);
        }
        nfeatures += measurements.length;

        // add top k peak frequency bins
        int[] peaks = findPeaks(measurements);
        for(int i = 0; i < nPeaks; i++){
            attrs.add(new Attribute("peak"+i, nfeatures+i));
            values.add((double) peaks[i]);
        }
        nfeatures += peaks.length;

        // add min/max frequencies
        double[] freqRange = getFreqRange(measurements);
        attrs.add(new Attribute("minFreq", nfeatures));
        values.add(freqRange[0]);
        nfeatures += 1;
        attrs.add(new Attribute("maxFreq", nfeatures));
        values.add(freqRange[1]);
        nfeatures += 1;

        double spectralCentroid = calcSpectralCentroid(measurements);

        // add spectral centroid
        attrs.add(new Attribute("specCentroid", nfeatures));
        values.add(spectralCentroid);
        nfeatures += 1;

        // add spectral bandwidth
        double spectralBW = calcSpectralBandwidth(measurements, spectralCentroid);
        attrs.add(new Attribute("specBW", nfeatures));
        values.add(spectralBW);
        nfeatures += 1;

        // add band energy ratio
        double bandEnergyRatio = calcBandEnergyRatio(measurements);
        attrs.add(new Attribute("ber", nfeatures));
        values.add(bandEnergyRatio);
        nfeatures += 1;

        /* build our dataset (instance header) */
        attrs.add(new Attribute("classlabel", classLabels, nfeatures));
        dataset = new Instances("dataset", attrs, 0);
        dataset.setClassIndex(nfeatures);

        /* build the output instance */
        double[] valueArray = new double[nfeatures+1];
        for(int i=0; i<nfeatures; i++) {
            valueArray[i] = values.get(i);
        }

        return instanceFromArray(valueArray, data.label);
    }

    private Instance calcOtherInstance(DataInstance data) {
        final double[] valueArray = new double[nfeatures+1];

        double[] measurements = IntStream.range(0, data.measurements.length).mapToDouble(i -> data.measurements[i]).toArray();

        int startIdx = 0;

        // add fft bin measurements
        for (int i = 0; i < measurements.length; i++){
            valueArray[i] = measurements[i];
        }
        startIdx += measurements.length;

        // add top k peak frequencies
        int[] peaks = findPeaks(measurements);
        for (int i = 0; i < nPeaks; i++){
            valueArray[startIdx+i] = peaks[i];
        }
        startIdx += peaks.length;

        // add min/max frequencies
        double[] freqRange = getFreqRange(measurements);
        valueArray[startIdx] = freqRange[0];
        startIdx += 1;
        valueArray[startIdx] = freqRange[1];
        startIdx += 1;

        double spectralCentroid = calcSpectralCentroid(measurements);

        // add spectral centroid
        valueArray[startIdx] = spectralCentroid;
        startIdx += 1;

        // add spectral bandwidth
        double spectralBW = calcSpectralBandwidth(measurements, spectralCentroid);
        valueArray[startIdx] = spectralBW;
        startIdx += 1;

        // add band energy ratio
        double bandEnergyRatio = calcBandEnergyRatio(measurements);
        valueArray[startIdx] = bandEnergyRatio;
        startIdx += 1;

        return instanceFromArray(valueArray, data.label);
    }

    public Instance calcFeatures(DataInstance data) {
        if (isFirstInstance) {
            isFirstInstance = false;
            return calcFirstInstance(data);

        } else {
            return calcOtherInstance(data);
        }
    }

    public Instances calcFeatures(Collection<DataInstance> dataCollection) {
        Instances res = null;
        for (DataInstance data : dataCollection) {
            Instance inst = calcFeatures(data);

            if (res == null) {
                res = new Instances(dataset, dataCollection.size());
            }
            res.add(inst);
        }
        return res;
    }
}
