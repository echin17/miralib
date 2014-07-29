/* COPYRIGHT (C) 2014 Fathom Information Design. All Rights Reserved. */

package miralib.shannon;

import java.util.ArrayList;
import java.util.Arrays;

import miralib.data.DataSlice1D;
import miralib.data.DataSlice2D;
import miralib.data.Value1D;
import miralib.data.Value2D;
import miralib.utils.Log;
import processing.core.PApplet;

/**
 * Optimal Histogram bin size calculation from Shimazaki and Shinomoto:
 * http://toyoizumilab.brain.riken.jp/hideaki/res/histogram.html
 *
 */

public class BinOptimizer {
  static final int MAX_BIN_COUNT       = 0;
  static final int SIMULATED_ANNEALING = 1;
  
  static int EXHAUSTIVE_SERCH_SIZE = 100000;
  static int MINIMZATION_METHOD = MAX_BIN_COUNT; 
  static int MAX_HIST_BINS = 100;
  static int MAX_RES_SAMPLE_SIZE = 10;
  static int MAX_HIST_SAMPLE_SIZE = 10000;
  static boolean PRINT_ERRORS = false;
  
  static public int calculate(DataSlice1D slice) {
    if (slice.varx.categorical()) return (int)slice.countx;
      
    int hsize = slice.values.size() / 2;
        
    int minNBins, maxNBins;
    if (slice.countx < 5) {
      minNBins = maxNBins = (int)slice.countx;
    } else {
      minNBins = 2;
      long lcount = slice.countx;
      int icount = Integer.MAX_VALUE < lcount ? Integer.MAX_VALUE : (int)lcount;
      float res = (float)res(slice.values);
      maxNBins = PApplet.min((int)(1.0f/res) + 1, icount, hsize);
    }
    int numValues = maxNBins - minNBins + 1;
    if (minNBins <= 0 || maxNBins <= 0 || numValues <= 0) {
      if (PRINT_ERRORS) {
        Log.message("Unexpected error number of bin values is negative. Bin limits: " + 
                    "[" + minNBins + ", " + maxNBins + "]");
      }
      return 1;
    }
    if (numValues <= EXHAUSTIVE_SERCH_SIZE) {
      IndexedValue[] cost = new IndexedValue[numValues];
      for (int n = minNBins; n <= maxNBins; n++) {
        float bsize = 1.0f / n; 
        double[] counts = hist1D(slice.values, n);

//        double k = countsMean(counts);    
//        double v = countsDev(counts, k);
        double[] res = countsMeanDev(counts);
        double k = res[0];
        double v = res[1];    

//        float c = (float)((2 * k - v) / (bsize * bsize));
        int m = slice.values.size();
        float c = (float)((2 * k - v) / (m * m * bsize * bsize));
        
        cost[n - minNBins] = new IndexedValue(c, n, false);
      }
      Arrays.sort(cost);
      if (cost[0] != null) {
        int n = cost[0].index;
        return n;
      } else {
        if (PRINT_ERRORS) {
          Log.message("Unexpected error cost array has null 0th element after sorting. Bin limits: " + 
                      "[" + minNBins + ", " + maxNBins + "]");
          Log.message("Cost array:");
          Log.message(cost.toString());
        }
        return (minNBins + maxNBins)/2;
      }      
    } else {
      if (MINIMZATION_METHOD == SIMULATED_ANNEALING) {
        // TODO: implement this!  
      }
      return maxNBins;
    }
  }

