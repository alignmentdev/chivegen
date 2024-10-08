/***

  ChiveGen - a static fanfiction archive generator.
  Accepts an input directory and output directory, and builds an archive from
  the input files to the output directory.

***/

import java.util.*;
import java.util.regex.Pattern;
import java.io.*;
import java.time.*;
import java.time.format.*;
import java.lang.Math;

public class FicArchiveBuilder {
  /***
    For input and output folders.
  ***/
  // Files for input folder, template and output folder
  private static File templateFile;
  private static File input;
  private static File output;
  // Holds the paths for input/output/template as parsed from args
  // (so we don't put them into a File object right away)
  private static String inputPath = "";
  private static String templatePath = "";
  private static String outputPath = "";
  // Scanner for template
  private static Scanner templateReader;

  /***
    Array of all stories in archive
  ***/
  private static Story[] stories;

  /***
    The standard sets of fields for each template
  ***/
  private static String[] standardKeywords =
    new String[] {"Title", "Main", "Footer"};

  private static String[] storyInfoKeywords =
    new String[] {"StoryTitle", "Fandom", "Wordcount", "Chapters", "Published",
                  "Updated", "Summary", "IsComplete", "Author", "Tags",
                  "Rating"};

  private static String[] chapterKeywords =
    new String[] {"StoryInfo", "ChapterTitle", "StoryNotes", "ChapterNotes",
                  "ChapterBody", "ChapterEndNotes", "EndNotes",
                  "ChapterPagination"};

  private static String[] paginationKeywords =
    new String[] {"Previous", "JumpPrev", "JumpCurrent", "JumpNext", "Next"};

  private static String[] chapterPaginationKeywords =
    new String[] {"Previous", "Next"};

  private static String[] workIndexKeywords =
    new String[] {"Navigation", "ListingTitle", "CurrentlyShowing", "Listings",
                  "Pagination"};

  private static String[] statsWidgetKeywords =
    new String[] {"StoryNumber", "TotalWordcount", "FandomNumber",
                  "AuthorNumber", "VersionNumber"};

  private static String[] fieldKeywords = new String[] {"L", "C"};

  private static String[] allKeywords =
    new String[] {"Title", "Main", "Footer", "StoryTitle", "Fandom",
                  "Wordcount", "Chapters", "Published", "Updated", "Summary",
                  "IsComplete", "Author", "Tags", "Rating", "StoryInfo",
                  "ChapterTitle", "StoryNotes", "ChapterNotes", "ChapterBody",
                  "ChapterEndNotes", "EndNotes", "ChapterPagination",
                  "Previous", "JumpPrev", "JumpCurrent", "JumpNext", "Next",
                  "Navigation", "ListingTitle", "CurrentlyShowing", "Listings",
                  "Pagination", "L", "C"};


  /***
    The template objects used to build pages, fields, etc
  ***/
  private static ContentTemplate pageContentTemplate;
  private static ContentTemplate infoBoxContentTemplate;
  private static ContentTemplate indexInfoBoxContentTemplate;
  private static ContentTemplate chapterContentTemplate;
  private static ContentTemplate workIndexContentTemplate;
  private static ContentTemplate paginationContentTemplate;
  private static ContentTemplate chapterPaginationContentTemplate;
  private static ContentTemplate fieldContentTemplate;
  private static ContentTemplate summaryContentTemplate;
  private static ContentTemplate byLineContentTemplate;
  private static ContentTemplate tagContentTemplate;


  // To use for config
  private static HashSet<String> validConfigSettingSet;
  private static String[] validConfigSettingNames =
    new String[] {"title", "sitename", "footer", "sitepath", "maxitemsperpage",
                  "includestylesheets", "showchapternumbers", "casualhtml",
                  "usebylines", "generatefieldlabels", "ignoreleadingthe",
                  "skipjumppagination", "skipemptyfields", "ignoretabs",
                  "skippage", "statswidget", "paginationdivider"};

  // Used to check for a valid metadata field
  private static HashSet<String> validStoryMetadataSet;
  private static String[] validStoryMetadataTypes =
    new String[] {"title", "fandom", "fandoms", "author", "creator", "summary",
                  "notes", "end notes", "tags", "characters", "words", "length",
                  "wordcount", "rating", "rated", "complete", "date updated",
                  "updated", "date published", "published", "date posted",
                  "posted"};


  /***
    Strings for various bits and bobs used to create the site.
  ***/
  // Used when building chapter pages
  private static String nextChapterButton = "Next Chapter";
  private static String prevChapterButton = "Previous Chapter";
  private static String tocButton = "Table of Contents";
  // Used when building infoboxes
  private static String fandomLabel = "Fandom";
  private static String updatedLabel = "Updated";
  private static String publishedLabel = "Published";
  private static String wordcountLabel = "Words";
  private static String chapterCountLabel = "Chapters";
  private static String completionLabel = "Complete";
  private static String summaryLabel = "Summary";
  private static String notesLabel = "Notes";
  private static String endNotesLabel = "End Notes";
  private static String authorLabel = "Author";
  private static String tagsLabel = "Tags";
  private static String ratingLabel = "Rating";
  private static String nextPageLabel = "Next Page";
  private static String prevPageLabel = "Previous Page";
  // Used for archive ratings system
  private static String ratingLevelG = "G";
  private static String ratingLevelPG = "PG";
  private static String ratingLevelT = "T";
  private static String ratingLevelM = "MA";
  private static String ratingLevelE = "E";
  private static String ratingLevelNR = "Not Rated";
  // Used for completion statuses
  private static String[] completionStatuses = new String[] {"Yes", "No"};
  // Used on certain pages titles
  private static String titleIndexLabel = "Stories by Title";
  private static String latestIndexLabel = "Stories by Date Updated";
  // Name of website. Used in various places.
  private static String siteName = "Archive";
  // Regex template for page titles.
  private static String titleTemplate = "{T} - {S}";
  private static String titleBase; // the version with the site name in it
  // Regex template for footers.
  private static String footerTemplate = "{{SiteName}} | Powered by ChiveGen "
                                         + ChiveGenMain.versionString;
  // Built with buildPageFooter()
  private static String standardFooter;
  // What we should prefix links with when linking from
  // an arbitrary page to the index
  private static String sitePath = "/";
  // Possible future feature - URL customization for category folders
  private static String storyDirectoryName = "stories";
  private static String fandomDirectoryName = "fandom";
  private static String authorDirectoryName = "author";
  private static String tagsDirectoryname = "tag";

  /***
    Default templates for other page elements. These are overridden if an
    appropriate file exists.
  ***/
  private static String pageTemplate = "<!DOCTYPE html>\n<html>\n<head>"
    + "{{Title}}</head>\n<body>\n{{Main}}\n<footer>{{Footer}}</footer>\n</body>"
    + "\n</html>";
  private static String storyInfoTemplate =
    "<div class=storyinfo>\n<h2>\n{{StoryTitle}}\n</h2>\n{{Author}}\n"
    + "{{Fandom}}\n{{Rating}}\n{{Wordcount}}\n{{Chapters}}\n{{Published}}\n"
    + "{{Updated}}\n{{Summary}}\n</div>";
  private static String indexStoryInfoTemplate = storyInfoTemplate;
  private static String chapterTemplate = "{{StoryInfo}}\n"
    + "<div class=\"chapter-nav top-nav\"><a href=\"toc.html\">"
    + "Table of Contents</a>\n{{ChapterPagination}}\n</div>\n"
    + "<h3>\n{{ChapterTitle}}\n</h3>\n<div class=notes>\n{{StoryNotes}}\n"
    + "</div>\n{{ChapterBody}}\n<div class=notes>\n{{EndNotes}}\n</div>"
    + "<div class=\"chapter-nav bottom-nav\">\n{{ChapterPagination}}\n</div>";
  private static String chapterPaginationTemplate =
   "<div class=chapter-pagination>\n{{Previous}}\n{{Next}}\n</div>";
  private static String paginationTemplate =
    "<div class=pagination>\n{{Previous}}\n{{JumpPrev}}\n<span>{{JumpCurrent}}"
    + "</span>\n{{JumpNext}}\n{{Next}}\n</div>";
  private static String workIndexTemplate =
    "{{Navigation}}\n<h1>{{ListingTitle}}</h1>\n<h2>{{CurrentlyShowing}}</h2>"
    + "\n{{Pagination}}<div class=listings>{{Listings}}</div>{{Pagination}}";
  private static String fieldTemplate = "{{L}}: {{C}}";
  private static String summaryTemplate = fieldTemplate + "DEGBU!!!";
  private static String byLineTemplate = " by {{C}}";
  private static String workIndexNavigationTemplate =
    "<div class=listingnav>{C}</div>";
  private static String tagTemplate =
    "<div class=tag><a href=\"{{C}}\">{{L}}</a></div>";
  private static String chapterTitleTemplate = "Chapter {{L}}: {{C}}";

