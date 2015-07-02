package miralib.shannon;

import miralib.utils.Log;

public class DependencyTest {
  // List of accepted dependency-testing algorithms
  final static public int NO_TEST           = 0;
  final static public int SURROGATE_GAUSS   = 1;
  final static public int SURROGATE_GENERAL = 2;
  final static public int GAMMA_TEST        = 3;
  
  static public String algorithmToString(int algo) {
//    return "GAMMA_TEST";
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
//    return GAMMA_TEST;
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
