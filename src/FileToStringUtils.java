/***

  Utility functions for reading files to strings.

***/

import java.util.*;
import java.util.regex.Pattern;
import java.io.*;

public class FileToStringUtils {

  // If we don't need to go line by line, speed things up by using \0 as our
  // scanning delimiter
  private static Pattern fastScanningPattern =
    Pattern.compile(Character.toString((char)0));


 // Reads a string into a file, with optional casual HTML and tab depth.
  public static String readFileToString(File inputFile, int leftTabs,
                                        boolean useCasualHTML) {
    //long start = System.currentTimeMillis();
    StringBuilder fileContents = new StringBuilder();
    int lines = 0;
    try {
      Scanner inputReader = new Scanner(inputFile);
      if (leftTabs > 0) {
        while (inputReader.hasNextLine()) {
          lines++;
          //if (!ignoreTabs) {  // TODO update this condition later
            for (int i = 0; i <= leftTabs; i++) { // match tab depth of div
              fileContents.append("\t");
            }
          //}
          fileContents.append(inputReader.nextLine() + "\n");
        }
      }
      else {
        // Read in the file all in one go if possible
        inputReader.useDelimiter(fastScanningPattern);
        while (inputReader.hasNext()) {
          fileContents.append(inputReader.next());
        }
      }
      // Close the scanner once we're done
      inputReader.close();
    } catch (FileNotFoundException e) {
      System.out.println("Error: file " + inputFile.getPath()
                         + " does not exist.");
      e.printStackTrace();
    }
    // replace with ChiveGenMain once ready
    if (FicArchiveBuilder.isVerbose()) {
      System.out.println("Read " + lines + " lines from " + inputFile.getPath()
                         + " to string:");
    }
    // If using casual HTML, just run it through the converter
    if (useCasualHTML) {
      return HtmlUtils.convertToHtml(fileContents.toString());
    }
    return fileContents.toString();
  }

  //@override
  public static String readFileToString(File inputFile, int leftTabs) {
    // default to no casual html
    return readFileToString(inputFile, leftTabs, false);
  }

  //@override
  public static String readFileToString(File inputFile, boolean useCasualHTML) {
    return readFileToString(inputFile, 0, useCasualHTML);
  }

  //@override
  public static String readFileToString(File inputFile) {
    return readFileToString(inputFile, 0, false);
  }
}
