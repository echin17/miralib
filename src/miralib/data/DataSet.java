/* COPYRIGHT (C) 2014 Fathom Information Design. All Rights Reserved. */

package miralib.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.zip.GZIPInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;

import processing.data.Table;
import processing.data.TableRow;
import processing.data.XML;
import miralib.math.Numbers;
import miralib.shannon.Similarity;
import miralib.utils.Fileu;
import miralib.utils.Log;
import miralib.utils.Project;

/**
 * Class holding all the information required to specify a dataset (tables, 
 * hierarchy tree, sort variable, etc.)
 *
 */

public class DataSet {
  protected Project project;
  
  protected MiraTable data;
  
  protected DataTree tree; 
  protected HashMap<String, CodebookPage> codebook;
  protected ArrayList<Variable> allvars;
  protected ArrayList<Variable> covars;
  protected ArrayList<Variable> columns;  
  protected ArrayList<Float> scores;
  protected ArrayList<Boolean> selected;
  
  protected Variable labelVar;
  
  protected Variable sortVar;
  protected DataRanges sortRanges;
  protected float sortPValue;
  protected float sortMissingThreshold;
  
  protected ThreadPoolExecutor scorePool;
  protected SortTask sortTask;
  protected boolean threadedSort;
  protected boolean cancelSort;
  protected int nonthreadedCount;
  
  public DataSet(Project project) {
    this.project = project;
    
    loadCodebook();
    loadData();
    loadGroups();
    loadMetadata();
    
    initColumns();
    Log.message("Done.");
  }
  
  public int getRecordCount() {
    return getRowCount();
  }
  
  public int getRowCount() {
    return data.getRowCount();    
  }

  public int getRecordCount(DataRanges ranges) {
    return getRowCount(ranges);
  }
  
  public int getRowCount(DataRanges ranges) {
    DataRanges oranges = new DataRanges(ranges);
    int ntot = 0;
    for (int r = 0; r < data.getRowCount(); r++) {
      TableRow row = data.getRow(r);       
      if (!insideRanges(row, oranges)) continue;
      ntot++; 
    }
    return ntot;
  } 
  
  public int getGroupCount() {
    return tree.groups.size();
  }

  public int getTableCount() {
    return tree.tables.size();
  }  
  
  public int getVariableCount() {
    return tree.variables.size();
  }  
  
  public Variable getVariable(int i) {
    return tree.getVariable(i);
  }

  public Variable getVariable(String name) {
    return tree.varmap.get(name);
  }

  public int getVariableIndex(Variable var) {
    return tree.variables.indexOf(var); 
  }  
  
  public ArrayList<Variable> getVariables(VariableContainer container) {
    if (container.getItemType() == DataTree.GROUP_ITEM) {
      return tree.getGroupVariables(container);
    } else if (container.getItemType() == DataTree.TABLE_ITEM) {
      return tree.getTableVariables(container);
    }
    return null;
  }
  
  public ArrayList<Variable> getVariables() {
    return allvars;
  }
  
  public int getColumnCount() {
    return columns.size();    
  }
  
  public int getColumn(Variable var) {
    return columns.indexOf(var);
  }

  public Variable getColumn(int i) {
    return columns.get(i);  
  }
  
  public ArrayList<Variable> getColumns() {
    return columns;
  }    
  
  public int addColumn(Variable var) {
    if (!var.include) return -1;
    if (!columns.contains(var)) {
      var.column = true;      
      int idx = 0;
      if (sortVar == null) {
        int ridx = getVariableIndex(var);
        while (idx < columns.size() && getVariableIndex(columns.get(idx)) < ridx) idx++;
        columns.add(idx, var);
        scores.add(idx, -1f);        
      } else {
        if (sorting()) cancelCurrentSort();
        // The column is added at the end, but the sorting will put it at its
        // correct location
        idx = columns.size() - 1;
        columns.add(var);
        scores.add(-1f);
        sortColumn(var);        
      }
      tree.updateColumns();
      return idx;
    }
    return columns.indexOf(var);
  }

