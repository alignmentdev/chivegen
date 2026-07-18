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
  "<hr", "<h", "</", "<di", "<im", "<au", "<li", "<ul", "<ol",
  "<if", "<bl", "<ta", "<tr", "<td", "<th", "<no", "<p"};
  private static String[] acceptednonClosingHtmlTags = new String[] {"<br>", "<hr>", "<img"};
  // To reduce magic numbers
  private static final int LONGEST_OPENING_TAG_LENGTH = 4;
  private static final int SHORTEST_OPENING_TAG_LENGTH = 2;

  // Contains all recognized non-paragraph HTML opening tag starts
  private static HashSet<String> acceptedHtmlTagSet =
    new HashSet<String>(Arrays.asList(acceptedOpeningHTMLTags));
  // Ditto for tags that don't have a closing tag
  private static HashSet<String> nonClosingHtmlTagSet =
    new HashSet<String>(Arrays.asList(acceptednonClosingHtmlTags));


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
    String currentClean; // whitespace-trimmed version of line
    boolean inParagraph = false;
    String currentEndTag = ""; // what end tag to check for, if any (e.g. </p>, </div>...)
    boolean noFormat = false; // true inside a block that shouldn't auto-format
    // only start doing additional <br>s outside of paragraphs if 2+ blanks
    boolean prevLineEmpty = false;
    while (lineReader.hasNextLine()) {
      current = lineReader.nextLine();
      currentClean = current.trim(); // ignore whitespace
      //System.out.println("CURRENT LINE\t" + current);
      // If we are entering a preformatted paragraph...
      if (inParagraph) {
        if (currentClean.equals("")) {
          // If we find an empty line, we have reached the end of the
          // paragraph, so put </p>
          formatted.append("</p>\n\n");
          //System.out.println("\\n\\n detected, ending paragraph...");
          inParagraph = false;
        } else {
          // If the current line has content, but the paragraph is ongoing
          // we must have found a single line break inside the paragraph
          formatted.append("\n<br/>\n");
          //System.out.println("single \\n detected, inserting newline inside paragraph...");
          formatted.append(current);
        }
      } else {
        // If we are entering a non-paragraph block (e.g. <div>, <table>, <h1>...)
        // or a newline
        currentClean = current.trim().toLowerCase(); // for ease of comparisons
        if (noFormat || currentClean.equals("") 
            || startsWithTag(currentClean, acceptedHtmlTagSet)) { // non-paragraph content
          // Outside a paragraph, ignore single newlines and only start counting
          // if there are at least two of them (so we don't get extraneous
          // newlines being inserted constantly between elements)
          if (currentClean.equals("")) {
            if (prevLineEmpty) { // 2 newlines in a row
            	//System.out.println("previous line was empty and current line is empty; adding <br>");
              formatted.append("<br/>");
            }
          } else {
            // If we aren't already inside a non-autoformatted block, check for an opening
            // tag, but only if it's a tag that actually closes.
            if (!noFormat && !startsWithTag(currentClean, nonClosingHtmlTagSet)) {
              // Keep track of our opening tag so we know when it gets closed
              // and don't start formatting before that
              // (this should give </ + tag> -> </tag>)
              currentEndTag = "</" + currentClean.substring(1, currentClean.indexOf('>') + 1);
              // In case we have a tag with attributes, e.g. <div class="">
              if (currentEndTag.indexOf(' ') != -1) {
                currentEndTag = currentEndTag.substring(0, currentEndTag.indexOf(' ')) + ">";
              }
              noFormat = true; // don't check for paragraphs in this block
              //System.out.println("opening tag detected. no formatting will be applied until we see " + currentEndTag);
            }
            // If we find the end tag to whatever HTML block we're currently inside...
            if (!currentEndTag.equals("") && currentClean.endsWith(currentEndTag)) {
              noFormat = false;
              //System.out.println("end tag " + currentEndTag + " detected, ending block...");
              currentEndTag = "";
            }
          }
          formatted.append(current + "\n");
        } else { // we have entered a new paragraph
          formatted.append("<p>" + current);
          //System.out.println("default: new paragraph");
          inParagraph = true;
        }
      }
      // Track if the previous line was empty for next round
      if (currentClean.equals("")) {
      	prevLineEmpty = true;
      } else {
      	prevLineEmpty = false;
      }
      //System.out.println();
    }
    lineReader.close();
    return formatted.toString();
  }

  // Returns whether or not the given string ends with "</p>".
  private static boolean paragraphEnds(String s) {
    return s.length() > 3 && s.substring(s.length() - 4).equals("</p>");
  }

  // Returns true if s starts with a string in matches, or false otherwise.
  // Used specifically to detect HTML opening tags for HTML parsing, hence usage
  // of LONGEST_OPENING_TAG_LENGTH, SHORTEST_OPENING_TAG_LENGTH optimizations.
  private static boolean startsWithTag(String s, HashSet<String> matches) {
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
      // Check progressively shorter versions of the string against the set
      // of non-paragraph HTML tags
      int j = comparisonSubstring.length();
      for (int i = j; i >= SHORTEST_OPENING_TAG_LENGTH; i--) {
        comparisonSubstring = comparisonSubstring.substring(0, i);
        if (matches.contains(comparisonSubstring)) {
          //System.out.println("opening tag " + comparisonSubstring + " matched");
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

}
