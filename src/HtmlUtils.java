/***

  A class for some basic HTML related utilities such as "casual HTML" text
  conversion and (somewhat nonstandard) URL character escaping.

***/

import java.util.*;
import java.util.regex.Pattern;
import java.io.*;

public class HtmlUtils {

  // Our collection of recognized non-paragraph HTML tag openings.
  // In order: line break, horizontal line, heading, end of a tag, div, image, 
  // list item, unordered list, ordered list, iframe, blockquote, table, 
  // table row, table data, table heading.
  // <no></no> can be used to tell ChiveGen to ignore a line for formatting
  // regardless of what else it starts with
  private static String[] acceptedOpeningHTMLTags = new String[] {"<br", 
  "<hr", "<h", "</", "<di", "<im", "<li", "<ul", "<ol",
  "<if", "<bl", "<ta", "<tr", "<td", "<th", "<no"};
  // To reduce magic numbers
  private static final int LONGEST_OPENING_TAG_LENGTH = 3;
  private static final int SHORTEST_OPENING_TAG_LENGTH = 2;
  
  // Contains all recognized non-paragraph HTML opening tag starts
  private static HashSet<String> nonParagraphHTMLTags =
    new HashSet<String>(Arrays.asList(acceptedOpeningHTMLTags));


  // Wraps each line of a string in HTML paragraph tags, unless it appears to
  // be a non-paragraph entity such as an empty line, a div tag, a line break,
  // or horizonal line.
  public static String convertToHTML(String text) {
    if (text.equals("")) { // if it's blank, don't even bother
      return text;
    }
    StringBuilder formattedOutput = new StringBuilder();
    Scanner currentReader = new Scanner(text);
    Scanner nextLineReader = new Scanner(text);
    String current = "";
    String current2 = "";
    String next = "";
    int currentPosition = 0;
    int nextPosition = 0;
    boolean isParagraph;
    while (nextLineReader.hasNextLine()) {
      isParagraph = false;
      // Get the next line
      next = nextLineReader.nextLine(); 
      nextPosition++;
      // Skip nextLineReader ahead to the next paragraph break/empty line
      // if possible
      while (nextLineReader.hasNextLine() 
           && !next.trim().equals("")) {
        next = nextLineReader.nextLine(); 
        nextPosition++;
      }
      // Now we look through the lines between current's starting point 
      // and next.
      // currentReader reads the first line of the current paragraph;
      // moves a line forward
      if (currentReader.hasNextLine()) {
        current = currentReader.nextLine();
        currentPosition++;
      }
      // Check for any opening tags that would suggest we aren't in a
      // paragraph
      isParagraph = !detectNonParagraphHTMLTags(current);
      // If the current line starts with <p>, don't add it.
      if (isParagraph && !current.trim().startsWith("<p")) {
        formattedOutput.append("<p>");
      }
      formattedOutput.append(current);
      // If we have more lines than the first, keep reading and adding 
      // <br> until nextPos is reached.
      while (currentPosition < nextPosition) {
        // keep track of the last line in case it ends with a </p> tag
        if (currentPosition == nextPosition - 1) {
          current2 = current;
        }
        // Get the next line
        current = currentReader.nextLine();
        currentPosition++;
        
        // If this is clearly not a paragraph, or was preceded by something
        // with existing HTML formatting, just append it without any further
        // formatting
        if (detectNonParagraphHTMLTags(current.trim())
            || detectNonParagraphHTMLTags(current2)) {
          formattedOutput.append("\n" + current);
        } else {
          formattedOutput.append("\n<br>" + current);
        }
      }
      if (isParagraph) {
        // if the 2nd most recent line of currentReader doesn't end in
        // </p>, add it.
        if (nextLineReader.hasNextLine() && !current2.endsWith("/p>")) {
          formattedOutput.append("</p>\n");
        }
        // Otherwise, if we're at the end of the string, check the last
        // line
        if (!nextLineReader.hasNextLine() && !current.endsWith("/p>")) {
          formattedOutput.append("</p>");
        }
      }
    }
    return formattedOutput.toString();
  }
  