  public void addColumns(ArrayList<Variable> vars) {
    boolean some = false;
    boolean sorted = sortVar != null;
    for (Variable var: vars) {
      if (!var.include || columns.contains(var)) continue;
      var.column = true;      
      int idx = 0;
      if (sorted) {
        if (sorting()) cancelCurrentSort();
        // The column is added at the end, but the sorting will put it at its
        // correct location
        columns.add(var);
        scores.add(-1f);        
      } else {
        int ridx = getVariableIndex(var);
        while (idx < columns.size() && getVariableIndex(columns.get(idx)) < ridx) idx++;
        columns.add(idx, var);
        scores.add(idx, -1f);
      }
      some = true;      
    }
    if (some) {
      if (sorted) resort();   
      tree.updateColumns();
    }
  }
  
  public void removeColumn(Variable var) {
    if (columns.contains(var)) {
      boolean sort = sorting();
      if (sort) cancelCurrentSort();  
      int idx = columns.indexOf(var);      
      columns.remove(var);
      scores.remove(idx);
      var.column = false;
      tree.updateColumns();
      if (sort) resort();
    }  
  } 
  
  public void removeColumns(ArrayList<Variable> vars) {    
    removeColumns(vars, null);
  }
  
  public void removeColumns(ArrayList<Variable> vars, Variable exvar) {    
    boolean some = false;
    for (Variable var: vars) {
      if (var == exvar) continue;
      if (!columns.contains(var)) continue;
      some = true;
      break;
    }    
    if (some) {
      boolean sort = sorting();
      if (sort) cancelCurrentSort();
      for (Variable var: vars) {
        if (var == exvar || !columns.contains(var)) continue;
        int idx = columns.indexOf(var);      
        columns.remove(var);
        scores.remove(idx);
        var.column = false;    
        some = true;
      }
      tree.updateColumns(); 
      if (sort) resort();
    }    
  }  
  
  public float getScore(Variable var) {    
    return scores.get(columns.indexOf(var));  
  }

  public float getScore(int i) {    
    return scores.get(i);  
  } 
  
  public int getCovariateCount() {
    return covars.size();
  }
  
  public int getCovariate(Variable var) {
    return covars.indexOf(var);
  }
  
  public Variable getCovariate(int i) {
    return covars.get(i);
  }
  
  public int addCovariate(Variable var) {
    if (!var.include) return -1;
    if (!covars.contains(var)) {
      covars.add(var);
      var.covariate = true;
      return covars.size() - 1;
    }
    return covars.indexOf(var);
  }
  
  public void removeCovariate(Variable var) {
    if (covars.contains(var)) {
      covars.remove(var);
      var.covariate = false;
    }    
  }  
  
  public DataTree getTree() {
    return tree;
  }  
    
  public Variable[] getVariables(String query) {
    query = query.trim();
    if (query.equals("")) return new Variable[0];    
    Collections.fill(selected, new Boolean(false));    
    for (int i = 0; i < tree.variables.size(); i++) {
      Variable var = tree.getVariable(i);
      if (var.matchName(query) || var.matchAlias(query)) {
        selected.set(i, true);
      }
    }
    int count = 0;
    for (int i = 0; i < selected.size(); i++) {
      if (selected.get(i)) count++;
    }
    Variable[] result = new Variable[count];
    if (0 < count) {
      int n = 0;
      for (int i = 0; i < selected.size(); i++) {
        if (selected.get(i)) result[n++] = tree.getVariable(i);
      }        
    }
    return result;
  } 
  
