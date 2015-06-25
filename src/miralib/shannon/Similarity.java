/* COPYRIGHT (C) 2014 Fathom Information Design. All Rights Reserved. */

package miralib.shannon;

import org.apache.commons.math3.distribution.GammaDistribution;
import org.apache.commons.math3.distribution.NormalDistribution;
import java.util.HashMap;
import miralib.data.DataSlice2D;
import miralib.data.Variable;
import miralib.math.Numbers;
import miralib.utils.Log;
import miralib.utils.Project;

/**
 * Similarity score between two variables.
 *
 */

public class Similarity {
  // List of accepted dependency-testing algorithms
  final static public int NO_TEST           = 0;
  final static public int SURROGATE_GAUSS   = 1;
  final static public int SURROGATE_GENERAL = 2;
  final static public int GAMMA_TEST        = 3;
  
  protected static NormalDistribution normDist = new NormalDistribution();
  protected static HashMap<Double, Double> criticalValues = new HashMap<Double, Double>();
  
  static public float calculate(DataSlice2D slice, float pvalue, Project prefs) {
    Variable varx = slice.varx;
    Variable vary = slice.vary;

    if (varx.weight() || vary.weight() || (varx.subsample() && vary.subsample())) {
      // weight variables are not comparable, or subsample variables between 
      // each other
      return 0;
    } 
    
    Double area = new Double(1 - pvalue/2);
    Double cval = criticalValues.get(area);
    if (cval == null) {
      cval = normDist.inverseCumulativeProbability(area);      
      criticalValues.put(area,  cval);
    } 
    
    int count = slice.values.size();
    int[] res = BinOptimizer.calculate(slice);
    int binx = res[0];
    int biny = res[1]; 
    
    float ixy = MutualInformation.calculate(slice, binx, biny);
    boolean indep = false;
            
    if (Float.isNaN(ixy) || Float.isInfinite(ixy)) {
      indep = true;
    } else if (prefs.depTest == NO_TEST || Numbers.equal(pvalue, 1)) {
      indep = ixy <= prefs.threshold;
    } else if (prefs.depTest == SURROGATE_GAUSS) {
      indep = surrogateGauss(slice, ixy, prefs.surrCount, cval);            
    } else if (prefs.depTest == SURROGATE_GENERAL) {      
      indep = surrogateGeneral(slice, ixy, pvalue);
    } else if (prefs.depTest == GAMMA_TEST) {
      indep = gammaTest(ixy, binx, biny, count, pvalue);
    }
    
    if (indep) {
      return 0;
    } else {
      float hxy = JointEntropy.calculate(slice, binx, biny);      
      float w;
      if (Numbers.equal(0.0, hxy)) {
        w = 0;
      } else {
        w = Numbers.constrain(ixy / hxy, 0, 1);
        if (Float.isNaN(w)) w = 0;
      }      
      return w;
    }
  }  
  
  static protected boolean surrogateGauss(DataSlice2D slice, float ixy, 
                                          int scount, double cvalue) {
    int sbinx = 0;
    int sbiny = 0;         
    float meani = 0;
    float meaniSq = 0;
    float stdi = 0; 
    for (int i = 0; i < scount; i++) {
      DataSlice2D surrogate = slice.shuffle();          
      if (i == 0) {
        int[] sres = BinOptimizer.calculate(surrogate);
        sbinx = sres[0];
        sbiny = sres[1];
      }
      float smi = MutualInformation.calculate(surrogate, sbinx, sbiny);      
      meani += smi;
      meaniSq += smi * smi;
    }
    meani /= scount;
    meaniSq /= scount;
    stdi = (float)Math.sqrt(Math.max(0, meaniSq - meani * meani));  // TODO: fix, biased estimate!!      
    float zs = (ixy - meani) / stdi;
    if (Float.isNaN(zs) || Float.isInfinite(zs)) {
      return true;
    } else { 
      return -cvalue <= zs && zs <= cvalue;
    }    
  }
  
  static protected boolean surrogateGeneral(DataSlice2D slice, float ixy, 
                                            float pvalue) {
    int sbinx = 0;
    int sbiny = 0;  
    float maxMI = 0;
    int numSurr = (int)(1/pvalue) - 1;
    for (int i = 0; i < numSurr; i++) {          
      DataSlice2D surrogate = slice.shuffle();
      if (i == 0) {
        int[] sres = BinOptimizer.calculate(surrogate);
        sbinx = sres[0];
        sbiny = sres[1];
      }
      maxMI = Math.max(maxMI, MutualInformation.calculate(surrogate, sbinx, sbiny));
    }
    return ixy < maxMI;    
  }
  
  static protected boolean gammaTest(float ixy, int binx, int biny, int count, float pvalue) {
    double shapePar = (binx - 1) * (biny - 1) / 2d;
    double scalePar = 1.0d / count;
    try {
      GammaDistribution gammaDist = new GammaDistribution(shapePar, scalePar);
      double c = gammaDist.inverseCumulativeProbability(1 - pvalue);            
      return ixy <= c;
    } catch (Exception ex) {
      return true;
    }    
  }
  
  static public String algorithmToString(int algo) {
    if (algo == NO_TEST) {        
      return "NO_TEST";
    } else if (algo == SURROGATE_GAUSS) {
      return "SURROGATE_GAUSS";
    } else if (algo == SURROGATE_GENERAL) {
      return "SURROGATE_GENERAL";
    } else if (algo == GAMMA_TEST) {
      return "GAMMA_TEST";
    }
    String err = "Unsupported similarity algorithm: " + algo;
    Log.error(err, new RuntimeException(err));
    return "unsupported";    
  }
  
  static public int stringToAlgorithm(String name) {
    name = name.toUpperCase();
    if (name.equals("NO_TEST")) {
      return NO_TEST;
    } else if (name.equals("SURROGATE_GAUSS")) {
      return SURROGATE_GAUSS;
    } else if (name.equals("SURROGATE_GENERAL")) {
      return SURROGATE_GENERAL;
    } else if (name.equals("GAMMA_TEST")) {
      return GAMMA_TEST;
    } 
    String err = "Unsupported similarity algorithm: " + name;
    Log.error(err, new RuntimeException(err));
    return -1;
  }
}