  /***
    Scanner patterns for template and file reading.
  ***/
  // Match {{ or }} only
  private static Pattern templateDelimiters = Pattern.compile("\\{\\{|\\}\\}");
  // Stupid hack that should probably be replaced with something more normal -
  // Use the 0 (end) character as a delimiter for scanning a whole file in as
  // few loops as possible.
  private static Pattern fastScanningPattern =
    Pattern.compile(Character.toString((char)0));

  /***
    To provide formatting for dates such as date updated/date published.
    Currently unused.
  ***/
  private static DateTimeFormatter dateFormat;

  /***
    Used to create pages for works by tags, fandom, and author.
  ***/
  private static HashMap<String, ArrayList<Story>> archiveTagMap;
  private static HashMap<String, ArrayList<Story>> archiveFandomMap;
  private static HashMap<String, ArrayList<Story>> archiveAuthorMap;

  /***
    Various other configuration options.
  ***/
  // Maximum number of items per page
  private static int maxItemsPerPage = 20;
  // False if we choose to skip CSS files
  private static boolean includeStyleSheets = true;
  // Add chapter numbers in chapter title (i.e. "Chapter N: Chapter Title")
  private static boolean showChapterNumbers = false;
  // Generate built-in labels for infobox fields
  // (If false, assume they already exist in the page template)
  private static boolean generateInfoBoxTemplateFields = true;
  // Format author data as "by AUTHOR" rather than "Author: AUTHOR"
  private static boolean useByLine = false;
  // Automatically insert paragraph tags if not present
  private static boolean casualHTML = false;
  // Skip empty metadata fields instead of autopopulating
  private static boolean skipEmptyFields = false;
  // Ignore a leading "The" when sorting by title, fandom, etc
  // E.g. if true, sorting by title puts "The Cask of Amontillado" before "Romeo and Juliet"
  private static boolean ignoreLeadingThe = false;
  // Don't generate jump pagination (i.e. links to specific pages)
  private static boolean skipJumpPagination;
  // Don't bother trying to make the tab level match when adding output text to file
  private static boolean ignoreTabs = false;
  // Don't create pages for the following:
  private static boolean skipHomepage = false;
  private static boolean skipWorkIndices = false;
  private static boolean skipTitleIndex = false;
  private static boolean skipFandomIndex = false;
  private static boolean skipLatestIndex = false;
  private static boolean skipAuthorIndex = false;
  private static boolean skipTagPages = false;
  // Put some stats about the archive on the homepage?
  private static boolean homePageStatsWidget = true;
  // Format dates?
  private static boolean autoFormatDates = false;
  // If true, put EPOCH (1970-01-01) for missing pub/update dates for sorting
  // Otherwise, try getting creation and last modified dates from the story
  // input folder.
  private static boolean defaultToEpochDate = true;
  // If true, show the actual placeholder/default fallback dates. Otherwise,
  // just put "undated".
  private static boolean showDefaultDates = false;

  // EXPERIMENTAL - URL divider between name and page # for paginated
  // archive categories.
  // Defaults to '/', creating subfolders for pages of a category, but could
  // be changed to allow for easier non-recursive page uploading.
  private static String paginationDivider = "/";

  /***
     VERBOSITY - under overhaul/refactor. Current/old settings as follows.
  ***/
  // Show extra print statements for various functions
  private static boolean verbose = false;
  // Skip some print statements that print by default
  private static boolean brief = false;

  // Variables for main to decide if certain things should actually be executed.
  private static boolean readyToBuild = true; // true if we're ready to build the site, false otherwise
  private static boolean useConfigFile = true; // true if config is detected and not disabled
  private static boolean building = true; // false if the command is something else like --man or --license
  private static boolean archiveHasFandoms = false; // true only if at least one story has a fandom in the metadata
  private static boolean archiveHasAuthors = false; // true only if at least one story has an author in the metadata
  private static boolean archiveHasTags = false; // true only if at least one story has tags


  /*** PRE-BUILD CONFIGURATION FUNCTIONS ***/

  // Sets the input, output, and template paths.
  public static void setFilePaths(File in, File out, File template) {
    input = in;
    output = out;
    templateFile = template;
  }

  // Temporary function to set verbosity from ChiveGenMain. Will be replaced
  // along with all current use of 'verbose' and 'brief' once verbosity overhaul
  // is complete.
  public static void setVerbosity(Verbosity v) {
    if (v.ordinal() <= ChiveGenMain.BRIEF) {
      brief = true;
      verbose = false;
    } else if (v.ordinal() >= ChiveGenMain.VERBOSE) {
      brief = false;
      verbose = true;
    }
  }

  // Checks in the input folder for any custom label files (field labels,
  // ratings, completion codes, etc.), reads in the files and sets them in
  // the global archive settings.
  public static void getCustomLabels() {
    // Check for files with labels, ratings, etc, and use them to
    // override defaults if so.
    File fieldLabels = new File(input, "labels.txt");
    if (fieldLabels.exists()) {
      if (verbose) {
        System.out.println("Field labels file found in input directory.");
      }
      setFieldLabels(fieldLabels);
    }
    File customRatings = new File(input, "ratings.txt");
    if (customRatings.exists()) {
      if (verbose) {
        System.out.println("Custom ratings file found in input directory.");
      }
      setRatings(customRatings);
    }
    File customCompletionCodes = new File(input, "completion.txt");
    if (customCompletionCodes.exists()) {
      if (verbose) {
        System.out.println("Custom completion statuses file found in input "
                           + "directory.");
      }
      setCompletionStatuses(customCompletionCodes);
    }
  }

  // Reads a set of field labels from file.
  // File must have ALL labels included, even non-custom ones!
  public static void setFieldLabels(File labelsFile) {
    try {
      Scanner labelReader = new Scanner(labelsFile);
      // Used when building chapter pages
      try {
        nextPageLabel = labelReader.nextLine();
        prevPageLabel = labelReader.nextLine();
        nextChapterButton = labelReader.nextLine();
        prevChapterButton = labelReader.nextLine();
        tocButton = labelReader.nextLine();
        fandomLabel = labelReader.nextLine();
        authorLabel = labelReader.nextLine();
        updatedLabel = labelReader.nextLine();
        publishedLabel = labelReader.nextLine();
        wordcountLabel = labelReader.nextLine();
        chapterCountLabel = labelReader.nextLine();
        completionLabel = labelReader.nextLine();
        summaryLabel = labelReader.nextLine();
        tagsLabel = labelReader.nextLine();
        ratingLabel = labelReader.nextLine();
        notesLabel = labelReader.nextLine();
        endNotesLabel = labelReader.nextLine();
      } catch (NoSuchElementException e) {
        System.out.println("Error: reached the end of the field labels file too"
                           + " early. Make sure all fields are accounted for!");
        labelReader.close();
        return; // stop trying to read the file
      }
      labelReader.close();
    } catch (FileNotFoundException e) {
      System.out.println("Error: tried to read the field labels file, but it "
                         + "could not be found.");
      e.printStackTrace();
    }
  }

  // Reads a set of ratings from file as the archive ratings.
  // File must have ALL labels included, even non-custom ones!
  public static void setRatings(File ratingsList) {
    try {
      Scanner ratingsReader = new Scanner(ratingsList);
      // Used when building chapter pages
      try {
        ratingLevelG = ratingsReader.nextLine();
        ratingLevelPG = ratingsReader.nextLine();
        ratingLevelT = ratingsReader.nextLine();
        ratingLevelM = ratingsReader.nextLine();
        ratingLevelE = ratingsReader.nextLine();
        ratingLevelNR = ratingsReader.nextLine();
      } catch (NoSuchElementException e) {
        System.out.println("Error: reached the end of the ratings file too "
                           + "early. Make sure all ratings are accounted for. "
                           + "To skip a label, put a blank line!");
        ratingsReader.close();
        return; // stop trying to read the file
      }
      ratingsReader.close();
    } catch (FileNotFoundException e) {
      System.out.println("Error: tried to read the ratings file, but it could "
                         + "not be found.");
      e.printStackTrace();
    }
  }

