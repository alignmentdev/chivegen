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
  // or horizonal line. In case of natural in-text line breaks, insert <br>.
  public static String convertToHtml(String text) {
    //System.out.println("CONVERSION: ");
    if (text.equals("")) { // if it's blank, don't even bother
      return text;
    }
    StringBuilder formatted = new StringBuilder();
    Scanner lineReader = new Scanner(text);
    String current;
    String currentClean;
    boolean inParagraph = false;
    while (lineReader.hasNextLine()) {
      current = lineReader.nextLine();
      currentClean = current.trim();
      // If this is clearly not part of a paragraph, just append as-is
      if (detectNonParagraphHtmlTags(currentClean)) {
        //System.out.println("Non-paragraph content: " + current);
        formatted.append("\n" + current);
      } else if (inParagraph) {
        //System.out.println("Paragraph content: " + current);
        // If we're already in a paragraph, break lines with <br> unless we
        // reach an entirely blank line, in which case the paragraph ends.
        if (currentClean.equals("")) {
          formatted.append("</p>");
          inParagraph = false;
        } else if (paragraphEnds(currentClean)) {
          inParagraph = false;
        } else {
          formatted.append("\n<br>" + current);
        }
      } else if (!currentClean.equals("")) {
        // If we have content on this line, we are entering a new paragraph.
        //System.out.println("Paragraph ENTERED, line 1: " + current);
        formatted.append("\n<p>" + current);
        // If this paragraph doesn't end itself with a </p> tag, mark that we
        // are scanning inside a paragraph right now
        if (!paragraphEnds(currentClean)) {
          inParagraph = true;
        }
      } else {
        // Blank line.
        //System.out.println("Blank line. Adding <br>");
        formatted.append("\n<br>\n");
      }
    }
    //System.out.println("RESULTS: ");
    //System.out.println(formatted.toString());
    lineReader.close();
    return formatted.toString();
  }
  
  // Returns whether or not the given string ends with "</p>".
  private static boolean paragraphEnds(String s) {
    return s.length() > 3 && s.substring(s.length() - 4).equals("</p>");
  }
  
  // Returns true if the string starts with a recognized non-paragraph HTML
  // opening tag.
  private static boolean detectNonParagraphHtmlTags(String s) {
    if (s.equals("")) {
      return false;
    }
    // Strip leading whitespace, and get a substring of only the first
    // maxTagLength+1 characters
    String comparisonSubstring = s.trim();
    if (comparisonSubstring.charAt(0) == '<') {
      // Only check for HTML tags if we clearly start with one
      if (comparisonSubstring.length() > LONGEST_OPENING_TAG_LENGTH) { 
        // only get the substring if it's longer than our max tag length
        comparisonSubstring = comparisonSubstring.substring(0, LONGEST_OPENING_TAG_LENGTH);
      }
      //System.out.println("Is this a tag we know?: " + comparisonSubstring);
      // Check progressively shorter versions of the string against the set
      // of non-paragraph HTML tags
      int j = comparisonSubstring.length();
      for (int i = j; i >= SHORTEST_OPENING_TAG_LENGTH; i--) {
        comparisonSubstring = comparisonSubstring.substring(0, i);
        //System.out.println("Is this a tag we know?: " + comparisonSubstring);
        if (nonParagraphHTMLTags.contains(comparisonSubstring)) {
          return true;
        }
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