  static public int[] calculate(DataSlice2D slice) {
    if (slice.varx.categorical() && slice.vary.categorical()) {
      return new int[] {(int)slice.countx, (int)slice.county};
    }
    
    int sqsize = (int)PApplet.sqrt(slice.values.size() / 2);
    
    int minNBins0, maxNBins0;
    if (slice.varx.categorical()) {
      minNBins0 = maxNBins0 = (int)slice.countx;
    } else if (slice.countx < 5) {
      minNBins0 = maxNBins0 = (int)slice.countx;
    } else {
      minNBins0 = 2;
      long lcount = slice.countx;
      int icount = Integer.MAX_VALUE < lcount ? Integer.MAX_VALUE : (int)lcount;
      float res = (float)resx(slice.values);   
      maxNBins0 = PApplet.min((int)(1.0f/res) + 1, icount, sqsize);
    }
    
    int minNBins1, maxNBins1;
    if (slice.vary.categorical()) {
      minNBins1 = maxNBins1 = (int)slice.county;
    } else if (slice.county < 5) {
      minNBins1 = maxNBins1 = (int)slice.county;
    } else {
      minNBins1 = 2;
      long lcount = slice.county;
      int icount = Integer.MAX_VALUE < lcount ? Integer.MAX_VALUE : (int)lcount;
      float res = (float)resy(slice.values);            
      maxNBins1 = PApplet.min((int)(1.0f/res) + 1, icount, sqsize);
    }
    
    int blen0 = maxNBins0 - minNBins0 + 1;
    int blen1 = maxNBins1 - minNBins1 + 1;
    int numValues = blen0 * blen1; 
        
    if (minNBins0 <= 0 || maxNBins0 <= 0 || blen0 <= 0 || 
        minNBins1 <= 0 || maxNBins1 <= 1 || blen1 <= 0) {
      if (PRINT_ERRORS) {
        Log.message("Unexpected error number of bin values is negative. Bin limits: " + 
                    "[" + minNBins0 + ", " + maxNBins0 + "] x " + 
                    "[" + minNBins1 + ", " + maxNBins1 + "]");
      }
      return new int[] {1, 1};
    }
    
    if (numValues <= EXHAUSTIVE_SERCH_SIZE) {
      IndexedValue[] cost = new IndexedValue[numValues];
      //    String[] values = new String[numValues];
      for (int n0 = minNBins0; n0 <= maxNBins0; n0++) {
        for (int n1 = minNBins1; n1 <= maxNBins1; n1++) {
          float bsize0 = 1.0f / n0; 
          float bsize1 = 1.0f / n1;     
          float barea = bsize0 * bsize1;
          double[][] counts = hist2D(slice.values, n0, n1);

          //        double k = countsMean(counts);    
          //        double v = countsDev(counts, k);        
          double[] res = countsMeanDev(counts);
          double k = res[0];
          double v = res[1];

          int m = slice.values.size();
          float c = (float)((2 * k - v) / (m * m * barea * barea));

          int n = (n0 - minNBins0) * blen1 + (n1 - minNBins1);        
          cost[n] = new IndexedValue(c, n, false);
          //        values[n] = "" + c;
        }
      }
      //    PApplet.saveStrings(new File("./values"), values);
      //    System.exit(0);    
      Arrays.sort(cost);
      if (cost[0] != null) {
        int n = cost[0].index;    
        int n0 = n / blen1 + minNBins0;
        int n1 = n % blen1 + minNBins1;      
//        System.err.println(maxNBins0 + " " + maxNBins1 + " | " + n0 + " " + n1);
        return new int[] {n0, n1};
      } else {
        if (PRINT_ERRORS) {
          Log.message("Unexpected error cost array has null 0th element after sorting. Bin limits: " + 
              "[" + minNBins0 + ", " + maxNBins0 + "] x " + 
              "[" + minNBins1 + ", " + maxNBins1 + "]");
          Log.message("Cost array:");
          Log.message(cost.toString());
        }
        return new int[] {(minNBins0 + maxNBins0)/2, (minNBins1 + maxNBins1)/2};
      }
    } else {
      if (MINIMZATION_METHOD == SIMULATED_ANNEALING) {
        // TODO: implement this!  
      }
      return new int[] {maxNBins0, maxNBins1};      
    }    
  } 
  
  // Converts the 1D histogram defined by the equally-sized numBins bins, into
  // a new binning of the [0, 1] interval so that the probability of inside
  // each bin is uniform and equal to 1/numBins.
  static public float[] uniformBins1D(ArrayList<Value1D> values, int numBins) {
    float[] res = new float[numBins + 1];
    res[0] = 0;
    res[numBins] = 1;
    int N = values.size();
    double[] binCounts = hist1D(values, numBins);
    
    for (int i = 1; i < numBins; i++) {
      float pi = (float)binCounts[i - 1] / N; 
      res[i] = res[i - 1] + pi;
    }
    
    return res;
  }
  
  // Given the "uniforming" bins defined by the array ubins, this function returns
  // a value x' that results of applying the "uniformization" transformation
  // on the value x. This transformation consists in choosing the value x'
  // that is inside the i-th ubin, where i is the equally-sized bin x belongs to.
  static public double uniformTransform1D(double x, float[] ubins) {
    int bnum = ubins.length - 1;
    float bsize = 1.0f / bnum;    
    int bin = PApplet.constrain((int)(x / bsize), 0, bnum - 1);    
    return ubins[bin] + Math.random() * (ubins[bin + 1] - ubins[bin]); 
  }

  static public double[] hist1D(ArrayList<Value1D> values, int bnum) {    
    double[] counts = new double[bnum];
    float bsize = 1.0f / bnum;
//    for (Value1D value: values) {
    int mod = PApplet.max(1, values.size() / MAX_HIST_SAMPLE_SIZE);
    for (int i = 0; i < values.size(); i += mod) {
      Value1D value = values.get(i);
      int bin = PApplet.constrain((int)(value.x / bsize), 0, bnum - 1);
      counts[bin] += value.w;
    }
    return counts; 
  }

  static public double[][] hist2D(ArrayList<Value2D> values,                                
                                  int bnumx, int bnumy) {
    double[][] counts = new double[bnumx][bnumy];
    float bsizex = 1.0f / bnumx; 
    float bsizey = 1.0f / bnumy; 
//    for (Value2D value: values) {
    int mod = PApplet.max(1, values.size() / MAX_HIST_SAMPLE_SIZE);
    for (int i = 0; i < values.size(); i += mod) {
      Value2D value = values.get(i);
      int binx = PApplet.constrain((int)(value.x / bsizex), 0, bnumx - 1);
      int biny = PApplet.constrain((int)(value.y / bsizey), 0, bnumy - 1);    
      counts[binx][biny] += value.w;
    }
    return counts; 
  }