  // Returns true if a string starts with HTML formatting that would make it
  // not need paragraph tags
  public static boolean detectNonParagraphHTMLTags(String s) {
    if (s.equals("") || nonParagraphHTMLTags.contains(s)) { 
      // treat a blank line as not requiring html
      return true;
    }
    // Strip leading whitespace, and get a substring of only the first
    // maxTagLength+1 characters
    String comparisonSubstring = s.trim();/*.replaceAll("\\s", ""); */
    if (comparisonSubstring.charAt(0) == '<') { 
      // Only check for HTML tags if we clearly start with one
      if (comparisonSubstring.length() > LONGEST_OPENING_TAG_LENGTH) { 
        // only get the substring if it's longer than our max tag length
        comparisonSubstring = comparisonSubstring.substring(0, LONGEST_OPENING_TAG_LENGTH);
      }
      // Check progressively shorter versions of the string against the set
      // of non-paragraph HTML tags
      int j = comparisonSubstring.length();
      for (int i = j; i > SHORTEST_OPENING_TAG_LENGTH; i--) {
        if (nonParagraphHTMLTags.contains(comparisonSubstring)) {
          return true;
        }
        comparisonSubstring = comparisonSubstring.substring(0, i);
      }
    }
    return false;
  }
  
  // Formats the wordcount as a String with commas (i.e. 1,234,567)
  public static String numberWithCommas(int n) {
    StringBuilder numberWithCommas = new StringBuilder();
    String numberAsString = String.valueOf(n);
    if (n < 1000) {  // don't even bother
      return numberAsString;
    }
    // Get the number of digits before the first comma
    int initialOffset = numberAsString.length() % 3;
    if (initialOffset == 0) {
      initialOffset = 3;
    }
    numberWithCommas.append(numberAsString.substring(0, initialOffset));
    // If there are >3 digits left, keep appending in chunks of 3 at a time
    // with preceding commas
    if (numberAsString.length() > 3) {
      for (int i = initialOffset; i < numberAsString.length(); i+=3) {
        numberWithCommas.append(",");
        numberWithCommas.append(numberAsString.substring(i, i+3));
      }
    }
    return numberWithCommas.toString();
  }
  
  // Takes a string and converts it to Title Case
  public static String toTitleCase(String text) {
    if (text.equals("")) {
      return text;
    }
    StringBuilder titleBuilder = new StringBuilder();
    boolean atStartOfWord = true;
    char c = ' ';
    for (int i = 0; i < text.length(); i++) {
      c = text.charAt(i);
      // Check that c is actually a letter!
      // 64-91 is uppercase, 96-123 is lowercase
      if (atStartOfWord) {
        if ((c > 96 && c < 123)) {
          c = (char)(c - 32);
        }        
        atStartOfWord = false;
      }
      titleBuilder.append(c);
      if (c == ' ' || c == '\t' || c == '.' || c == '(') {
        atStartOfWord = true;
      }
    }
    return titleBuilder.toString();
  }
  
  // Converts potentially unsafe input strings (like story tags) into strings
  // that are safe for URLs. Unsafe characters are replaced with "_##" where
  // "##" is the numerical value of the character. ('_' is also escaped so
  // we don't have collisions with it.)
  // Theoretically if we wanted nicer URLs we could make use of Java's actual
  // networking libraries and a proper URI conversion utility, but for now
  // this is fine.
  public static String toSafeUrl(String s) {
    StringBuilder url = new StringBuilder();
    int c = 0;
    for (int i = 0; i < s.length(); i++) {
      c = s.charAt(i); // get the int value of the ith character
      // 32 = space
      if (c == 32) {
        url.append('-');
      }
      // 45 = '-', 95 = '_', 48-57 = digits 0-9, 65-90 = A-Z, 97-122 = a-z
      else if (!((c > 96 && c < 123) || (c > 64 && c < 91) 
           || (c > 47 && c < 58))) {
        url.append("_" + c);
      }
      else {
        url.append(s.charAt(i));
      }
    }
    return url.toString();
  }
  
  // Removes a leading "the" (and forces all lowercase) for comparison
  public static String stripLeadingThe(String s) {
    s = s.toLowerCase();
    if (s.startsWith("the ")) {
      s = s.replace("the ", "");
    }
    return s;
  }
  
  // Counts and returns how many tabs a string begins with.
  // DEPRECATED, as nothing seems to use this.
  public static int countLeftTabs(String text) {
    if (text.length() == 0) {
      return 0;
    }
    char s = text.charAt(0);
    int tabs = 0;
    while (s == '\t') { 
      tabs++;
      s = text.charAt(tabs);
    }
    return tabs;
  }
}
