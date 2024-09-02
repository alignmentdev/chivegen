/***

  A class for string/page templates.

***/

import java.util.*;
import java.util.regex.Pattern;
import java.io.*;

public class ContentTemplate {
  // Strings that make up the template
  private String[] templateStrings;
  // Order in which content should be inserted. 
  // TODO: eventually rename to contentIndices or something more appropriate
  private int[] insertionPoints;
  
  // For template scanning - matches {{ or }} only
  private static Pattern templateDelimiters = Pattern.compile("\\{\\{|\\}\\}");
  
  // Old constructor. Deprecated. TODO check for use and replace.
  public ContentTemplate(ArrayList<String> strings, ArrayList<Integer> inserts) {
    templateStrings = strings.toArray(new String[0]);
    insertionPoints = new int[inserts.size()];
    // Since we can't convert directly from ArrayList<Integer> to int[], we
    // have to do this manually.
    for (int i = 0; i < insertionPoints.length; i++) {
      insertionPoints[i] = inserts.get(i);
    }
  }
  
  // Constructor. Reads a template string from the input file if possible, and
  // constructs the template from the string and keywords. If the file is not
  // valid (i.e. doesn't exist), uses the fallback string as the template
  // string instead.
  public ContentTemplate(File file, String[] keywords, String fallback) {
    // If the file exists and can be read, read it in and use it for our
    // template. Otherwise, use the fallback string.
    if (file.exists()) {
    //  if (FicArchiveBuilder.isVerbose()) {  // TODO replace verbosity check
        System.out.println("'" + file.getName() + "' found in input directory."
                           + " Reading to string...");
    //  }
      this.parseTemplateString(FileToStringUtils.readFileToString(file), keywords);
    } else {
      this.parseTemplateString(fallback, keywords);
    }
  }

  // Alternate constructor. Accepts a string and array of keyword strings, and
  // parses and sets up the internal contents of the template object with them.
  public ContentTemplate(String templateInput, String[] keywords) {
    this.parseTemplateString(templateInput, keywords);
  }
  
  // Accepts a string and array of keyword strings, and parses and
  // sets up the internal contents of the template object using them.
  private void parseTemplateString(String templateInput, String[] keywords) {
    // For quicker string -> index lookup
    HashMap<String, Integer> keywordMap = GenUtils.hashMapStringToIndex(keywords);
    // For the ContentTemplate constructor
    ArrayList<String> strings = new ArrayList<String>();
    ArrayList<Integer> inserts = new ArrayList<Integer>();
    // To track in case some keywords aren't found
    boolean[] foundKeywords = new boolean[keywords.length];
    Arrays.fill(foundKeywords, false);
    // Scanner to read the input string in chunks
    Scanner templateReader = new Scanner(templateInput);
    // Build a string of current content until keyword is reached
    StringBuilder currentSection = new StringBuilder();
    // For keyword comparisons
    String possibleKeyword;
    // For the current chunk of text
    String current;
    // Use {} delimiters
    templateReader.useDelimiter(templateDelimiters);
    // Whether or not we're checking the current chunk for a keyword
    boolean inKeyword = false;
    // In case we start with a keyword
    if (templateInput.startsWith("{{")) {
      inKeyword = true;
    }
    while (templateReader.hasNext()) {
      current = templateReader.next();
      if (inKeyword) {
        possibleKeyword = current.replaceAll("\\s", ""); // strip spaces
        //System.out.println("Checking: " + possibleKeyword);
        // Check if keyset contains the keyword
        if (keywordMap.keySet().contains(possibleKeyword)) {
          // Note the insertion point from the hashmap
          inserts.add(keywordMap.get(possibleKeyword));
          // Note that we have found this keyword
          foundKeywords[keywordMap.get(possibleKeyword)] = true;
          // Add the accumulated string to the template
          strings.add(currentSection.toString());
          // Reset the string builder
          currentSection = new StringBuilder();
        } else {
          // If it's not a template keyword,
          // Add to the template as normal
          currentSection.append(current);
        }
        // Go back to looking for the next keyword
        inKeyword = false;
      } else {
        // Add this section to the current stringbuilder
        currentSection.append(current);
        inKeyword = true;
      }
      //System.out.println("Currently reading: " + currentSection.toString());
    }
    // If there's still content in the template string, append it
    if (currentSection.length() != 0) {
      strings.add(currentSection.toString());
    }
    // Cleanup
    templateReader.close();
    // Optionally warn for any missing keywords.
    // TODO - change this to ChiveGenMain once ready
    if (FicArchiveBuilder.isVerbose()) {
      for (int i = 0; i < foundKeywords.length; i++) {
        if (foundKeywords[i] == false) {
          System.out.println("Warning: keyword " + keywords[i] 
                             + " was not found in the file.");
        }
      }
      System.out.println("Insertion points: " + inserts);
    }
    // Set internal variables
    templateStrings = strings.toArray(new String[0]);
    insertionPoints = new int[inserts.size()];
    // Since we can't convert automatically from ArrayList<Integer> to int[], we
    // have to do this manually.
    for (int i = 0; i < insertionPoints.length; i++) {
      insertionPoints[i] = inserts.get(i);
    }
  }
  
  // Write the given array of Strings into this template, and return the
  // interleaved result.
  public String assemble(String[] contentToInsert) {
    if (this.getTemplateStrings().length < 1) { 
      // don't bother for blank template
      return "";
    }
    StringBuilder content = new StringBuilder();
    // if there's nothing to insert, return the full text of the template
    if (contentToInsert.length < 1) { 
      for (int i = 0; i < this.templateStrings.length; i++) {
        content.append(this.templateStrings[i]);
      }
      return content.toString();
    }
    // Interleave the template strings and content to insert
    for (int i = 0; i < this.templateStrings.length; i++) {
      /**
      if (FicArchiveBuilder.isVerbose()) {  // TODO: ->ChiveGenMain when ready
        System.out.println("Template line: " + this.templateStrings[i]);
        if (i < this.insertionPoints.length) {
          System.out.println("Input line index: " + this.insertionPoints[i]);
        }
      }
      **/
      content.append(this.templateStrings[i]);
      // If i is in range, and the value isn't -1 (not found)
      // insert the input string
      if (i < this.insertionPoints.length && this.insertionPoints[i] != -1) {
        content.append(contentToInsert[this.insertionPoints[i]]);
      }
    }
    return content.toString();
  }
  
  // Get the template strings
  public String[] getTemplateStrings() {
    return templateStrings;
  }
  
  // Get the array of indexes of content to be inserted.
  public int[] getInsertionPoints() {
    return insertionPoints;
  }
}