  public Table[] getTable(ArrayList<Variable> selvars, DataRanges ranges) {
    DataRanges oranges = new DataRanges(ranges);
    
    Table datatab = new Table();
    datatab.setMissingString(data.getMissingString());
    
    for (Variable var: selvars) {
      String name = var.getName();
      datatab.addColumn(name, Table.STRING);
    }
    
    int count = 0;
    boolean[] mask = new boolean[data.getRowCount()]; 
    for (int r = 0; r < data.getRowCount(); r++) {
      TableRow src = data.getRow(r);
      mask[r] = insideRanges(src, oranges);
      if (mask[r]) count++;
    }
    datatab.setRowCount(count);
    
    int r1 = 0;
    for (int r0 = 0; r0 < data.getRowCount(); r0++) {
      if (!mask[r0]) continue;
      TableRow src = data.getRow(r0);
      TableRow dest = datatab.getRow(r1);
      r1++;
      
      int destCol = 0;
      for (Variable var: selvars) {
        int srcCol = var.getIndex();
        String value = var.missing(src) ? project.missingString :
                                          src.getString(srcCol);
        dest.setString(destCol, value);
        destCol++;
      }
    }
    
    Table dicttab = new Table();
    dicttab.setColumnCount(3);
    dicttab.setColumnType(0, Table.STRING);
    dicttab.setColumnType(1, Table.STRING);
    dicttab.setColumnType(2, Table.STRING); 
    for (Variable var: selvars) {
      TableRow dest = dicttab.addRow();
      dest.setString(0, var.getAlias());
      dest.setString(1, Variable.formatType(var.type()));      
      dest.setString(2, var.formatRange(false));      
    }
    
    return new Table[] {datatab, dicttab};
  }
  
  public Table getProfile(ArrayList<Variable> selvars) {    
    Table prof = new Table();
    prof.setColumnCount(3);
    prof.setColumnType(0, Table.STRING);
    prof.setColumnType(1, Table.STRING);
    prof.setColumnType(2, Table.FLOAT);
    for (Variable var: selvars) {
      int idx = getColumn(var);
      if (idx == -1) continue;
      TableRow row = prof.addRow();
      row.setString(0, var.getName());
      row.setString(1, var.getAlias());
      row.setFloat(2, getScore(getColumn(var)));
    }
    return prof;
  }
  
  public float getMissing(Variable var, DataRanges ranges) {
    DataRanges oranges = new DataRanges(ranges);
    int ntot = 0;
    int nmis = 0;    
    for (int r = 0; r < data.getRowCount(); r++) {
      TableRow row = data.getRow(r);
      if (!insideRanges(row, oranges)) continue;
      ntot++;
      if (var.missing(row)) nmis++;
    }
    float missing = (float)nmis / (float)ntot;    
    return missing;
  }
  
  public DataSlice1D getSlice(Variable varx, DataRanges ranges) {
    return new DataSlice1D(data, varx, ranges, labelVar);   
  }
  
  public DataSlice2D getSlice(Variable varx, Variable vary, DataRanges ranges) {
    return new DataSlice2D(data, varx, vary, ranges, labelVar);
  }  
  
  public void sort(Variable var, DataRanges ranges, float pvalue, float misst) {
    if (!var.include) {
      Log.message("Variable " + var.getName() + " is not included in the calculations, skipping sorting");
      return;
    }
    
    cancelCurrentSort();
    
    if (sortVar != null) sortVar.sortKey = false;
    var.sortKey = true;
    sortVar = var;    
    sortRanges = new DataRanges(ranges);
    sortPValue = pvalue;
    sortMissingThreshold = misst;

    launchScorePool(true);
    sortTask = new SortTask();
    sortTask.start();
  }

  public void resort() {
    if (sortVar != null) {
      cancelCurrentSort();
      launchScorePool(true);
      sortTask = new SortTask();
      sortTask.start();
    }
  }

  public void resort(DataRanges ranges) {
    if (sortVar != null) {
      // TODO: there might be a little delay specially when resorting after 
      // a drag operation and threads might be synchronizing on the access to the
      // data ranges.
      sortRanges = new DataRanges(ranges);
      cancelCurrentSort();            
      threadedSort = false;
      nonthreadedCount = 0;
      Collections.fill(scores, new Float(-1f));
      sortTask = new SortTask(SortTask.INSERTION);
      sortTask.start();            
    }
  } 
  
