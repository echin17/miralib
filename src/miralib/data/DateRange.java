package miralib.data;

import java.util.ArrayList;

import org.joda.time.DateTime;

import processing.data.TableRow;

public class DateRange extends Range {
  protected DateTime mind, maxd;
  protected ArrayList<String> values;
  
  public DateRange(Variable var) {
    super(var);
    this.values = new ArrayList<String>();
  }

  public DateRange(DateRange that) {
    super(that);
    this.mind = new DateTime(mind);
    this.maxd = new DateTime(maxd);
    this.values = new ArrayList<String>();
  }
  
  @Override
  public void set(double min, double max, boolean normalized) {
    if (normalized) {
      long minl = Math.round(var.range.denormalize(min));
      long maxl = Math.round(var.range.denormalize(max));
      mind = new DateTime(minl);
      maxd = new DateTime(maxl);
    } else {
      long minl = Math.round(min);
      long maxl = Math.round(max);
      mind = new DateTime(minl);
      maxd = new DateTime(maxl);      
    }    
  }

  @Override
  public void set(ArrayList<String> values) {
    String[] array = new String[values.size()]; 
    values.toArray(array);
    set(array);    
  }

  @Override
  public void set(String... values) {
    if (values == null || values.length < 2) return;
    mind = DateVariable.parse(values[0]);
    maxd = DateVariable.parse(values[1]);
    if (mind == null) mind = new DateTime("1900-01-01");
    if (maxd == null) maxd = new DateTime("2099-12-31");    
  }

  @Override
  public void reset() {
    if (mind == null) mind = new DateTime("1900-01-01");
    if (maxd == null) maxd = new DateTime("2099-12-31");    
  }

  @Override
  public void update(TableRow row) {
    int idx = var.getIndex();
    String value = row.getString(idx);
    DateTime dat = DateVariable.parse(value);
    if (dat != null) {
      if (dat.compareTo(mind) < 0) mind = dat;
      if (0 < dat.compareTo(maxd)) maxd = dat;      
    }    
  }

  @Override
  public boolean inside(TableRow row) {
    int idx = var.getIndex();
    String value = row.getString(idx);
    DateTime dat = DateVariable.parse(value);
    if (dat != null) {      
      return 0 <= dat.compareTo(mind) && dat.compareTo(maxd) <= 0;       
    }
    return false;
  }

  @Override
  public double getMin() {
    return mind.getMillis();
  }

  @Override
  public double getMax() {
    return maxd.getMillis();
  }

  @Override
  public long getCount() {
    return Long.MAX_VALUE;
  }

  @Override
  public int getRank(String value) {
    return -1;
  }

  @Override
  public int getRank(String value, Range supr) {
    return -1;
  }

  @Override
  public ArrayList<String> getValues() {
    values.clear();    
    values.add(DateVariable.print(mind));
    values.add(DateVariable.print(maxd));
    return values;
  }

  @Override
  public double snap(double value) {
    return constrain((long)value);
  }

  @Override
  public double normalize(int value) {
    return normalizeImpl(value); 
  }
  
  @Override
  public double normalize(long value) {
    return normalizeImpl(value); 
  }
  
  @Override
  public double normalize(float value) {
    return normalizeImpl(value);
  }
  
  @Override
  public double normalize(double value) {
    return normalizeImpl(value);
  }
  
  @Override
  public double denormalize(double value) {
    double min = getMin(); 
    double max = getMax();        
    return min + value * (max - min);    
  } 

  @Override
  public int constrain(int value) {
    return (int)constrainImpl(value);
  }
  
  @Override
  public long constrain(long value) {
    return (long)constrainImpl(value);
  }
  
  @Override
  public float constrain(float value) {
    return (float)constrainImpl(value);
  }
  
  @Override
  public double constrain(double value) {
    return constrainImpl(value);
  }
  
  public boolean equals(Object that) {
    if (this == that) return true;
    if (that instanceof DateRange) {
      DateRange range = (DateRange)that;
      return this.mind.compareTo(range.mind) == 0 && this.maxd.compareTo(range.maxd) == 0;
    }
    return false;
  }
  
  public String toString() {
    String val0 = DateVariable.print(mind);
    String val1 = DateVariable.print(maxd);    
    return val0 + "," + val1;
  }
}
