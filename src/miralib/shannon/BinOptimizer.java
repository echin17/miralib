/* COPYRIGHT (C) 2014 Fathom Information Design. All Rights Reserved. */

package miralib.shannon;

import java.util.ArrayList;
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
  // These parameters dramatically affect the performance of the optimization algorithm
  static int MAX_SEARCH_SAMPLE_SIZE = 1000; // Won't evaluate more than this number of bins while searching for the optimal size
  static int MAX_HIST_BINS = 100;           // No more than this number of bins per variable.
  static int MAX_RES_SAMPLE_SIZE = 10;      // Number of values sampled to search for minimum difference
  static int MAX_HIST_SAMPLE_SIZE = 10000;  // number of values used to estimate the histograms during optimization
  static boolean PRINT_ERRORS = false;
  
  static public int calculate(DataSlice1D slice) {
    if (slice.varx.categorical()) return (int)slice.countx;
      
    int size = slice.values.size();
    int hsize = size / 2;
        
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
    
    int minn = (minNBins + maxNBins)/2;
    
    float minc = Float.MAX_VALUE;
    int mod = PApplet.max(1, numValues / MAX_SEARCH_SAMPLE_SIZE);
    for (int i = 0; i < numValues; i += mod) {
      int n = minNBins + i;
      float bsize = 1.0f / n;
      double[] counts = hist1D(slice.values, n);
      double[] res = countsMeanDev(counts);
      double k = res[0];
      double v = res[1];
      float c = (float)((2 * k - v) / (size * size * bsize * bsize));        
      if (c < minc) {
        minc = c;
        minn = n;
      }           
    }
    return minn;
  }

  static public int[] calculate(DataSlice2D slice) {
    if (slice.varx.categorical() && slice.vary.categorical()) {
      return new int[] {(int)slice.countx, (int)slice.county};
    }
    
    int size = slice.values.size();
    int sqsize = (int)PApplet.sqrt(size / 2);
    
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
      
    int minn0 = (minNBins0 + maxNBins0)/2;
    int minn1 = (minNBins1 + maxNBins1)/2;
    float minc = Float.MAX_VALUE;
    int mod = PApplet.max(1, numValues / MAX_SEARCH_SAMPLE_SIZE);
    for (int i = 0; i < numValues; i += mod) {
      int n0 = i / blen1 + minNBins0;
      int n1 = i % blen1 + minNBins1;
      float bsize0 = 1.0f / n0; 
      float bsize1 = 1.0f / n1;
      float barea = bsize0 * bsize1;          
      double[][] counts = hist2D(slice.values, n0, n1);
      double[] res = countsMeanDev(counts);
      double k = res[0];
      double v = res[1];
      float c = (float)((2 * k - v) / (size * size * barea * barea));
      if (c < minc) {
        minc = c;
        minn0 = n0;
        minn1 = n1;
      }           
    }
    return new int[] {minn0, minn1};
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
    int mod = PApplet.max(1, values.size() / MAX_HIST_SAMPLE_SIZE);
    for (int i = 0; i < values.size(); i += mod) {
      Value2D value = values.get(i);
      int binx = PApplet.constrain((int)(value.x / bsizex), 0, bnumx - 1);
      int biny = PApplet.constrain((int)(value.y / bsizey), 0, bnumy - 1);    
      counts[binx][biny] += value.w;
    }
    return counts; 
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
    // VERY IMPORTANT: Do NOT use a variance that uses N-1 to divide the sum of 
    // squared errors. Use the biased variance in the method.
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
    
  static protected double[] countsMeanDev(double[][] counts) {
    int ni = counts.length;
    int nj = counts[0].length;
    int n = ni * nj;
    double sum = 0;
    double sumsq = 0;
    for (int i = 0; i < ni; i++) {
      for (int j = 0; j < nj; j++) {
        double count = counts[i][j];
        sum += count;
        sumsq += count * count; 
      }
    }
    // VERY IMPORTANT: Do NOT use a variance that uses N-1 to divide the sum of 
    // squared errors. Use the biased variance in the method.    
    double mean = sum / n;
    double meansq = sumsq / n;
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
}