  public void resort(float pvalue, float misst) {
    if (sortVar != null) {      
      sortPValue = pvalue;
      sortMissingThreshold = misst;
      cancelCurrentSort();
      launchScorePool(true);
      sortTask = new SortTask();
      sortTask.start();
    }
  }
  
  public void sortColumn(Variable var) {
    if (sortVar == null) {
      Log.message("The columns are currently unsorted");
      return;
    }
    
    if (!var.column) {
      Log.message("Variable " + var.getName() + " is not included in the columns, skipping sorting");
      return;
    }

    float frac = sortProgress();    
    cancelCurrentSort();    
    launchScorePool(false);    
    sortTask = new SortTask(frac < 0.9 ? SortTask.QUICKSORT : SortTask.INSERTION);
    sortTask.start();
  }
  
  public void unsort() {
    if (sortVar != null) { 
      sortVar.sortKey = false;
      sortVar = null;
      sortTask = new SortTask(true);
      sortTask.start();      
    }    
  }
  
  public void stopSorting() {
    cancelCurrentSort();
    if (sortVar != null) { 
      sortVar.sortKey = false;
      sortVar = null;
    }
  }
  
  protected void cancelCurrentSort() {
    cancelSort = true;
    
    if (scorePool != null && !scorePool.isTerminated()) {
      Log.message("Suspending scoring calculations...");
      scorePool.shutdownNow();
      while (!scorePool.isTerminated()) {
        Thread.yield();
      }      
      Log.message("Done.");
    }
    
    if (sortTask != null && sortTask.isAlive()) {
      Log.message("Suspending sorting calculations...");
      sortTask.interrupt();
      while (sortTask.isAlive()) {
        Thread.yield();
      }
      Log.message("Done.");
    }
    
    cancelSort = false;
  }
  
  protected void launchScorePool(boolean clear) {
    // Calculating all the (missing) scores with a thread pool.
    threadedSort = true;
    if (clear) Collections.fill(scores, new Float(-1f));
    int proc = Runtime.getRuntime().availableProcessors();
    scorePool = (ThreadPoolExecutor)Executors.newFixedThreadPool(Math.min(1, proc - 1));
    for (int i = 0; i < columns.size(); i++) {
      if (-1 < scores.get(i)) continue; 
      final int col = i;
      scorePool.execute(new Runnable() {
        public void run() {
          Variable vx = columns.get(col);
          DataSlice2D slice = getSlice(vx, sortVar, sortRanges);
          float score = 0f;
          if (slice.missing < sortMissingThreshold) {
            score = Similarity.calculate(slice, sortPValue, project);            
          }
          scores.set(col, score);
        }
      });      
    }
    scorePool.shutdown();
  }
    
  public boolean sorting() {
    return (scorePool != null && !scorePool.isTerminated()) || 
           (sortTask != null && sortTask.isAlive());
  }
  
  public float sortProgress() {    
    if (sortVar == null) return 0;    
    if (threadedSort) {
      if (scorePool != null && !scorePool.isTerminated()) {
        float f = (float)scorePool.getCompletedTaskCount() / (scorePool.getTaskCount());
        return Numbers.map(f, 0, 1, 0, 0.99f);
      } else if (sortTask != null && sortTask.isAlive()) {
        return 0.99f;  
      } else {
        return 1f;
      }      
    } else {
      return Numbers.constrain((float)nonthreadedCount / (float)scores.size(), 0, 1);
    }
  }
  
  public boolean unsorted() {
    return sortVar == null;
  }
  
  public boolean sorted() {
    return sortVar != null && !sorting(); 
  }
  
  public Variable sortKey() {
    return sortVar;
  }
  
