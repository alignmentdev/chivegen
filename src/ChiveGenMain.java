/***

  ChiveGen - a static fanfiction archive generator.
  Accepts an input directory and output directory, and builds an archive from
  the input files to the output directory.

  Usage: chivegen -i INPUT_DIR -o OUTPUT_DIR [additional options]
 	or,  java ChiveGenMain -i INPUT_DIR -o OUTPUT_DIR [additional options]

***/

import java.util.*;
import java.util.regex.Pattern;
import java.io.*;
import java.time.*;
import java.time.format.*;
import java.lang.Math;

public class ChiveGenMain {

  // Version string for manual and about text
  public final static String versionString = "v0.2.14";

  // GLOBAL FOLDER/DIRECTORY VARIABLES
  // Files for input folder, template and output folder
  private static File templateFile;
  private static File input;
  private static File output;
  // Holds the paths for input/output/template as parsed from args
  // (so we don't put them into a File object right away)
  private static String inputPath = "";
  private static String templatePath = "";
  private static String outputPath = "";

  /***
  // Show extra print statements for various functions
  private static boolean verbose = false;
  // Skip some print statements that print by default
  private static boolean brief = false;
  ***/

  // VERBOSITY
  // Constants for quickly checking against various verbosity levels
  public final static int SILENT = Verbosity.SILENT.ordinal();
  public final static int BRIEF = Verbosity.BRIEF.ordinal();
  public final static int NORMAL = Verbosity.NORMAL.ordinal();
  public final static int VERBOSE = Verbosity.VERBOSE.ordinal();
  public final static int DEBUG = Verbosity.DEBUG.ordinal();

  private static Verbosity verbosity = Verbosity.NORMAL;

  // OTHER SETTINGS FOR GENERAL RUNNING BEHAVIOR

  // Decides if a config file should be read. True by default, can be turned off
  // by command arguments.
  private static boolean useConfigFile = true;

  // Moved here temporarily to keep compiler happy.
  // Regex template for page titles.
  private static String titleTemplate = "{T} - {S}";
  // Regex template for footers.
  private static String footerTemplate = "{{SiteName}} | Powered by ChiveGen "
                                         + versionString;
  private static String siteName = "Archive";

  // Main method
  public static void main(String[] args) {
    // Track time it takes for the program to run
    long startTime = System.currentTimeMillis();
    // Parse just the initial folder arguments, and put the rest in another
    // array to pass along to our config object.
    ArrayList<String> options = new ArrayList<String>();
    boolean building = parseFolderArgs(args, options);
    // If we're not building anything, exit without further output.
    if (!building) {
      return;
    }
    boolean ready = building && getFolders(inputPath, outputPath, templatePath);
    // If we can't proceed, exit without further output.
    if (!ready) {
      return;
    }

    // For debug purposes
    if (verbosity == Verbosity.DEBUG) {
      System.out.println("[DEBUG] INVOKED WITH ARGS: ");
      for (int i = 0; i < args.length; i++) {
        System.out.println(args[i]);
      }
      // Print our options
      printStatus("Additional args: " + options.toString(), Verbosity.NORMAL);
    }

    // Create FicArchiveBuilder, pass it the folder arguments, and tell it to
    // read in the config and any additional command line arguments.
    FicArchiveBuilder archiveBuilder = new FicArchiveBuilder();
    archiveBuilder.setFilePaths(input, output, templateFile);
    // Pass additional arguments AFTER any config file has been read and parsed.
    if (useConfigFile) {
      archiveBuilder.readConfig();
    }
    archiveBuilder.parseArgs(options.toArray(new String[0]));
    // Check in the input folder for any custom labels to set
    archiveBuilder.getCustomLabels();
    // Build the standard content templates
    archiveBuilder.buildTemplates();
    // Set verbosity - stopgap measure until verbosity overhaul is properly
    // implemented.
    archiveBuilder.setVerbosity(verbosity);

    // Build the archive.
    long storyTime = FicArchiveBuilder.build();

    // Report how long it took to build the site, if it was built.
    long finalTime = System.currentTimeMillis() - startTime;
    if (ready) {
      printStatus("Time taken: " + finalTime + " ms.", Verbosity.BRIEF);
      printStatus("Time for story generation: " + storyTime + " ms.",
             Verbosity.VERBOSE);
    }
  }


  /*** INTERNAL HELPER FUNCTIONS ***/