  // Reads a set of strings from file as the completion statuses.
  public static void setCompletionStatuses(File statuses) {
    try {
      Scanner statusReader = new Scanner(statuses);
      // Used when building chapter pages
      try {
        completionStatuses[0] = statusReader.nextLine();
        completionStatuses[1] = statusReader.nextLine();
      } catch (NoSuchElementException e) {
        System.out.println("Error: reached the end of the completion statuses "
                           + "file too early. If you want one to be blank, put "
                           + "a blank line!");
        statusReader.close();
        return; // stop trying to read the file
      }
      statusReader.close();
    } catch (FileNotFoundException e) {
      System.out.println("Error: tried to read the completion statuses file, "
                         + "but it could not be found.");
      e.printStackTrace();
    }
  }


  // Builds all of the various ContentTemplates use to generate site HTML
  public static void buildTemplates() {
    // Generate the content templates from the various input files.
    System.out.println("Building content templates...");
    pageTemplate = getTemplateFromFile(templateFile, pageTemplate);
    chapterTitleTemplate =
      getTemplateFromFile(new File(input, "chaptertitles.txt"),
                          chapterTitleTemplate);
    summaryTemplate =
      getTemplateFromFile(new File(input, "summaries.txt"),
                          summaryTemplate);
    if (verbose) {
      System.out.println("Constructing standard page template...");
    }
    pageContentTemplate = new ContentTemplate(templateFile, standardKeywords,
                                              pageTemplate);
    if (verbose) {
      System.out.println("Constructing story infobox templates...");
    }
    infoBoxContentTemplate = new ContentTemplate(new File(input, "infobox.txt"),
                                                 storyInfoKeywords,
                                                 storyInfoTemplate);
    // If we have a specific index infobox style, build that template;
    // otherwise, use the default infobox that we've already established
    if ((new File(input, "infobox_index.txt").exists())) {
      indexInfoBoxContentTemplate =
        new ContentTemplate(new File(input, "infobox_index.txt"),
                            storyInfoKeywords, storyInfoTemplate);
    } else {
      indexInfoBoxContentTemplate = infoBoxContentTemplate;
    }
    if (verbose) {
      System.out.println("Constructing chapter page template...");
    }
    chapterContentTemplate = new ContentTemplate(new File(input, "chapter.txt"),
                                                 chapterKeywords,
                                                 chapterTemplate);
    if (verbose) {
      System.out.println("Constructing work index page template...");
    }
    workIndexContentTemplate =
      new ContentTemplate(new File(input, "stories_by.txt"), workIndexKeywords,
                                   workIndexTemplate);
    if (verbose) {
      System.out.println("Constructing pagination template...");
    }
    paginationContentTemplate =
      new ContentTemplate(new File(input, "pagination.txt"), paginationKeywords,
                          paginationTemplate);
    if (verbose) {
      System.out.println("Constructing chapter pagination template...");
    }
    chapterPaginationContentTemplate =
      new ContentTemplate(new File(input, "chapterpagination.txt"),
                          chapterPaginationKeywords,
                          chapterPaginationTemplate);
    if (verbose) {
      System.out.println("Constructing field template...");
    }
    fieldContentTemplate = new ContentTemplate(new File(input, "fields.txt"),
                                               fieldKeywords, fieldTemplate);
    if (verbose) {
      System.out.println("Constructing summary template...");
    }
    summaryContentTemplate =
      new ContentTemplate(new File(input, "summaries.txt"), fieldKeywords,
                          summaryTemplate);
    // Dont bother with this one unless we're actually using the byline
    if (useByLine) {
      if (verbose) {
        System.out.println("Reading byline template...");
      }
      byLineContentTemplate = new ContentTemplate(byLineTemplate, fieldKeywords);
    }
    if (verbose) {
      System.out.println("Reading tag template...");
    }
    tagContentTemplate = new ContentTemplate(tagTemplate, fieldKeywords);
  }

  // Returns either the string contents of the File argument, or the original
  // String argument if no such File exists.
  public static String getTemplateFromFile(File f, String s) {
    if (f.exists()) {
      if (!brief) {
        System.out.println("'" + f.getName() + "' found in input directory." +
        " Reading to string...");
      }
      /***
      ChiveGenMain.printStatus("'" + f.getName() + "' found in input directory."
                               + " Reading to string...",
                               Verbosity.NORMAL);
                               ***/
      s = FileToStringUtils.readFileToString(f);
    }
    return s;
  }

  // Helper function for quickly parsing true/false text. Accepts a boolean and
  // either the string 'true' or 'false', and returns true if the string starts
  // with 't' or false if it starts with 'f'.
  // If the string begins with neither character (meaning it was invalid),
  // the function returns the original boolean.
  public static boolean quickParseTrueFalse(String s, boolean b) {
    s = s.toLowerCase();
    if (s.charAt(0) == 't') {
      return true;
    }
    if (s.charAt(0) == 'f') {
      return false;
    }
    if (!brief) {
      System.out.println("Error: expected 'true' or 'false' but instead found '"
       + s + "'. Returning default...");
    }
    /***
    ChiveGenMain.printStatus("Error: expected 'true' or 'false' but instead "
                              + "found '" + s + "'. Returning default...",
                              Verbosity.NORMAL);
    ***/
    return b; // default to existing value
  }

  // Reads the config.txt file from the established input folder and parses
  // settings appropriately.
  public static void readConfig() {
    if (!input.exists()) {
      System.out.println("Error: tried to read config, but no input file "
                         + "exists.");
      return;
    }
    File siteConfigFile = new File(input, "config.txt");
    validConfigSettingSet = GenUtils.hashSetFromArray(validConfigSettingNames);
    if (siteConfigFile.exists()) {
      System.out.println("Config file detected. Config settings will be used "
        + "unless contradicted by command line arguments.");
      //read config file
      // for each line: use a hashset to check that the label is valid,
      // then switch statement using 1st char and disambig from there
      try {
        Scanner configReader = new Scanner(siteConfigFile);
        int i = 0; // to track current line number
        while (configReader.hasNextLine()) {
          // break input into 2 strings: metadata name, and content
          String[] currentLineData = configReader.nextLine().split("=", 2);
          // strip underscores, and force lowercase
          currentLineData[0] = currentLineData[0].toLowerCase().replace("_", "");
          if (currentLineData.length > 1) {
            if (validConfigSettingSet.contains(currentLineData[0])) {
              // Once we know our field is something in the valid
              // config settings hashset, we can just narrow it down
              // by checking a few chars instead of a full string
              // comparison. This is a little imprecise, but faster
              // than checking the full string.
              switch (currentLineData[0].charAt(0)) {
                case 'c':
                  casualHTML = quickParseTrueFalse(currentLineData[1], casualHTML);
                  break;
                case 'f':
                  footerTemplate = currentLineData[1];
                  break;
                case 'g':
                  generateInfoBoxTemplateFields = quickParseTrueFalse(currentLineData[1],
                  generateInfoBoxTemplateFields);
                  break;
                case 'i':
                  if (currentLineData[0].charAt(1) == 'n') {
                    includeStyleSheets = quickParseTrueFalse(currentLineData[1],
                    includeStyleSheets);
                  }
                  else if (currentLineData[0].charAt(6) == 'l') {
                    ignoreLeadingThe = quickParseTrueFalse(currentLineData[1],
                    ignoreLeadingThe);
                  }
                  else {
                    ignoreTabs = quickParseTrueFalse(currentLineData[1], ignoreTabs);
                  }
                  break;
                case 'm':
                  try {
                    maxItemsPerPage = Integer.parseInt(currentLineData[1]);
                  } catch (NumberFormatException e) {
                    maxItemsPerPage = 20;
                    System.out.println("Error: maxItemsPerPage cannot be '" +
                    currentLineData[1] + "'. Leaving as default (20).");
                  }
                  break;
                case 'p':
                  paginationDivider = currentLineData[1];
                case 's':
                  // si...
                  if (currentLineData[0].charAt(1) == 'i') {
                    if (currentLineData[0].charAt(4) == 'p') {
                      sitePath = currentLineData[1];
                    }
                    else if (currentLineData[0].charAt(4) == 'n') {
                      siteName = currentLineData[1];
                    }
                  }
                  // sh...
                  else if (currentLineData[0].charAt(1) == 'h') {
                    showChapterNumbers = quickParseTrueFalse(currentLineData[1],
                    showChapterNumbers);
                  }
                  // sk(ip)...
                  else if (currentLineData[0].charAt(1) == 'k') {
                    if (currentLineData[0].charAt(4) == 'e') {
                      skipEmptyFields = quickParseTrueFalse(currentLineData[1],
                      skipEmptyFields);
                    }
                    else if (currentLineData[0].charAt(4) == 'j') {
                      skipJumpPagination = quickParseTrueFalse(currentLineData[1],
                      skipJumpPagination);
                    }
                    else if (currentLineData[0].charAt(4) == 'p') {
                      String[] pagesToSkip = currentLineData[1].split(",");
                      for (String s : pagesToSkip) {
				                s = s.toLowerCase();
				                if (s.equals("all")) {
				                  skipWorkIndices = true;
				                } else if (s.equals("author")) {
				                  skipAuthorIndex = true;
				                } else if (s.equals("home")) {
				                  skipHomepage = true;
				                } else if (s.equals("fandom")) {
				                  skipFandomIndex = true;
				                } else if (s.equals("latest")) {
				                  skipLatestIndex = true;
				                } else if (s.equals("tags")) {
				                  skipTagPages = true;
				                } else if (s.equals("title")) {
				                  skipTitleIndex = true;
				                }
				              }
                    }
                  }
                  // st...
                  else if (currentLineData[0].charAt(1) == 't') {
                    homePageStatsWidget = quickParseTrueFalse(currentLineData[1],
                    homePageStatsWidget);
                  }
                  break;
                case 't':
                  titleTemplate = currentLineData[1];
                  break;
                case 'u':
                  useByLine = quickParseTrueFalse(currentLineData[1], useByLine);
                  break;
                default:
                  // does nothing
              }
            }
            else {
              if (currentLineData[0].length() > 0 && currentLineData[0].charAt(0) == '#') {
                continue; // treat lines starting in '#' as commented out
              }
              System.out.println("Error: malformed or unrecognized setting name on line " + i +
              "of config.txt: '" + currentLineData[0] + "'.");
            }
          }
        }
      } catch (FileNotFoundException e) {
        System.out.println("Error: config file was detected, but found missing.");
      }
    }
    else {
      useConfigFile = false;
    }
  }

