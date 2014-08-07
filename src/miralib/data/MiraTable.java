/* COPYRIGHT (C) 2014 Fathom Information Design. All Rights Reserved. */

package miralib.data;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import miralib.data.DataSet.CodebookPage;
import miralib.utils.Log;
import processing.core.PApplet;
import processing.data.Table;
import processing.data.TableRow;

/**
 * Customized table class
 *
 */

public class MiraTable extends Table {
  final static protected int[] CHECK_FRACTION = {1, 2, 10, 100};
  final static protected int STRING_CATEGORICAL_MAX_COUNT = 100;
  final static protected int NUMERICAL_CATEGORICAL_MAX_COUNT = 5;    
  final static protected float STRING_MIN_FRACTION = 0.1f;
  final static protected float NUMERICAL_MIN_FRACTION = 0.1f;
  final static protected float NUMERICAL_MAX_FRACTION = 0.1f; 
  
  {
    missingInt = Integer.MIN_VALUE;
    missingLong = Long.MIN_VALUE;
    missingFloat = Float.NEGATIVE_INFINITY;
    missingDouble = Double.NEGATIVE_INFINITY;
  }
  
  public MiraTable() {
    super();
  }  
  
  public MiraTable(InputStream input, String options) throws IOException {
    super(input, options);
  }
  
  public void setColumnTypes(final Table dictionary) {
    ensureColumn(dictionary.getRowCount() - 1);
    int typeCol = 1;
    final String[] typeNames = dictionary.getStringColumn(typeCol);

    if (dictionary.getColumnCount() > 1) {
      if (getRowCount() > 1000) {
        int proc = Runtime.getRuntime().availableProcessors();
        ExecutorService pool = Executors.newFixedThreadPool(proc/2);
        for (int i = 0; i < dictionary.getRowCount(); i++) {
          final int col = i;
          pool.execute(new Runnable() {
            public void run() {
              setColumnType(col, typeNames[col]);
            }
          });
        }
        pool.shutdown();
        while (!pool.isTerminated()) {
          Thread.yield();
        }

      } else {
        for (int col = 0; col < dictionary.getRowCount(); col++) {
          setColumnType(col, typeNames[col]);
        }
      }
    }
  }
  
  public MiraTable typedParse(InputStream input, String options) throws IOException {
    MiraTable table = new MiraTable();
    table.setColumnTypes(this);
    table.parse(input, options);
    return table;
  }  
  
  static public MiraTable typedParse(InputStream input, Table dict, 
                                     String options, String missing) {
    MiraTable table = new MiraTable();
    table.setMissingString(missing);
    table.setColumnTypes(dict);
    try {
      table.parse(input, options);
    } catch (IOException e) {
      Log.error("Cannot parse data", e);        
    }
    return table;
  }
  
  static public MiraTable guessedParse(InputStream input, 
                                       HashMap<String, CodebookPage> codebook, 
                                       String options, String missing) {
    MiraTable table = new MiraTable();
    table.setMissingString(missing);
    try {
      table.parse(input, options);
    } catch (IOException e) {
      Log.error("Cannot parse data", e);
    }
    
    // obtaining types from codebook, or trying to guess from data...
    for (int i = 0; i < table.getColumnCount(); i++) {
      String name = table.getColumnTitle(i);
      if (name == null || name.equals("")) continue;
      CodebookPage pg = codebook.get(name);
      if (pg == null) {
        int guess = guessColumnType(table, i, missing);
        table.setColumnType(i, guess);
      } else {
        table.setColumnType(i, pg.type);
      }
//    Log.message("Column " + i + " " + table.getColumnTitle(i) + ": " + guess  + " " + table.getColumnType(i));
    }
    
    return table;
  }
  