  // Parses the directory input and output arguments, as well as a general page
  // template path if one is given, and global parameters such as verbosity
  // (--brief, --verbose, etc) and --no-config.
  // Returns true if the given set of directory arguments is valid for running
  // the program, or false otherwise.
  // Returns the list of additional arguments via the opts ArrayList.
  public static boolean parseFolderArgs(String[] args, ArrayList<String> opts) {
    // Print a warning if there are no arguments given
    if (args.length == 0) {
      printUsage();
      System.out.println("(Try '--man' or '--help' if you need the manual.)\n");
      return false;
    }
    // Loop through the arguments and parse the folder-related ones, storing the
    // others in our ArrayList opt for later use.
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-i") || args[i].equals("-input")) {
        if (i == args.length - 1) {
          // If this is the last argument, report an error and return false
          printMissingArgError(args[i], "input directory");
          return false;
        } else {
          // Otherwise, take the next argument as input, and skip ahead one
          inputPath = args[i+1];
          i++;
        }
      } else if (args[i].equals("-o") || args[i].equals("-output")) {
        if (i == args.length - 1) {
          // Again, if this is last, it's an error
          printMissingArgError(args[i], "output directory");
          return false;
        } else {
          // Get the output path from the next argument, and skip ahead
          outputPath = args[i+1];
          i++;
        }
      } else if (args[i].equals("-t") || args[i].equals("-template")) {
        if (i == args.length - 1) {
          printMissingArgError(args[i], "template file");
          return false;
        } else {
          templatePath = args[i+1];
          i++;
        }
      } else if (args[i].equals("--no-config")) {
        useConfigFile = false;
      } else if (args[i].equals("--man") || args[i].equals("--help")) {
        printManual();
        return false; // ignore further arguments
      } else if (args[i].equals("--license")) {
        printLicense();
        return false;
      } else if (args[i].equals("--credits") || args[i].equals("--about")) {
        printCredits();
        return false;
      } else if (args[i].equals("-d") || args[i].equals("--debug")) {
        verbosity = Verbosity.DEBUG;
      } else if (args[i].equals("-b") || args[i].equals("--brief")) {
        verbosity = Verbosity.BRIEF;
      } else if (args[i].equals("-v") || args[i].equals("--verbose")) {
        verbosity = Verbosity.VERBOSE;
      } else if (args[i].equals("-s") || args[i].equals("--silent")) {
        verbosity = Verbosity.SILENT;
      } else {
        opts.add(args[i]);  // add to the list of non-folder arguments
      }
    }
    return true;
  }

  // Creates global Files for input, output, and page template. Returns false
  // if input or output is blank, or if the input directory or the template file
  // cannot be opened or are of the wrong type (e.g. a template that is actually
  // a folder, or input that is just a file). Otherwise, returns true.
  public static boolean getFolders(String inputPath, String outputPath,
                                   String templatePath) {
    // If either input or output is missing, do not proceed.
    if (inputPath.equals("") || outputPath.equals("")) {
      printUsage();
      return false;
    }
    // Create the input, output, and template files and check their validity
    // before proceeding to anything else
    input = new File(inputPath);
    if (!input.exists()) {
      System.out.println("Error: input folder does not exist.");
      return false;
    } else if (input.isFile()) {
      System.out.println("Error: input folder is actually a file.");
      return false;
    }
    // If no template is included in arguments, look for template.html in the
    // root of the input directory.
    if (templatePath.equals("")) {
      templateFile = new File(inputPath, "template.html");
      if (!templateFile.exists()) {
        System.out.println("Error: couldn't find template.html in folder '"
                           + inputPath + "'.");
        System.out.println("(Make sure it's in the root of the input directory,"
                           + " and the filename is all lowercase!)");
        return false;
      }
    } else if (!templatePath.endsWith(".html")) {
      System.out.println("Error: page template must be an .html file.");
      return false;
    } else {  // Otherwise, attempt to use the given path
      templateFile = new File(templatePath);
    }
    // Check the template file exists
    if (!templateFile.exists()) {
      System.out.println("Error: template file does not exist.");
      return false;
    } else if (templateFile.isDirectory()) {
      System.out.println("Error: template file is actually a directory.");
      return false;
    }
    // Check the output path is valid and doesn't have anything already there
    output = new File(outputPath);

    if (output.exists() && verbosity.ordinal() >= BRIEF) {
      /***
      System.out.println("Warning: output folder " + outputPath + " already "
                         + "exists, and will be overwritten.");
      ***/
    } else {
      // Check that our output folder is in a valid location
      File parent = output.getParentFile();
      if (!parent.canWrite() || !output.canWrite()) {
        System.out.println("Error: cannot write to directory"
                           + parent.getPath());
        return false;
      }
    }
    // If we got through all that...

    return true;
  }

  // Prints a general 'usage' error message. Technically redundant since the
  // bash helper script already does this if we don't have valid input and
  // output paths, but just in case...
  private static void printUsage() {
    System.out.println("\nUSAGE:\tchivegen -i INPUT_DIR -o OUTPUT_DIR "
             + "[additional options]");
 	  System.out.println("  or,\tjava ChiveGenMain -i INPUT_DIR -o OUTPUT_DIR "
 	           + "[additional options]\n");
  }

  // Prints the manual.
  private static void printManual() {
    System.out.println("\nCHIVEGEN ALPHA: FANFIC ARCHIVE BUILDER "
               + versionString);
    System.out.println("\nBasic usage:");
    System.out.println("chivegen -i INPUT_DIR -o OUTPUT_DIR"
               + " [... OTHER OPTIONS ...]");
    System.out.println("\nREQUIRED PARAMETERS");
    System.out.println("-i, -input\t\tSpecify input folder.");
    System.out.println("-o, -output\t\tSpecify output folder.");
    System.out.println("\nGENERAL ARCHIVE FORMATTING OPTIONS");
    System.out.println("-s, -site-name\t\tSpecify website name for use in "
               + "page titles, etc.\n\t\t\tDefault is \""
               + siteName + "\".");
    System.out.println("-url, -site-path\tSpecify a site folder path for "
               + "links, i.e. \"/name/\".\n\t\t\tDefaults to "
               + "\"/\".");
    System.out.println("-c, --casual-html\tEnable casual HTML for story "
               + "input. (May be slow.)");
    System.out.println("-tf, -title\t\tGive a title template for page "
               + "titles. \n\t\t\tDefault is \"" + titleTemplate
               + "\".");
    System.out.println("-ff, -footer\t\tGive a footer template for "
               + "automatic footers. \n\t\t\tDefault is \""
               + footerTemplate + "\".");
    System.out.println("\nSTORY INFOBOX OPTIONS");
    System.out.println("--skip-empty-fields\tDon't show placeholder data in"
               + " story infoboxes.");
    System.out.println("--show-auto-dates\tShow autofilled update and "
               + "published dates instead of \n\t\t\t\"Undated\".");
    System.out.println("--use-folder-dates\tDefault to the creation and "
               + "last modified dates \n\t\t\tof story input "
               + "folders for missing dates.");
    System.out.println("-nl, --no-labels\tDon't add field labels in "
               + "infoboxes.");
    System.out.println("\nCHAPTER FORMATTING OPTIONS");
    System.out.println("--show-chapter-numbers\tPreface chapter titles with"
               + " \"Chapter 1: \", \n\t\t\t\"Chapter 2: \", etc.");
    System.out.println("\nSTORY INDEX OPTIONS");
    System.out.println("-sk, --skip-index\tDon't create any index pages.");
    System.out.println("--skip-title-index\tDon't create an index page for "
               + "stories by title.");
    System.out.println("--skip-fandom-index\tDon't create index pages for "
               + "fandoms.");
    System.out.println("--skip-author-index\tDon't create index pages for "
               + "authors.");
    System.out.println("-st, --skip-tags\tDon't generate tag index pages or"
               + " valid tag links.");
    System.out.println("-ss, --skip-stats\tDon't bother calculating site "
               + "stats for the homepage.");
    System.out.println("-sc, --skip-css\t\tDon't copy CSS stylesheets from "
               + "input folder.");
    System.out.println("--skip-jump\t\tGenerate simple pagination, no jump "
               + "pagination links.");
    System.out.println("--ignore-leading-the\tIgnore a starting \"The\" "
               + "when sorting by title,\n\t\t\tfandom, etc.");
    System.out.println("\nOTHER OPTIONS");
    System.out.println("-v, --verbose\t\tVerbose mode. Shows extra print "
               + "statements.\n\t\t\t(WARNING! May show a LOT of "
               + "text, depending on what I've\n\t\t\tremembered to"
               + " comment out.)");
    System.out.println("-b, --brief\t\tBrief mode. Show fewer print "
               + "statements.");
    System.out.println("\nOTHER COMMANDS");
    System.out.println("--man, --help\t\tPrints the manual. (You probably "
               + "know this one.)");
    System.out.println("--about, --credits\tPrints the credits.");
    System.out.println("--license\t\tPrints the license. (Not properly "
               + "implemented yet.)");
    System.out.println("\nThere are no public docs at the moment, but when "
               + "there are I'll link them.\n");
  }

  // Prints the license.
  // TODO: decide on a license, if any!
  private static void printLicense() {
    // LICENSE GOES HERE
    System.out.println("\nThis program is not yet released.");
    System.out.println("\nUntil then:");
    System.out.println("\t1) THIS PROGRAM COMES WITH ABSOLUTELY NO WARRANTY OF "
               + "ANY KIND, and ");
    System.out.println("\t2) PLEASE DON'T BE STUPID ENOUGH TO USE THIS FOR"
               + " ANYTHING OF VALUE.\n");
  }

  // Prints the credits.
  private static void printCredits() {
    System.out.println("\nChiveGen " + versionString + " is a project "
               + "created and developed by alignmentDev.\n");
    System.out.println("It is written in Java (and bash) using gedit.\n");
    // In the future, add any further contributors if applicable...
  }


  /*** OTHER EXTERNALLY CALLABLE FUNCTIONS ***/

  // Prints a given string with println, but ONLY if the provided Verbosity
  // value is less than or equal to the current verbosity level.
  // Returns true if output would have been printed, or false otherwise.
  public static boolean printStatus(String s, Verbosity v) {
    if (v.ordinal() <= verbosity.ordinal()) {
      System.out.println(s);
      return true;
    }
    return false;
  }

  public static int verbosityInt() {
    return verbosity.ordinal();
  }

  public static String getVersionString() {
    return versionString;
  }

  // Prints an error message to indicate a missing necessary argument.
  public static void printMissingArgError(String arg, String content) {
    System.out.println("Error: argument " + arg + " was given, but no "
                       + content + "  was supplied.");
  }

}