  final static protected boolean insideRanges(TableRow row, DataRanges ranges) {
    boolean inside = true;
    for (Range range: ranges.values()) {
      if (!range.inside(row)) return false;  
    }
    return inside;    
  }
  
  protected void loadCodebook() {
    codebook = new HashMap<String, CodebookPage>();
    if (project.hasCodebook()) {
      Table codb = loadTable(project.getCodebookPath());
      boolean readingRanges = false;
      CodebookPage page = null;
      for (int r = 0; r < codb.getRowCount(); r++) {
        TableRow row = codb.getRow(r);
        String val0 = row.getString(0); 
        String val1 = row.getString(1);
        
        boolean sep = val0.equals("") && val1.equals("");        
        boolean tab = val0.equals("code") && val1.equals("description");
        boolean alias = val0.equals("alias");
        boolean weight = val0.equals("weight");
        boolean label = val0.equals("label");
        boolean title = !sep && !tab && !alias && !readingRanges;
        
        if (sep) {
          readingRanges = false;
        } else if (title) {
          String varname = val0;
          int vartype = Variable.getType(val1);
          page = new CodebookPage(varname, vartype);
          codebook.put(varname, page);
        } else if (alias) {
          page.alias = val1;
        } else if (weight) {
          page.weight = val1;
        } else if (label) {
          page.label = true;
        } else if (tab) {
          readingRanges = true;
        } else if (readingRanges) {
          page.addToRange(val0, val1);
        }    
      }
    } 
  }
  
  protected void loadData() {
    Log.message("Loading data...");
    
    boolean useBinary = project.hasBinary();
    
    String dataPath = project.hasSource() ? project.getSourcePath() : "";
    String dictPath = project.hasDictionary() ? project.getDictionaryPath() : "";
    String binPath = project.hasBinary() ? project.getBinaryPath() : "";
    
    if (useBinary && (new File(binPath)).exists()) {   
      Log.message("  Reading binary file...");
      data = loadTable(binPath);
    } else {
      Log.message("  Reading data file...");

      if ((new File(dictPath)).exists()) {
        // Fast (typed) loading
        data = loadTable(dataPath, "header,dictionary=" + dictPath, project.missingString);
      } else {
        // Uses the codebook, or guess types from the values, which could be 
        // potentially very slow for large tables
        data = loadTableNoDict(dataPath, "header", project.missingString);
      }
      
      if (useBinary) {
        Log.message("  Saving data in binary format...");
        saveTable(data, binPath, "bin");
      }
    }
    
    Variable.setMissingString(project.missingString);
    DateVariable.setParsePattern(project.dateParsePattern);
    DateVariable.setPrintPattern(project.datePrintPattern);    
    allvars = new ArrayList<Variable>();  
    for (int col = 0; col < data.getColumnCount(); col++) {
      String name = data.getColumnTitle(col);      
      int type = data.getColumnType(col);
      if (name == null || name.equals("")) {
        Log.warning("Variable " + col + " in the data has empty name, will ignore it");
        continue;
      }
      Variable var = Variable.create(col, name, type);
      allvars.add(var);
    }
    
    for (Variable var: allvars) {
      var.initRange(data);
      Log.message("  Variable " + var.getName() + " " + Variable.formatType(var.type()) + " " + var.formatRange());
    }
    
    covars = new ArrayList<Variable>(); 
  }
   