  static protected int guessColumnType(Table table, int i, String missing) {
    int type0 = table.getColumnType(i);
    if (table.getColumnType(i) != Table.STRING) return type0;
    
    int[] typeCounts = {0, 0, 0, 0, 0};  // string, int, long, float, double
    float[] typeFracs = {0, 0, 0, 0, 0}; // string, int, long, float, double  
    HashSet<String> strValues = new HashSet<String>(); 
    int count = 0;
    int tot = table.getRowCount();
    int step = 1;
    if (tot < 1000) step = CHECK_FRACTION[0];
    else if (tot < 10000) step = CHECK_FRACTION[1];
    else if (tot < 100000) step = CHECK_FRACTION[2];
    else step = CHECK_FRACTION[3];
    for (int n = 0; n < table.getRowCount(); n++) {
      if (n % step != 0) continue;
      
      TableRow row = table.getRow(n); 
      String value = row.getString(i);
      if (value.equals(missing)) continue;
      count++;    
      
      if (isInt(value)) typeCounts[Table.INT]++;
      else if (isFloat(value)) typeCounts[Table.FLOAT]++;
      else if (isLong(value)) typeCounts[Table.LONG]++;
      else if (isDouble(value)) typeCounts[Table.DOUBLE]++;
      else typeCounts[Table.STRING]++;      
      if (strValues.size() <= PApplet.max(STRING_CATEGORICAL_MAX_COUNT, NUMERICAL_CATEGORICAL_MAX_COUNT) + 1) strValues.add(value);
    }
    
    if (count == 0) return Table.INT;
      
    for (int t = Table.STRING; t <= Table.DOUBLE; t++) {
      typeFracs[t] = (float)typeCounts[t] / count;
    }
    
    if (STRING_MIN_FRACTION <= typeFracs[Table.STRING]) {
      // String or category 
      if (strValues.size() <= STRING_CATEGORICAL_MAX_COUNT) return Table.CATEGORY;
      else return Table.STRING;
    } else {
      // Numerical or category
      if (NUMERICAL_MIN_FRACTION <= typeFracs[Table.INT] && 
          typeFracs[Table.LONG] < NUMERICAL_MAX_FRACTION && 
          typeFracs[Table.FLOAT] < NUMERICAL_MAX_FRACTION && 
          typeFracs[Table.DOUBLE] < NUMERICAL_MAX_FRACTION) {
        if (strValues.size() <= NUMERICAL_CATEGORICAL_MAX_COUNT) return Table.CATEGORY;
        else return Table.INT;
      }
      if (NUMERICAL_MIN_FRACTION <= typeFracs[Table.LONG] && 
          typeFracs[Table.INT] < NUMERICAL_MAX_FRACTION && 
          typeFracs[Table.FLOAT] < NUMERICAL_MAX_FRACTION && 
          typeFracs[Table.DOUBLE] < NUMERICAL_MAX_FRACTION) {
        if (strValues.size() <= NUMERICAL_CATEGORICAL_MAX_COUNT) return Table.CATEGORY;
        else return Table.LONG;      
      }
      if (NUMERICAL_MIN_FRACTION <= typeFracs[Table.FLOAT] && 
          typeFracs[Table.LONG] < NUMERICAL_MAX_FRACTION && 
          typeFracs[Table.DOUBLE] < NUMERICAL_MAX_FRACTION) {
        return Table.FLOAT;
      }
      if (NUMERICAL_MIN_FRACTION <= typeFracs[Table.DOUBLE]) {
        return Table.DOUBLE;
      } 
    }
    
    return Table.STRING;
  }    
   
  static protected boolean isInt(String str) {
    try {
      new Integer(str);
      return true;
    } catch (NumberFormatException e) {
      return false; 
    }  
  }

  static protected boolean isLong(String str) {
    try {
      new Long(str);
      return true;
    } catch (NumberFormatException e) {
      return false; 
    }  
  }

  static protected boolean isFloat(String str) {
    try {
      new Float(str);
      return true;
    } catch (NumberFormatException e) {
      return false; 
    }  
  }

  static protected boolean isDouble(String str) {
    try {
      new Double(str);
      return true;
    } catch (NumberFormatException e) {
      return false; 
    }  
  }
}