  static protected double countsMean(double[] counts) {
    int n = counts.length;
    double sum = 0;  
    for (int i = 0; i < n; i++) {
      sum += counts[i];   
    }
    return sum / n;  
  }

  static protected double countsDev(double[] counts, double mean) {
    int n = counts.length;
    double sum = 0;  
    for (int i = 0; i < n; i++) {
      sum += (counts[i] - mean) * (counts[i] - mean);
    }
    return sum / n;  
  }
  
  static protected double[] countsMeanDev(double[] counts) {
    int n = counts.length;
    double sum = 0;  
    double sumsq = 0;
    for (int i = 0; i < n; i++) {
      double count = counts[i];
      sum += count;
      sumsq += count * count; 
    }
    double mean = sum / n;
    double meansq = sumsq / n;
    double dev = Math.max(0, meansq - mean * mean);    
    return new double[] {mean, dev};    
  }    
  
  static protected double res(ArrayList<Value1D> values) {
    double res = Double.POSITIVE_INFINITY;
    int mod = PApplet.max(1, values.size() / MAX_RES_SAMPLE_SIZE);
    for (int i = 0; i < values.size(); i += mod) {
      Value1D vali = values.get(i);
      for (int j = 0; j < values.size(); j++) {
        Value1D valj = values.get(j);
        double diff = Math.abs(valj.x - vali.x);
        if (0 < diff) {
          res = Math.min(res, diff);
        }        
      }
    }    
    return Math.max(res, 1.0d / MAX_HIST_BINS);
  } 

  static protected double countsMean(double[][] counts) {
    int ni = counts.length;
    int nj = counts[0].length;
    double sum = 0;  
    for (int i = 0; i < ni; i++) {
      for (int j = 0; j < nj; j++) {
        sum += counts[i][j];   
      }
    }
    return sum / (ni * nj);  
  }

  static protected double countsDev(double[][] counts, double mean) {
    int ni = counts.length;
    int nj = counts[0].length;
    double sum = 0;  
    for (int i = 0; i < ni; i++) {
      for (int j = 0; j < nj; j++) {
        sum += (counts[i][j] - mean) * (counts[i][j] - mean);
      }    
    }
    return sum / (ni * nj);  
  }
    
  static protected double[] countsMeanDev(double[][] counts) {
    int ni = counts.length;
    int nj = counts[0].length;
    double sum = 0;
    double sumsq = 0;
    for (int i = 0; i < ni; i++) {
      for (int j = 0; j < nj; j++) {
        double count = counts[i][j];
        sum += count;
        sumsq += count * count; 
      }
    }
    double mean = sum / (ni * nj);
    double meansq = sumsq / (ni * nj);
    double dev = Math.max(0, meansq - mean * mean);    
    return new double[] {mean, dev};
  }  
  
  static protected double resx(ArrayList<Value2D> values) {
    double res = Double.POSITIVE_INFINITY;
    int mod = PApplet.max(1, values.size() / MAX_RES_SAMPLE_SIZE);
    for (int i = 0; i < values.size(); i += mod) {
      Value2D vali = values.get(i);
      for (int j = 0; j < values.size(); j++) {
        Value2D valj = values.get(j);
        double diff = Math.abs(valj.x - vali.x);
        if (0 < diff) {
          res = Math.min(res, diff);
        }        
      }
    }
    return Math.max(res, 1.0d / MAX_HIST_BINS);
  }  

  static protected double resy(ArrayList<Value2D> values) {
    double res = Double.POSITIVE_INFINITY;
    int mod = PApplet.max(1, values.size() / MAX_RES_SAMPLE_SIZE);
    for (int i = 0; i < values.size(); i += mod) {
      Value2D vali = values.get(i);
      for (int j = 0; j < values.size(); j++) {
        Value2D valj = values.get(j);
        double diff = Math.abs(valj.y - vali.y);
        if (0 < diff) {
          res = Math.min(res, diff);
        }        
      }
    }    
    return Math.max(res, 1.0d / MAX_HIST_BINS);
  }  
  
  static protected class IndexedValue implements Comparable<IndexedValue> {
    public float value;
    public int index;
    public boolean up;
    
    public IndexedValue(float value, int index) {
      this(value, index, true);
    }
    
    public IndexedValue(float value, int index, boolean up) {
      this.value = value; // Assuming value is normalized (0, 1, -1 for missing data).
      this.index = index;
      this.up = up;
    }
    
    // Returns a negative integer, zero, or a positive integer as this object is 
    // less than, equal to, or greater than the specified object.
    public int compareTo(IndexedValue obj) {
      float a = this.value;
      float b = obj.value;
      
      boolean bErr = b == -1 || Float.isNaN(b);
      boolean aErr = a == -1 || Float.isNaN(a);
      
      if (bErr || aErr) {
        // Missing/NaN data always at the bottom:
        if (bErr && aErr) {
          return 0;
        } else if (bErr) {
          return -1;
        } else {
          return +1; 
        }
      }
      
      if (!up) {
        a = -a;
        b = -b;
      }
      
      if (a < b) return +1;
      else if (b < a) return -1;
      else return 0;
    }
  }
}