  // Looks at args to get input and output folders
  // DEPRECATED - these controls are being moved to ChiveGenMain.
  /***
  public static void parseFolderArgs(String[] args) {
    // Print a warning if there are no arguments given
    if (args.length == 0) {
      building = false;
      System.out.println("You must supply at least one argument. "
                         + "Try '--man' or '--help' if you need the manual.");
    }
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-i") || args[i].equals("-input")) {
        if (i == args.length - 1) { // If this is the last argument, report an error
          System.out.println("Error: argument " + args[i] + " was given, but no input was supplied.");
          building = false; // don't continue with no input folder
        }
        else { // Otherwise, take the next argument as input, and skip it next iteration
          inputPath = args[i+1];
          i++;
        }
      }
      else if (args[i].equals("-o") || args[i].equals("-output")) {
        if (i == args.length - 1) {
          System.out.println("Error: argument " + args[i] + " was given, but no output location was supplied.");
          building = false; // don't continue w/o output location either
        }
        else {
          outputPath = args[i+1];
          i++;
        }
      }
      else if (args[i].equals("-t") || args[i].equals("-template")) {
        if (i == args.length - 1) {
          System.out.println("Error: argument " + args[i] + " was given, but no template location was supplied.");
        }
        else {
          templatePath = args[i+1];
          i++;
        }
      }
      else if (args[i].equals("--no-config")) {
        useConfigFile = false;
      }
      else if (args[i].equals("--man") || args[i].equals("--help")) {
        building = false;
        printManual();
        break; // ignore further arguments
      }
      else if (args[i].equals("--license")) {
        building = false;
        printLicense();
        break; // ignore further arguments
      }
      else if (args[i].equals("--credits") || args[i].equals("--about")) {
        building = false;
        printCredits();
        break; // ignore further arguments
      }
    }
  }
  ***/