  protected void loadGroups() {
    if (project.hasGroups() && new File(project.getGroupsPath()).exists()) {
      Log.message("Loading groups...");
      for (Variable var: allvars) var.include = false;
      
      tree = new DataTree();
      int tableCount = 0;
      int varCount = 0; 
      XML xml = loadXML(project.getGroupsPath());
      XML[] groups = xml.getChildren("group");
      for (XML group: groups) {
        String groupName = group.getString("name");
        Log.message(groupName);
        
        int tableCount0 = tableCount;        
        XML[] tables = group.getChildren("table");
        for (XML table: tables) {
          String tableName = table.getString("name");          
          Log.message("  " + tableName);
          
          tableCount++;
          int varCount0 = varCount;          
          XML[] vars = table.getChildren("variable");
          for (XML varx: vars) {
            String varName = varx.getString("name");
           
            Variable var = getVariableImpl(varName);
            if (var == null) {
              // Skipping variable not present in the dataset.
              Log.message("Variable "+ varName + " in the groups " + 
                          "is not found in the data");
              continue;
            }
            if (var.string()) {
              // Skipping variable not present in the dataset.
              Log.message("Variable "+ varName + " is not included because it is string");
              continue;
              
            }
            varCount++;
            var.include = true;
            tree.addVariable(var);
          }
          tree.addTable(tableName, varCount0, varCount - 1);
        }
        tree.addGroup(groupName, tableCount0, tableCount - 1);
      }
      
    }
    if (tree == null || tree.groups.size() == 0 || 
                        tree.tables.size() == 0 || 
                        tree.variables.size() == 0) {
      Log.message("No groups found, building default tree...");
      tree = new DataTree(allvars);
    }    
  }
  
  protected void loadMetadata() {
    if (project.hasDictionary()) {
      // Loading metadata (alias, range and weights) from dictionary file.
      Table dict = loadTable(project.getDictionaryPath());
      for (int r = 0; r < dict.getRowCount(); r++) {
        Variable var = allvars.get(r);
        TableRow row = dict.getRow(r);
        int count = row.getColumnCount();        
        String alias = row.getString(0);
        var.setAlias(alias);
        if (2 < count) {
          if (var.string()) {
            String str = row.getString(2);
            if (str != null && str.equals("label")) {
              Log.message("Set variable " + var.getName() + " as label");
              labelVar = var;              
            }
          } else {
            // Getting range string
            String range = row.getString(2);
            if (range != null && !range.equals("")) var.initValues(range);
            if (3 < count) {            
              // Getting weighting information
              String weight = row.getString(3);
              if (weight != null && !weight.equals("")) initWeight(var, weight);
            }            
          }
        }
      }
    }
    
    for (String name: codebook.keySet()) {
      Variable var = getVariable(name);
      if (var != null) {
        CodebookPage pg = codebook.get(name);
        if (pg.hasAlias()) var.setAlias(pg.alias);
        if (var.string()) {
          if (pg.isLabel()) {
            Log.message("Set variable " + var.getName() + " as label");
            labelVar = var;
          }
        } else {
          if (pg.hasRange()) var.initValues(pg.range);
          if (pg.hasWeight()) initWeight(var, pg.weight);          
        }
      }
    } 
  }
  
  protected void initColumns() {
    int size = tree.variables.size(); 
    columns = new ArrayList<Variable>();    
    for (int i = 0; i < size; i++) {
      Variable var = tree.getVariable(i);
      columns.add(var);
      var.column = true;
    }
    
    scores = new ArrayList<Float>(Collections.nCopies(size, -1f));
    selected = new ArrayList<Boolean>(Collections.nCopies(size, false));
  }
  
  protected Variable getVariableImpl(String name) {
    for (Variable var: allvars) {
      if (var.name.equals(name)) return var;
    }
    return null;
  }
  
  protected void initWeight(Variable var, String weightStr) {
    boolean isWeight = false;
    boolean isSubsample = false;
    boolean weightedBy = false;
    if (weightStr.toLowerCase().equals("sample weight")) {
      isWeight = true;
      isSubsample = false;
    } if (weightStr.toLowerCase().equals("subsample weight")) {
      isWeight = true;
      isSubsample = true;      
    } else {
      weightedBy = !weightStr.equals("");
    }
    
    if (isWeight) {
      var.setWeight();
      if (isSubsample) var.setSubsample();
      Log.message("    Setting variable " + var.getName() + " as weight, subsample " + isSubsample);
    } else if (weightedBy) {      
      Variable wvar = getVariableImpl(weightStr);              
      if (wvar != null) {
        var.setWeightVariable(wvar);
      }
      else {
        Log.message("Weight variable "+ weightStr + " in the metadata " + 
                    "is not found in the data");                
      }
    }            
  }
  
