/* COPYRIGHT (C) 2014 Fathom Information Design. All Rights Reserved. */

package miralib.utils;

import java.awt.Frame;

import javax.swing.JOptionPane;

/**
 * Message logging.
 *
 */

public class Log {
//look at this:
//out = new FileOutputStream("log.txt");
//ps = new PrintStream( out );
//System.setErr(ps); //redirects stderr to the log file as well
  
  static protected Messages messages;
  
  static public void init() {
    messages = new Messages();    
  }
  
  static public void message(String msg) {
    messages.push(msg);
  }
  
  static public void warning(String msg) {
    messages.push(msg);
  }

  static public void error(String msg, Throwable e) {
    messages.push(msg);
    JOptionPane.showMessageDialog(new Frame(), msg, "mirador",
                                  JOptionPane.ERROR_MESSAGE);    
    e.printStackTrace();    
    System.exit(0);
  } 
  
  static protected class Messages {
//  protected PApplet parent;
  
    public Messages(/*PApplet parent*/) {
//    this.parent = parent;  
    }
  
    public void push(String msg) {
      System.out.println(msg);  
    }
  }  
}