  // Parses the argument list for main, and sets relevant internal variables
  // based on this input.
  public static void parseArgs(String[] args) {
    /***
    // Print a warning if there are no arguments given
    if (args.length == 0) {
      building = false;
      System.out.println("You must supply at least one argument. " +
                         "Try '--man' or '--help' if you need the manual.");
    }
    ***/
    // Go through the arguments in order and read them
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-s") || args[i].equals("--site-name")) {
        if (i == args.length - 1) {
          printArgsError(args[i], "site name");
        }
        else {
          siteName = args[i+1];
          i++;
        }
      }
      else if (args[i].equals("-tf") || args[i].equals("--title")) {
        if (i == args.length - 1) {
          printArgsError(args[i], "title");
        }
        else {
          titleTemplate = args[i+1];
          i++;
        }
      }
      else if (args[i].equals("-ff") || args[i].equals("--footer")) {
        if (i == args.length - 1) {
          printArgsError(args[i], "footer");
        }
        else {
          footerTemplate = args[i+1];
          i++;
        }
      }
      else if (args[i].equals("-url") || args[i].equals("--site-path")) {
        if (i == args.length - 1) {
          printArgsError(args[i], "site path");
        }
        else {
          sitePath = args[i+1];
          i++;
        }
      }
      else if (args[i].equals("-mpp") || args[i].equals("--per-page")) {
        if (i == args.length - 1) {
          printArgsError(args[i], "number");
        }
        else {
          try {
            maxItemsPerPage = Integer.parseInt(args[i+1]);
            if (maxItemsPerPage < 1) {
              System.out.println("Error: maximum items per page must be at "
                                 + "least 1.");
              maxItemsPerPage = 1;
            }
          } catch (IllegalArgumentException e) {
            System.out.println("Error: '" + args[i+1] + "' is not an integer.");
            maxItemsPerPage = 20;
          }
          i++;
        }
      }
      else if (args[i].equals("-v") || args[i].equals("--verbose")) {
        // SOON TO BE DEPRECATED as of ongoing verbosity overhaul
        verbose = true;
        brief = false;
      }
      else if (args[i].equals("-b") || args[i].equals("--brief")) {
        verbose = false;
        brief = true;
      }
      else if (args[i].equals("-c") || args[i].equals("--casual-html")) {
        casualHTML = true;
      }
      else if (args[i].equals("-html") || args[i].equals("--full-html")) {
        casualHTML = false;
      }
      else if (args[i].equals("-it") || args[i].equals("--ignore-tabs")) {
        ignoreTabs = true;
      }
      else if (args[i].equals("-sk") || args[i].equals("--skip-index")) {
        skipWorkIndices = true;
      }
      else if (args[i].equals("-sc") || args[i].equals("--skip-css")) {
        includeStyleSheets = false;
      }
      else if (args[i].equals("-st") || args[i].equals("--skip-tags")) {
        skipTagPages = true;
      }
      else if (args[i].equals("--skip-author-index")) {
        skipAuthorIndex = true;
      }
      else if (args[i].equals("--skip-fandom-index")) {
        skipFandomIndex = true;
      }
      else if (args[i].equals("--skip-title-index")) {
        skipTitleIndex = true;
      }
      else if (args[i].equals("-ss") || args[i].equals("--skip-stats")) {
        homePageStatsWidget = false;
      }
      else if (args[i].equals("-by") || args[i].equals("--use-byline")) {
        useByLine = true;
      }
      else if (args[i].equals("--skip-empty-fields")) {
        skipEmptyFields = true;
      }
      else if (args[i].equals("--skip-jump")) {
        skipJumpPagination = true;
      }
      else if (args[i].equals("--no-labels")) {
        generateInfoBoxTemplateFields = false;
      }
      else if (args[i].equals("--ignore-the")) {
        ignoreLeadingThe = true;
      }
      else if (args[i].equals("--show-chapter-numbers")) {
        showChapterNumbers = true;
      }
      else if (args[i].equals("--use-folder-dates")) {
        defaultToEpochDate = false;
      }
      else if (args[i].equals("--show-auto-dates")) {
        showDefaultDates = true;
      }
    }
  }

  // Helper function for printing certain argument parsing error messages.
  private static void printArgsError(String arg, String argType) {
    if (!brief) {
      System.out.println("Error: argument " + arg + " was given, but no "
                         + argType + " was supplied.");
    }
    /***
    ChiveGenMain.printStatus("Error: argument " + arg + " was given, but no "
                             + argType + " was supplied.", Verbosity.BRIEF);
                             ***/
  }


  /*** ARCHIVE BUILD FUNCTION ***/

  // Builds the archive and returns the time it took to generate stories.
  public static long build() {
    if (skipWorkIndices) {
      skipFandomIndex = skipTitleIndex = skipAuthorIndex = true;
    }
    // Keep track of the time taken to build stories, specifically.
    long storyStartTime = 0;
    long storyEndTime = 0;
    // If the output directory doesn't exist, create it
    if (!output.exists()) {
      output.mkdirs();
    }
    // Create a standard page footer and title (template)
    standardFooter = buildPageFooter();
    titleBase = buildPageTitleBase(siteName);
    try {
      // Check again that the template file exists, just in case.
      if (!templateFile.exists()) {
        throw new FileNotFoundException();
      }
      else {
        if (verbose) {
          System.out.println("Looking for folders in '" + input.getPath()
                             + "'...");
        }
        /***
        ChiveGenMain.printStatus("Looking for folders in '" + input.getPath()
                                 + "'...", Verbosity.VERBOSE);
                                 ***/
        // Get all the direct subfolders of the input folder
        // These are our story folders.
        File[] storyFolders = input.listFiles(new FilenameFilter() {
        public boolean accept(File dir, String name) {
          // Get only the directories
          return (new File(dir, name).isDirectory());
        }
        });
        if (storyFolders.length == 0) {
          System.out.println("Warning: no story folders found.");
        }
        else {
          validStoryMetadataSet =
            GenUtils.hashSetFromArray(validStoryMetadataTypes);
          if (!brief) {
            System.out.print("Found " + storyFolders.length
                               + " subfolders in input directory: ");
            for (int i = 0; i < storyFolders.length - 1; ++i) {
              System.out.print(storyFolders[i].getName() + ", ");
            }
            System.out.println(storyFolders[storyFolders.length - 1].getName());
          }
        }
        // Initialize maps for tags, authors, fandoms
        archiveTagMap = new HashMap<String, ArrayList<Story>>();
        archiveAuthorMap = new HashMap<String, ArrayList<Story>>();
        archiveFandomMap = new HashMap<String, ArrayList<Story>>();
        // Create initial story array and output folder
        stories = new Story[storyFolders.length];
        File storiesOutputFolder = new File(output, "stories");
        storyStartTime = System.currentTimeMillis();
        // Generate stories
        System.out.println("Building stories...");
        for (int i = 0; i < stories.length; i++) {
          stories[i] = new Story(storyFolders[i], storiesOutputFolder);
          stories[i].buildInfoboxes();
          if (verbose) {
            System.out.println("Building story " + stories[i].getStoryTitle()
                               + "...");
          }
          stories[i].buildStory();
          addToStoryMap(archiveTagMap, stories[i].getStoryTags(), stories[i]);
          addToStoryMap(archiveAuthorMap, stories[i].getAuthors(), stories[i]);
          addToStoryMap(archiveFandomMap, stories[i].getFandoms(), stories[i]);
        }
        storyEndTime = System.currentTimeMillis();
        // Build indexes of works by various orderings
        String currentIndex;
        // Create index by title
        if (!skipTitleIndex) {
          File allByTitleFolder = new File(output, "by_title");
          if (!allByTitleFolder.exists()) {
            allByTitleFolder.mkdirs();
          }
          Arrays.sort(stories, new StoryTitleComparator());
          // Create pages
          String[] allByTitle = buildCategoryPages("", "by_title", stories,
                                                   titleIndexLabel, true);
          for (int i = 0; i < allByTitle.length; i++) {
            buildPage(buildStandardPageString(allByTitle[i], titleIndexLabel
                                                             + " (Page " + (i+1)
                                                              + ")"),
            new File(allByTitleFolder + paginationDivider + (i+1) + ".html"));
          }
        }
        // Create index of all works in reverse chronological
        // order, unless this is skipped.
        if (!skipLatestIndex) {
          File allByLatestFolder = new File(output, "latest");
          if (!allByLatestFolder.exists()) {
            allByLatestFolder.mkdirs();
          }
          Arrays.sort(stories, new DateUpdatedComparator());
          // Create pages
          String[] allByLatest = buildCategoryPages("", "latest", stories,
                                                    latestIndexLabel);
          for (int i = 0; i < allByLatest.length; i++) {
            buildPage(buildStandardPageString(allByLatest[i], latestIndexLabel
                                                              + " (Page "
                                                              + (i+1) + ")"),
            new File(allByLatestFolder + paginationDivider + (i+1) + ".html"));
          }
        }
        // Create fandom index if we have at least one fandom
        if (!skipFandomIndex) {
          // Generate fandom index page
          currentIndex =
            workIndexContentTemplate.assemble(buildAlphabeticalIndexOf(archiveFandomMap, "fandoms", "Fandoms"));
          // BUILD INDEX PAGE
          buildPage(buildStandardPageString(currentIndex,
                                            buildPageTitle("By Fandom")),
                    new File(output, "by_fandom.html"));
          if (verbose) {
            System.out.println("Fandom index page created.");
          }
          /***
          ChiveGenMain.printStatus("Fandom index page created.",
                                   Verbosity.VERBOSE);
                                   ***/
          // Build fandoms pages
          File fandomFolder = new File(output, "fandoms");
          if (!fandomFolder.exists()) {
            fandomFolder.mkdirs();
          }
          System.out.println("Generating fandom pages...");
          //ChiveGenMain.printStatus("Generating fandom pages...",
          //                         Verbosity.SILENT);
          buildArchiveCategory(archiveFandomMap, fandomFolder, "Fandoms",
                               "Stories in ");
        }
        // Create authors index if we have at least one author
        if (!skipAuthorIndex && archiveHasAuthors) {
          // Generate index page
          currentIndex =
            workIndexContentTemplate.assemble(buildAlphabeticalIndexOf(archiveAuthorMap,
                                                                       "authors",
                                                                       "Authors"));
          // BUILD INDEX PAGE
          buildPage(buildStandardPageString(currentIndex,
                                            buildPageTitle("By Author")),
                    new File(output, "by_author.html"));
          if (verbose) {
            System.out.println("Author index page created.");
          }
          /***
          ChiveGenMain.printStatus("Author index page created.",
                                   Verbosity.VERBOSE);
                                   ***/
          // Build authors pages
          File authorFolder = new File(output, "authors");
          if (!authorFolder.exists()) {
            authorFolder.mkdirs();
          }
          System.out.println("Generating author pages...");
          //ChiveGenMain.printStatus("Generating author pages...",
          //                         Verbosity.SILENT);
          buildArchiveCategory(archiveAuthorMap, authorFolder, "Authors",
                               "Stories by ");
        }
        // Create tag pages, if any tags are used.
        if (!skipTagPages && archiveHasTags) {
          System.out.println("Generating tag pages...");
          //ChiveGenMain.printStatus("Generating tag pages...",
          //                         Verbosity.SILENT);
          // CREATE TAG FOLDER + PAGES
          buildArchiveCategory(archiveTagMap, new File(output, "tags"), "Tags",
                               "Stories tagged ");
        }
        // Create the site homepage.
        String homePageContent = "";
        if (!skipHomepage) {
          File homePage = new File(input, "index.txt");
          if (homePage.exists()) {
            homePageContent = FileToStringUtils.readFileToString(homePage);
          }
          else {
            homePageContent = buildDefaultHomePage();
          }
          // Puts some site stats on the homepage, if the option to do so is
          // enabled.
          if (homePage.exists() && homePageStatsWidget) {
            // Locate insertion points and insert the stats widget data into the
            // homepage body
            ContentTemplate homePageTemplate =
              new ContentTemplate(homePageContent, statsWidgetKeywords);
            String[] stats =
              new String[] {HtmlUtils.numberWithCommas(stories.length),
                            HtmlUtils.numberWithCommas(getTotalWordcount(stories)),
                            HtmlUtils.numberWithCommas(getTotalFandoms(stories)),
                            HtmlUtils.numberWithCommas(getTotalAuthors(stories)),
                            ChiveGenMain.versionString};
            // Insert the stats into the homepage text body
            homePageContent = homePageTemplate.assemble(stats);
          }
          // Build the home page at index.html
          buildPage(buildStandardPageString(homePageContent,
                                            buildPageTitle("Home")),
                                            new File(output, "index.html"));
        }
        // Check for CSS files in input directory, and copy them over to the
        // output directory
        if (includeStyleSheets) {
          // Get an array of CSS files
          File[] styleSheets = input.listFiles(new FilenameFilter() {
          public boolean accept(File dir, String name) {
            // Get only the directories
            return (name.endsWith(".css"));
          }
          });
          if (!brief) {
            System.out.println("Checking for CSS stylesheets...");
            System.out.println(styleSheets.length + " stylesheets found: "
                               + Arrays.toString(styleSheets));
          }
          // Copy all stylesheets to the output folder
          String styleSheetSource = "";
          for (File styleSheet : styleSheets) {
            styleSheetSource = FileToStringUtils.readFileToString(styleSheet);
            buildPage(styleSheetSource, new File(output, styleSheet.getName()));
          }
        }
      }
    } catch (FileNotFoundException e) {
      if (!templateFile.exists()) {
        System.out.println("Error: required template file " +
        templateFile.getPath() + " does not exist.");
      }
      System.out.println();
      e.printStackTrace();
    }
    return storyEndTime - storyStartTime;
  }

  // Creates an HTML file and writes the input String to it
  public static File buildPage(String inputString, File outputFile) {
    if (!outputFile.exists()) {
      if (verbose) {
        System.out.println("Creating '" + outputFile.getPath() + "', since it "
                           + "does not already exist");
      }
      try {
        outputFile.createNewFile();
      } catch (IOException e) {
        System.out.println("Error: something went wrong building a page in "
                           + outputFile.getPath());
        e.printStackTrace();
      }
    }
    if (verbose) {
      System.out.println("Writing to output file: " + outputFile.getPath());
    }
    try {
      FileWriter pageBuilder = new FileWriter(outputFile);
      pageBuilder.write(inputString);
      // Close scanners and filewriter
      pageBuilder.close();
    } catch (FileNotFoundException e) {
      System.out.println("Error: tried to write to output file "
                         + outputFile.getPath()
                         + " but the file was not found.");
      e.printStackTrace();
    } catch (IOException e) {
      System.out.println("Error: tried to write to ouput file "
                         + outputFile.getPath()
                         + " but something went wrong in I/O.");
      e.printStackTrace();
    }
    return outputFile;
  }

  // Used to build a standard page string
  public static String buildStandardPageString(String inputString,
                                               String pageTitle) {
    return pageContentTemplate.assemble(new String[] {pageTitle, inputString,
                                                      standardFooter});
  }

  // Builds a page title using regex and a template.
  public static String buildPageTitle(String title, String subtitle) {
    if (siteName.equals("") && subtitle.equals("")) {
      return title;
    }
    if (title.equals("") && subtitle.equals("")) {
      return siteName;
    }
    if (siteName.equals("") && title.equals("")) {
      return subtitle;
    }
    if (subtitle.equals("")) { // title is not blank if it reaches this point
      return buildPageTitle(title);
    }
    if (title.equals("")) { // title is not blank if it reaches this point
      return buildPageTitle(subtitle);
    }
    if (siteName.equals("")) {
      return buildPageTitleBase(subtitle).replace("{T}", title);
    }
    return titleBase.replace("{T}", buildPageTitleBase(subtitle).replace("{T}", title));
  }

  // Used recursively to build page titles
  public static String buildPageTitle(String title) {
    if (title.equals("")) {
      return siteName;
    }
    return titleBase.replace("{T}", title);
  }

  // Build page title base
  public static String buildPageTitleBase(String title) {
    if (title.equals("")) {
      return "";
    }
    return titleTemplate.replace("{S}", title);
  }

  // Footer can have site name and version number
  public static String buildPageFooter() {
    return footerTemplate.replace("{{SiteName}}", siteName).replace("{{VersionNumber}}", ChiveGenMain.versionString);
  }

  public static String buildDefaultHomePage() {
    return "<p>Welcome to " + siteName + "!</p>";
  }


  /*** BUILDING INDICES FOR ARCHIVE ***/

  // Returns an array of three strings - the listing nav, the category name
  // and the string for the index of categories. Used to build the fandom
  // and author indexes.
  // Not currently paginated.
  public static String[] buildAlphabeticalIndexOf(HashMap<String, ArrayList<Story>> categories,
                                                  String categoryFolderURL,
                                                  String categoryName) {
    String[] categoryArray = categories.keySet().toArray(new String[0]);
    // Sort the stories array by the relevant comparator
    Arrays.sort(categoryArray);
    // Stringbuilder for story index
    StringBuilder categoryIndex = new StringBuilder();
    // Stringbuilder for quick links into index, if heading groups are used
    StringBuilder listingNav = new StringBuilder();
    // Headers for the values being compared
    char b = ' ';
    char a = ' ';
    for (int i = 0; i < categoryArray.length; i++) {
      // Get the grouping header of the current story
      b = Character.toUpperCase(categoryArray[i].charAt(0));
      // Only check the previous entry after at least one
      if (i > 0) {
        a = Character.toUpperCase(categoryArray[i-1].charAt(0));
      }
      if (verbose) {
        System.out.println(i + ": Grouping for " + categoryArray[i] + ": "
                           + Character.toString(b));
      }
      // Check if the current entry and the next's grouping match,
      // Or if we are on the first story in the array.
      if (i == 0 || a != b) {
        // Close the previous unordered list if we're moving to a new one
        if (i != 0) {
          categoryIndex.append("</ul>\n");
        }
        // Create a header for the new grouping
        categoryIndex.append("<h2 id=" + b + ">" + b + "</h2>\n<ul>");
        // And link to it in the listing navigation
        listingNav.append("<a href=#" + b + ">" + b + "</a>\n");
      }
      // Link to the first page of the category
      categoryIndex.append("<li><a href=\"" + sitePath + categoryFolderURL + "/"
                           + HtmlUtils.toSafeUrl(categoryArray[i]) +
      paginationDivider + "1.html\">" +
      HtmlUtils.toTitleCase(categoryArray[i]) + "</a> (" +
                            categories.get(categoryArray[i]).size() + ")</li>");
    }
    // Close the last unordered list
    if (categoryArray.length != 0) {
      categoryIndex.append("</ul>\n");
    }
    String listingNavString = "";
    if (!listingNav.equals("")) { // only create this if there's something to put in it
      listingNavString =
        workIndexNavigationTemplate.replace("{C}", listingNav.toString()).replace("{L}", "Navigation");;
    }
    return new String[] {listingNavString, categoryName, "",
                         categoryIndex.toString(), ""};
  }

  // Build all the pages for a category like tags/fandom/author/etc
  public static void buildArchiveCategory(HashMap<String, ArrayList<Story>> map,
                                          File categoryFolder,
                                          String categoryLabel,
                                          String titleLabel) {
    // Create the tag folder if it doesn't already exist
    if (!categoryFolder.exists()) {
      categoryFolder.mkdirs();
    }
    // For each tag/fandom/whatever, create the tag pages and write them
    // to file with the default url schema of:
    // [parent folder]/[URL-safe version of tag][pagination divider][page #].html
    File categorySubfolder;
    for (String category : map.keySet()) {
      categorySubfolder = new File(categoryFolder, HtmlUtils.toSafeUrl(category));
      // Only make the subfolders if they're actually being used
      if (paginationDivider.equals("/")) {
        categorySubfolder.mkdirs();
      }
      // Sort stories in the category by date updated
      Collections.sort(map.get(category), new DateUpdatedComparator());
      String[] pages = buildCategoryPages(categoryFolder.getName() + "/",
      category, map.get(category), (titleLabel + HtmlUtils.toTitleCase(category)));
      if (verbose) {
        System.out.println("Created " + pages.length + " page[s] for category "
                           + category);
      }
      for (int i = 0; i < pages.length; i++) {
        if (paginationDivider.equals("/")) {
          buildPage(buildStandardPageString(pages[i],
                    (titleLabel + HtmlUtils.toTitleCase(category) + " (Page " + (i+1) + ")")),
          new File(categorySubfolder, (i+1) + ".html")); // page URLs start at 1
        }
        else {
          buildPage(buildStandardPageString(pages[i],
                    (titleLabel + HtmlUtils.toTitleCase(category) + " (Page " + (i+1) + ")")),
          new File(categorySubfolder + paginationDivider + (i+1) + ".html"));
        }
      }
    }
    if (!brief) {
      System.out.println("Created " + map.keySet().size()
                         + " category folder[s] for " + categoryLabel);
    }
  }


  /*** BUILDING CATEGORY PAGES ***/

  // Create a string array for all pages of all works with a particular tag
  // or category, sorted in reverse chronological order, with pagination
  public static String[] buildCategoryPages(String categoryFolderURL,
                                            String category,
                                            ArrayList<Story> relatedStories,
                                            String categoryLabel,
                                            boolean URLIsSafe) {
    // If we need a "safe" version of the category name for the URL
    // convert that now.
    if (!URLIsSafe) {
      category = HtmlUtils.toSafeUrl(category);
    }
    // Figure out how many pages we need to generate
    int totalPages = relatedStories.size() / maxItemsPerPage;
    if (relatedStories.size() % maxItemsPerPage != 0) {
      totalPages++;
    }
    if (verbose) {
      System.out.println("Total pages to generate for " + category + ": " + totalPages);
    }
    String[] pageStrings = new String[totalPages]; // array to store the page strings
    StringBuilder pageOutput; // for building each page
    String[] indexPageElements; // for inserting into template
    // Build all pages under the category except for the final page
    for (int i = 0; i < totalPages - 1; i++) {
      pageOutput = new StringBuilder(); // for building the main page content
      // For entries in relatedStories at ids
      // i * maxPerPage + 0 to i * maxPerPage + maxPerPage - 1
      // (i.e. 0th to maxPerPage-1th from an offset of maxPerPage * however
      // many pages have been created already), build an index page.
      for (int j = 0; j < maxItemsPerPage; j++) {
        // add the infobox for each story tagged on that page
        pageOutput.append(relatedStories.get((i * maxItemsPerPage) + j).getInfoboxForIndex());
      }
      // Create page elements array
      indexPageElements =
        new String[] {"", categoryLabel,
                      ("Showing " + ((i * maxItemsPerPage) + 1) + "-"
                      + ((i + 1) * maxItemsPerPage) + " of "
                      + relatedStories.size()),
      pageOutput.toString(),
      buildStandardPaginationString(generatePagination(categoryFolderURL + category,
                                                       (i + 1), totalPages))};
      pageStrings[i] = workIndexContentTemplate.assemble(indexPageElements);
    }
    // Generate the last page, which might not be full length.
    pageOutput = new StringBuilder();
    for (int i = (maxItemsPerPage * (totalPages - 1)); i < relatedStories.size(); i++) {
      pageOutput.append(relatedStories.get(i).getInfoboxForIndex());
    }
    // Create page elements array for the final page
    indexPageElements =
      new String[] {"", categoryLabel,
                    ("Showing " + ((maxItemsPerPage * (totalPages - 1)) + 1)
                    + "-" + relatedStories.size() + " of "
                    + relatedStories.size()),
    pageOutput.toString(),
    buildStandardPaginationString(generatePagination(categoryFolderURL + category,
                                                     totalPages, totalPages))};
    // Insert the resulting page into the last entry of pageStrings
    pageStrings[totalPages - 1] = workIndexContentTemplate.assemble(indexPageElements);
    // Return the array
    return pageStrings;
  }

  //@override
  public static String[] buildCategoryPages(String categoryFolderURL,
                                            String category,
                                            ArrayList<Story> relatedStories,
                                            String categoryLabel) {
    // default to assuming category needs to be made URL-safe
    return buildCategoryPages(categoryFolderURL, category, relatedStories,
                              categoryLabel, false);
  }

  //@override
  public static String[] buildCategoryPages(String category,
                                            ArrayList<Story> relatedStories) {
    return buildCategoryPages((output.getName() + "/" + category), category,
                              relatedStories, "Stories in ");
  }

  //@override
  public static String[] buildCategoryPages(String categoryFolderURL,
                                            String category,
                                            Story[] relatedStories,
                                            String label) {
    ArrayList<Story> storiesArrayList =
      new ArrayList<Story>(relatedStories.length);
    for (Story story : relatedStories) {
      storiesArrayList.add(story);
    }
    return buildCategoryPages(categoryFolderURL, category,
                              storiesArrayList, label, false);
  }

  //@override
  public static String[] buildCategoryPages(String categoryFolderURL,
                                            String category,
                                            Story[] relatedStories,
                                            String label,
                                            boolean URLIsSafe) {
    ArrayList<Story> storiesArrayList =
      new ArrayList<Story>(relatedStories.length);
    for (Story story : relatedStories) {
      storiesArrayList.add(story);
    }
    return buildCategoryPages(categoryFolderURL, category, storiesArrayList,
                              label, URLIsSafe);
  }

  // Build pagination from a folder URL, current page, a maximum # of pages
  public static String[] generatePagination(String categoryFolderURL,
                                            int currentPage, int totalPages) {
    // Pagination template has 5 things, in order:
    // Previous Button, Previous Jump Pages, Current, Next Jump Pages, and Next Button
    String[] paginationContents = new String[] {"", "", "", "", ""};
    // If there's no pagination, don't even bother.
    if (totalPages == 1) {
      return paginationContents;
    }
    // If we have a page 2 pages back, link it
    if (!skipJumpPagination && currentPage > 2) {
      paginationContents[1] = "<a href=\"" + sitePath + categoryFolderURL
        + paginationDivider + (currentPage - 2) + ".html\">" + (currentPage - 2)
        + "</a>";
    }
    // If we have a previous page
    if (currentPage > 1) {
      if (!skipJumpPagination) {
        // Page n-1 and n-2 go in the same section
        paginationContents[1] = paginationContents[1] + "\n<a href=\""
          + sitePath + categoryFolderURL + paginationDivider
          + (currentPage - 1) + ".html\">" + (currentPage - 1) + "</a>";
      }
      // Previous button
      paginationContents[0] = "<a href=\"" + sitePath + categoryFolderURL
        + paginationDivider + (currentPage - 1) + ".html\">" + prevPageLabel
        + "</a>";
    }
    // The current Page
    if (!skipJumpPagination) {
      paginationContents[2] = Integer.toString(currentPage);
    }
    // If there's a next page
    if (currentPage < totalPages) {
      if (!skipJumpPagination) {
        paginationContents[3] = "<a href=\"" + sitePath + categoryFolderURL
          + paginationDivider + (currentPage + 1) + ".html\">"
          + (currentPage + 1) + "</a>";
      }
      // Next button
      paginationContents[4] = "<a href=\"" + sitePath + categoryFolderURL
        + paginationDivider + (currentPage + 1) + ".html\">" + nextPageLabel
        + "</a>";
    }
    // If there's a page after the next, link that
    if (!skipJumpPagination && currentPage < (totalPages - 1)) {
      // Page n+1 and n+2 go in the same section
      paginationContents[3] = paginationContents[3] + "\n<a href=\"" + sitePath
        + categoryFolderURL + paginationDivider + (currentPage + 2) + ".html\">"
        + (currentPage + 2) + "</a>";
    }
    return paginationContents;
  }


  /*** MISCELLANOUS ARCHIVE-BUILDING HELPER FUNCTIONS ***/

  // Used to build a standard pagination string
  public static String buildStandardPaginationString(String[] inputStrings) {
    return paginationContentTemplate.assemble(inputStrings);
  }

  // Gets the total wordcount of the entire archive
  public static int getTotalWordcount(Story[] stories) {
    int total = 0;
    for (Story s : stories) {
      total+= s.getWordCount();
    }
    return total;
  }

  // Gets the total number of fandoms in the archive
  public static int getTotalFandoms(Story[] stories) {
    int total = 0;
    // Store strings in a hashset for fast lookup
    HashSet<String> allFandoms = new HashSet<String>();
    for (Story s : stories) {
      // if the fandom isn't in the set, add it, and increment total
      for (String fandom : s.getFandoms()) {
        if (!allFandoms.contains(fandom)) {
          allFandoms.add(fandom);
          total++;
        }
      }
    }
    return total;
  }

  // Gets the total number of authors in the archive
  // May be deprecated at some point if a more general function is added to
  // combine the behavior of this, getTotalFandoms, etc.
  public static int getTotalAuthors(Story[] stories) {
    int total = 0;
    // Store strings in a hashset for fast lookup
    HashSet<String> allAuthors = new HashSet<String>();
    for (Story s : stories) {
      // if the author isn't in the set, add it, and increment total
      for (String author : s.getAuthors()) {
        if (!allAuthors.contains(author)) {
          allAuthors.add(author);
          total++;
        }
      }
    }
    return total;
  }

  // Add tags/hashset data and associated story to an archive category hashmap
  public static void addToStoryMap(HashMap<String, ArrayList<Story>> map,
                                   String[] newTags, Story story) {
    for (String tag : newTags) {
      addToStoryMap(map, tag, story);
    }
  }

  // General 'add to story map' method for individual tags
  public static void addToStoryMap(HashMap<String, ArrayList<Story>> map,
                                   String tag, Story story) {
    tag = tag.toLowerCase(); // ignore case
    if (map.containsKey(tag)) {
      // add this story to the arraylist of associated stories
      map.get(tag).add(story);
    }
    else {
      ArrayList<Story> temp = new ArrayList<Story>();
      temp.add(story);
      map.put(tag, temp);
    }
  }

  /*** EXTERNALLY CALLABLE HELPER FUNCTIONS - GETTERS  ***/

  // Get verbosity setting
  public static boolean isVerbose() {
    return verbose;
  }

  // Get the valid set of story metadata (for faster parsing)
  public static HashSet<String> getValidStoryMetadataSet() {
    return validStoryMetadataSet;
  }

  // Returns the story infobox template
  public static ContentTemplate getInfoBoxTemplate() {
    return infoBoxContentTemplate;
  }

  // Returns the story infobox template
  public static ContentTemplate getIndexInfoBoxTemplate() {
    return indexInfoBoxContentTemplate;
  }

  // Returns page template
  public static ContentTemplate getPageTemplate() {
    return pageContentTemplate;
  }

  // Returns chapter template
  public static ContentTemplate getChapterTemplate() {
    return chapterContentTemplate;
  }

  // Returns the generic jump pagination template.
  public static ContentTemplate getPaginationTemplate() {
    return paginationContentTemplate;
  }

  // Returns the chapter pagination template.
  public static ContentTemplate getChapterPaginationContentTemplate() {
    return chapterPaginationContentTemplate;
  }

  // Returns the field template
  public static ContentTemplate getFieldContentTemplate() {
    return fieldContentTemplate;
  }

  // Returns the summary template
  public static ContentTemplate getSummaryContentTemplate() {
    return summaryContentTemplate;
  }

  // Used for the byline
  public static ContentTemplate getByLineTemplate() {
    if (useByLine) {
      return byLineContentTemplate;
    }
    return fieldContentTemplate;
  }

  // Returns the story summary template
  // This is also used for story and chapter notes
  public static String getSummaryTemplate() {
    return summaryTemplate;
  }

  // Returns the standard infobox field template.
  public static String getFieldTemplate() {
    return fieldTemplate;
  }

  // Returns the chapter pagination template.
  public static String getChapterPaginationTemplate() {
    return chapterPaginationTemplate;
  }


  // Returns the tag template.
  public static ContentTemplate getTagTemplate() {
    return tagContentTemplate;
  }

  // Returns the chapter title template.
  public static String getChapterTitleTemplate() {
    return chapterTitleTemplate;
  }

  // Gets the previous chapter button label
  public static String getPrevChapterLabel() {
    return prevChapterButton;
  }

  // Gets the next chapter button label
  public static String getNextChapterLabel() {
    return nextChapterButton;
  }

  // Gets the table of contents button label
  public static String getTOCLabel() {
    return tocButton;
  }

  // Gets the label for the 'fandom' field
  public static String getFandomLabel() {
    return fandomLabel;
  }

  // Gets the label for the 'date updated' field
  public static String getDateUpdatedLabel() {
    return updatedLabel;
  }

  // Gets the label for the 'date published' field
  public static String getDatePublishedLabel() {
    return publishedLabel;
  }

  // Gets the label for the wordcount field
  public static String getWordcountLabel() {
    return wordcountLabel;
  }

  // Gets the label for the 'number of chapters' field
  public static String getChapterCountLabel() {
    return chapterCountLabel;
  }

  // Gets the label for the 'is complete' field
  public static String getCompletionLabel() {
    return completionLabel;
  }

  // Gets the label for the notes field
  public static String getNotesLabel() {
    return notesLabel;
  }

  // Gets the label for the end notes field
  public static String getEndNotesLabel() {
    return endNotesLabel;
  }

  // Gets the label for the summary field
  public static String getSummaryLabel() {
    return summaryLabel;
  }

  // Gets the label for the author field
  public static String getAuthorLabel() {
    return authorLabel;
  }

  // Gets the label for the tags field
  public static String getTagsLabel() {
    return tagsLabel;
  }

  // Gets the label for the tags field
  public static String getRatingLabel() {
    return ratingLabel;
  }

  // Gets a string for the give Rating
  public static String getRatingString(Rating r) {
    switch (r) {
      case G:
        return ratingLevelG;
      case PG:
        return ratingLevelPG;
      case TEEN:
        return ratingLevelT;
      case MATURE:
        return ratingLevelM;
      case EXPLICIT:
        return ratingLevelE;
      default:
        return ratingLevelNR;
    }
  }

  // Returns the appropriate string for completion statuses
  public static String getCompletionStatusString(boolean complete) {
    if (complete) {
      return completionStatuses[0];
    }
    return completionStatuses[1];
  }

  // Returns the site folder path for the website this archive will be placed in
  public static String getSitePath() {
    return sitePath;
  }

  // Gets the standard list of page keywords
  public static String[] getStandardPageKeywords() {
    return standardKeywords;
  }

  // Gets the standard list of story info keywords
  public static String[] getStoryInfoKeywords() {
    return storyInfoKeywords;
  }

  // Gets the standard list of chapter page keywords
  public static String[] getChapterKeywords() {
    return chapterKeywords;
  }

  // Gets the standard list of pagination keywords
  public static String[] getPaginationKeywords() {
    return paginationKeywords;
  }

  // Get whether or not chapter numbers should be show in chapter titles
  public static boolean showChapterNumbers() {
    return showChapterNumbers;
  }

  // Get whether or not to use casual HTML
  public static boolean useCasualHTML() {
    return casualHTML;
  }

  // Get whether or not to skip empty metadata fields
  public static boolean skipEmptyFields() {
    return skipEmptyFields;
  }

  // Gets whether or not to build fields from template.
  // If false, the infobox template should already have them built in.
  public static boolean generateInfoBoxTemplateFields() {
    return generateInfoBoxTemplateFields;
  }

  // Gets whether or not to skip leading "The" in string comparisons
  public static boolean skipThe() {
    return ignoreLeadingThe;
  }

  // Returns true if no tag pages will be generated
  public static boolean skipTagPages() {
    return skipTagPages;
  }

  // Returns true if no author pages will be generated
  public static boolean skipAuthorIndex() {
    return skipAuthorIndex;
  }

  // Returns true if no fandom pages will be generated
  public static boolean skipFandomIndex() {
    return skipFandomIndex;
  }

  public static boolean autoFormatDates() {
    return autoFormatDates;
  }

  public static boolean defaultToEpochDate() {
    return defaultToEpochDate;
  }

  public static boolean showDefaultDates() {
    return showDefaultDates;
  }

  public static String getPaginationDivider() {
    return paginationDivider;
  }


  /*** EXTERNALLY CALLABLE HELPER FUNCTIONS - SETTERS ***/

  // Called (true) each time a story reads an author name in storyinfo.txt
  // If never called, we know there are no authors to sort by.
  public static void setHasAuthors(boolean b) {
    archiveHasAuthors = b;
  }

  // Called each time a story reads in a tagset.
  // If this is never called, we know there are no tags to generate pages for.
  public static void setHasTags(boolean b) {
    archiveHasTags = b;
  }

}