  //////////////////////////////////////////////////////////////////////////////

  
  protected MiraTable loadTable(String filename, String options, String missingStr) {
    try {
      String optionStr = Table.extensionOptions(true, filename, options);
      String[] optionList = splitOptions(optionStr);  

      MiraTable dict = null;
      for (String opt : optionList) {
        if (opt.startsWith("dictionary=")) {
          dict = loadDict(opt.substring(opt.indexOf('=') + 1));
          return MiraTable.typedParse(createInput(filename), dict, optionStr, missingStr);
        }
      }
      return new MiraTable(createInput(filename), optionStr);

    } catch (IOException e) {
      Log.error("Cannot load data file", e);
      return null;
    }
  }

  protected boolean saveTable(Table table, String filename, String options) {
    try {
      File outputFile = saveFile(filename);
      return table.save(outputFile, options);
    } catch (IOException e) {
      e.printStackTrace();
      return false;
    }
  }
  
  protected File saveFile(String where) {
    if (where == null) return null;
    String filename = where;
    Fileu.createPath(new File(filename));
    return new File(filename);
  }
    
  protected MiraTable loadTable(String filename) {
    return loadTable(filename, null);
  } 
  
  protected MiraTable loadTable(String filename, String options) {
    try {
      String optionStr = Table.extensionOptions(true, filename, options);
      String[] optionList = splitOptions(optionStr);

      MiraTable dictionary = null;
      for (String opt : optionList) {
        if (opt.startsWith("dictionary=")) {
          dictionary = loadTable(opt.substring(opt.indexOf('=') + 1), "tsv");
          return dictionary.typedParse(createInput(filename), optionStr);
        }
      }
      return new MiraTable(createInput(filename), optionStr);

    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }  
  
  protected XML loadXML(String filename) {
    return loadXML(filename, null);
  }
 
  protected XML loadXML(String filename, String options) {
    try {
      return new XML(createReader(filename), options);
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }
  
  protected InputStream createInput(String filename) {
    if (filename == null || filename.length() == 0) return null;
    
    InputStream input = null;
    try {
      input = new FileInputStream(filename);
    } catch (FileNotFoundException e1) {
      e1.printStackTrace();
    }
    
    if ((input != null) && filename.toLowerCase().endsWith(".gz")) {
      try {
        return new GZIPInputStream(input);
      } catch (IOException e) {
        e.printStackTrace();
        return null;
      }
    }
    
    return input;
  } 
  
  public BufferedReader createReader(String filename) {    
    InputStream input = createInput(filename);
    if (input != null) {
      InputStreamReader isr = null;
      try {
        isr = new InputStreamReader(input, "UTF-8");
        return new BufferedReader(isr);
      } catch (UnsupportedEncodingException e) {
        e.printStackTrace();
      }      
    }
    return null;
  }  
   
  static protected String[] splitOptions(String str) {
//  PApplet.trim(PApplet.split(optionStr, ','));
    String[] array = str.split(",");
    String[] outgoing = new String[array.length];
    for (int i = 0; i < array.length; i++) {
      if (array[i] != null) {
        outgoing[i] = array[i].replace('\u00A0', ' ').trim();
      }
    }
    return outgoing;    
  }  
  
  //////////////////////////////////////////////////////////////////////////////
  
  protected MiraTable loadTableNoDict(String filename, String options, String missingStr) {
    String optionStr = Table.extensionOptions(true, filename, options);
    return MiraTable.guessedParse(createInput(filename), codebook, optionStr, missingStr);      
  }
  
  protected MiraTable loadDict(String filename) {
    try {
      String optionStr = Table.extensionOptions(true, filename, null);
      return new MiraTable(createInput(filename), optionStr);
    } catch (IOException e) {
      Log.error("Cannot load dictionary file", e);      
      return null;
    }
  } 
  
  protected class CodebookPage {
    String name;
    String alias;
    int type;
    String range;
    String weight;
    boolean label;
    
    CodebookPage(String name, int type) {
      this.name = name;
      this.alias = "";
      this.type = type;
      this.range = "";
      this.weight = "";
    }
    
    boolean hasAlias() {
      return !alias.equals("");
    }
   
    boolean hasRange() {
      return !range.equals("");
    }    
    
    boolean hasWeight() {
      return !weight.equals("");
    }
    
    boolean isLabel() {
      return label;
    }
    
    void setRange(String range) {
      this.range = range;
    }
    
    void addToRange(String code, String value) {
      if (!range.equals("")) range += ";";
      range += code + ":" + value;
    }
  }
  
  protected class SortTask extends Thread {    
    final static int QUICKSORT = 0;
    final static int INSERTION = 1;
    int algo;
    boolean order;
    
    SortTask() {
      this(QUICKSORT);
    }
    
    SortTask(boolean order) {
      this(QUICKSORT, order);
    }    
    
    SortTask(int algo) {
      this(algo, false);
    }
    
    SortTask(int algo, boolean order) {
      this.algo = algo;
      this.order = order;
    }
    
    @Override
    public void run() {
      // The sort task will wait for the score pool to execute all of its 
      // threads.
      while (!scorePool.isTerminated()) {
        Thread.yield();
      }
      if (algo == QUICKSORT) {
        quicksort(0, columns.size() - 1);
      } else if (algo == INSERTION) {
        insertion();
      }
    }
    
    protected void insertion() {
      for (int i = 1; i < columns.size(); i++) {
        if (cancelSort) return;
        float temps = getScore(i);
        Variable tempv = columns.get(i);
        int j;
        for (j = i - 1; j >= 0 && temps > getScore(j); j--) {
          if (cancelSort) return;
          scores.set(j + 1, getScore(j));
          columns.set(j + 1, columns.get(j));          
        }
        scores.set(j + 1, temps);
        columns.set(j + 1, tempv);        
      }
    }
    
    protected void quicksort(int leftI, int rightI) {
      if (leftI < rightI) {
        if (cancelSort) return;
        int pivotIndex = (leftI + rightI)/2;
        int newPivotIndex = partition(leftI, rightI, pivotIndex);
        quicksort(leftI, newPivotIndex - 1);
        quicksort(newPivotIndex + 1, rightI);        
      }
    }
    
    protected int partition(int leftIndex, int rightIndex, int pivotIndex) {
      float pivotVal = getScore(pivotIndex);
      swap(pivotIndex, rightIndex);
      int storeIndex = leftIndex;
      for (int i = leftIndex; i < rightIndex; i++) {
        if (cancelSort) return pivotIndex;
        if (getScore(i) > pivotVal) {
          swap(i, storeIndex);
          storeIndex++;
        }
      }
      swap(rightIndex, storeIndex);
      return storeIndex;
    }
    
    protected void swap(int a, int b) {
      Variable tmpv = columns.get(a);
      columns.set(a, columns.get(b));      
      columns.set(b, tmpv);
      
      float tmps = getScore(a);
      scores.set(a, getScore(b));
      scores.set(b, tmps);
    }
    
    float getScore(int col) {
      if (order) {
        Variable var = columns.get(col);
        return 1.0f - (float)getVariableIndex(var) / (float)getVariableCount();
      } else {
        float score = scores.get(col);
        if (0 <= score) return score;  
        
        
        Variable vx = columns.get(col);
        DataSlice2D slice = getSlice(vx, sortVar, sortRanges);        
        if (slice.missing < sortMissingThreshold) {
          score = Similarity.calculate(slice, sortPValue, project);
        } else {
          score = 0f;  
        }
        scores.set(col, score);
        nonthreadedCount++;
        return score;        
      }
    } 
  }
}
