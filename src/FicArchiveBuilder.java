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
	// Version string for manual and about text
	private static String versionString = "v0.2.8";

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
	// Empty string for template
	private static String pageTemplate = "";
	
	// Array of all stories in archive
	private static Story[] stories;
	
	// Used for HTML converter formatting	
	private static HashSet<String> nonParagraphHTMLTags;
	// In order: line break, horizontal line, heading, end of a tag, div, image, 
	// list item, unordered list, ordered list, iframe, blockquote, table, 
	// table row, table data, table heading.
	// <no></no> can be used to tell ChiveGen to ignore a line for formatting
	// regardless of what else it starts with
	private static String[] acceptedOpeningHTMLTags = new String[] {"<br", "<hr", "<h", "</", "<di", "<im", "<li", "<ul", "<ol",
	"<if", "<bl", "<ta", "<tr", "<td", "<th", "<no"};
	
	
	// The standard sets of fields for each template
	private static String[] standardKeywords = new String[] {"Title", "Main", "Footer"};
	private static String[] storyInfoKeywords = new String[] {"StoryTitle", "Fandom", "Wordcount", "Chapters", "Published", "Updated", 
	"Summary", "IsComplete", "Author", "Tags", "Rating"};
	private static String[] chapterKeywords = new String[] {"StoryInfo", "ChapterTitle", "StoryNotes", "ChapterBody", "EndNotes", "Pagination"};
	private static String[] paginationKeywords = new String[] {"Previous", "JumpPrev", "JumpCurrent", "JumpNext", "Next"};
	private static String[] chapterPaginationKeywords = new String[] {"Previous", "Next"};
	private static String[] workIndexKeywords = new String[] {"Navigation", "ListingTitle", "CurrentlyShowing", "Listings"};	
	private static String[] statsWidgetKeywords = new String[] {"StoryNumber", "TotalWordcount", "FandomNumber", "AuthorNumber"};	
	private static String[] fieldKeywords = new String[] {"L", "C"};
	
	// The template objects
	private static ContentTemplate pageContentTemplate;
	private static ContentTemplate infoBoxContentTemplate;
	private static ContentTemplate chapterContentTemplate;
	private static ContentTemplate workIndexContentTemplate;
	private static ContentTemplate paginationContentTemplate;
	private static ContentTemplate chapterPaginationContentTemplate;
	private static ContentTemplate fieldContentTemplate;
	private static ContentTemplate byLineContentTemplate;
	
	
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
	private static String footerTemplate = "{SITENAME} | Powered by ChiveGen " + versionString;
	// Built with buildPageFooter()
	private static String standardFooter;
	// What we should prefix links with when linking from
	// an arbitrary page to the index
	private static String sitePath = "/";
	
	/***
	Default templates for other page elements. These are overridden if an 
	appropriate file exists.
	***/
	private static String storyInfoTemplate = ("<div class=storyinfo>\n<h2>\n{{StoryTitle}}\n</h2>\n" +
		"{{Fandom}}\n{{Wordcount}}\n{{Chapters}}\n{{Published}}\n{{Updated}}\n{{Summary}}\n</div>");
	private static String chapterTemplate = ("{{StoryInfo}}\n" + 
		"<div class=\"chapter-nav top-nav\"><a href=\"toc.html\">Table of Contents</a>\n{{Pagination}}\n</div>\n" + 
		"<h3>\n{{ChapterTitle}}\n</h3>\n"  + 
		"<div class=notes>\n{{StoryNotes}}\n</div>\n" +
		"{{ChapterBody}}\n<div class=notes>\n{{EndNotes}}\n</div><div class=\"chapter-nav bottom-nav\">\n{{Pagination}}\n</div>");
	private static String chapterPaginationTemplate = "<div class=chapter-pagination>\n{{Previous}}\n{{Next}}\n</div>";
	private static String paginationTemplate = "<div class=pagination>\n{{Previous}}\n{{JumpPrev}}\n{{JumpCurrent}}\n{{JumpNext}}\n{{Next}}\n</div>";
	private static String workIndexTemplate = "{{Navigation}}\n<h1>\n{{ListingTitle}}\n</h1><h2>\n{{CurrentlyShowing}}\n</h2><div class=listings>\n{{Listings}}\n</div>";
	private static String fieldTemplate = "{{L}}: {{C}}";
	private static String summaryTemplate = fieldTemplate;
	private static String byLineTemplate = " by {{C}}";
	private static String workIndexNavigationTemplate = "<div class=listingnav>{C}</div>";
	private static String tagTemplate = "<div class=tag>{C}</div>";
	private static String tagLastTemplate = "<div class=\"tag last\">{C}</div>";
	
	/***
	For template and file reading.
	***/
	// match {{ or }} only
	private static Pattern templateDelimiters = Pattern.compile("\\{\\{|\\}\\}");
	// The BEL character (char = 7), being an old teletype-related character, should not 
	// ever appear in any actual file or text, so it makes a good delimiter
	// for scanning the whole file in as few loops as possible.
	private static Pattern fastScanningPattern = Pattern.compile(Character.toString((char)7));
	
	/***
	Used to provide formatting for dates such as date updated/date published.
	***/
	private static DateTimeFormatter dateFormat;
	
	/***
	Used to created pages for works by tags, fandom, and author.
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
	// Put some stats about the archive on the homepage
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
	
	// Show extra print statements for various functions
	private static boolean verbose = false;
	// Skip some print statements that print by default
	private static boolean brief = false;
	
	// Variables for main to decide if certain things should actually be executed.	
	private static boolean readyToBuild = true; // true if we're ready to build the site, false otherwise
	private static boolean building = true; // false if the command is something else like man or license
	private static boolean archiveHasFandoms = false; // true only if at least one story has a fandom in the metadata
	private static boolean archiveHasAuthors = false; // true only if at least one story has an author in the metadata
	private static boolean archiveHasTags = false; // true only if at least one story has tags

	public static void main(String[] args) {
		long startTime = System.currentTimeMillis(); // Track time it takes for the program to run
		// Parse the arguments.
		parseArgs(args);
		if (!building) {
			readyToBuild = false;
		}
		else if (inputPath.equals("") || outputPath.equals("")) {
			readyToBuild = false;
			if (args.length == 0) {
				System.out.println("Need some help? Try 'FicArchiveBuilder man' for the manual.");
			}
			else if (!inputPath.equals("")) {
				System.out.println("You need to specify an output folder first.");
			}
			else if (!outputPath.equals("")) {
				System.out.println("You need to specify an input folder first.");
			}
			else if (args[0].startsWith("-")) {
				System.out.println("You need to specify input and output folders first.");
			}
			else {
				System.out.println("That doesn't seem to be a valid command. Try 'FicArchiveBuilder man' to check out the manual.");
			}
		}
		// If no template is included in arguments, look for template.html in the input path
		else if (templatePath.equals("")) {
			File defaultTemplate = new File(inputPath, "template.html");
			if (defaultTemplate.exists()) {
				templatePath = defaultTemplate.getPath();
			}
			else {
				System.out.println("Error: couldn't find template.html in folder '" + inputPath + "'.");
				System.out.println("(Make sure it's in the root of your input directory, and the filename is all lowercase!)");
				readyToBuild = false;
			}
		}
		else if (!templatePath.endsWith(".html")) {
			readyToBuild = false;
			System.out.println("Error: template files must be .html files.");
		}
		if (readyToBuild) {
			// Create the input, output, and template files and check their validity
			// before proceeding to anything else
			templateFile = new File(templatePath);
			input = new File(inputPath);
			output = new File(outputPath);
			if (!input.exists()) {
				readyToBuild = false;
				System.out.println("Error: input folder does not exist.");
			}
			else if (input.isFile()) {
				readyToBuild = false;
				System.out.println("Error: input folder is actually a file.");
			}
			if (output.isFile()) {
				readyToBuild = false;
				System.out.println("Error: output folder is actually a file.");
			}
			if (!templateFile.exists()) {
				readyToBuild = false;
				System.out.println("Error: template file does not exist.");
			}
			if (templateFile.isDirectory()) {
				readyToBuild = false;
				System.out.println("Error: template file is actually a directory.");
			}
		}
		if (readyToBuild) {
			// Set some relevant defaults
			if (skipWorkIndices) {
				skipFandomIndex = skipTitleIndex = skipAuthorIndex = true;
			}
			// If Casual HTML is enabled, set up the list of acceptable opening tags to ignore paragraphs for
			if (casualHTML) {
				nonParagraphHTMLTags = generateHashSetFromArray(acceptedOpeningHTMLTags);
			}
			// Turn the template into a string for use in other methods
			pageTemplate = readFileToString(templateFile);
			// Check if story info and chapter templates exist in the input directory
			// And override the defaults if so
			File storyInfoTemplateFile = new File(input, "infobox.txt");
			if (storyInfoTemplateFile.exists()) {
				if (verbose) {
					System.out.println("Story infobox template found in input directory.");
				}
				storyInfoTemplate = readFileToString(storyInfoTemplateFile);
			}
			File chapterTemplateFile = new File(input, "chapter.txt");
			if (chapterTemplateFile.exists()) {
				if (verbose) {
					System.out.println("Chapter template found in input directory.");
				}
				chapterTemplate = readFileToString(chapterTemplateFile);
			}
			File summaryTemplateFile = new File(input, "summaries.txt");
			if (summaryTemplateFile.exists()) {
				if (verbose) {
					System.out.println("Summary template found in input directory.");
				}
				summaryTemplate = readFileToString(summaryTemplateFile);
			}
			File paginationTemplateFile = new File(input, "pagination.txt");
			if (paginationTemplateFile.exists()) {
				if (verbose) {
					System.out.println("Pagination template found in input directory.");
				}
				paginationTemplate = readFileToString(paginationTemplateFile);
			}
			File fieldTemplateFile = new File(input, "fields.txt");
			if (fieldTemplateFile.exists()) {
				if (verbose) {
					System.out.println("Field template found in input directory.");
				}
				fieldTemplate = readFileToString(fieldTemplateFile);
			}
			File workIndexFile = new File(input, "stories_by.txt");
			if (workIndexFile.exists()) {
				if (verbose) {
					System.out.println("Work index template found in input directory.");
				}
				workIndexTemplate = readFileToString(workIndexFile);
			}
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
					System.out.println("Custom completion statuses file found in input directory.");
				}
				setCompletionStatuses(customCompletionCodes);
			}
						
			// Generate the templates
			if (!brief) {
				System.out.println("Reading standard page template...");
			}
			pageContentTemplate = buildTemplate(pageTemplate, standardKeywords);
			if (!brief) {
				System.out.println("Reading story infobox template...");
			}
			infoBoxContentTemplate = buildTemplate(storyInfoTemplate, storyInfoKeywords);
			if (!brief) {
				System.out.println("Reading chapter page template...");
			}
			chapterContentTemplate = buildTemplate(chapterTemplate, chapterKeywords);
			if (!brief) {
				System.out.println("Reading work index page template...");
			}
			workIndexContentTemplate = buildTemplate(workIndexTemplate, workIndexKeywords);
			if (!brief) {
				System.out.println("Reading pagination template...");
			}
			paginationContentTemplate = buildTemplate(paginationTemplate, paginationKeywords);
			if (!brief) {
				System.out.println("Reading chapter pagination template...");
			}
			chapterPaginationContentTemplate = buildTemplate(chapterPaginationTemplate, chapterPaginationKeywords);
			if (!brief) {
				System.out.println("Reading field template...");
			}
			fieldContentTemplate = buildTemplate(fieldTemplate, fieldKeywords);
			// Dont bother unless we're actually using the byline
			if (useByLine) {
				if (!brief) {
					System.out.println("Reading byline template...");
				}
				byLineContentTemplate = buildTemplate(byLineTemplate, fieldKeywords);
			}
			
			// If the output directory doesn't exist, create it
			if (!output.exists()) {
				output.mkdirs();
			}
			// Create a standard page footer
			standardFooter = buildPageFooter();
			// And title template
			titleBase = buildPageTitleBase();
			try {
				// Make sure the input and template files exist, just in case.
				if (!input.exists() || !templateFile.exists()) {
					throw new FileNotFoundException();
				}
				else {
					if (verbose) {
						System.out.println("Looking for folders in '" + input.getPath() + "'...");
					}
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
					else if (!brief) {
						System.out.println("Found " + storyFolders.length + " subfolders in input directory: " + Arrays.toString(storyFolders));
					}					
					// Initialize maps for tags, authors, fandoms
					archiveTagMap = new HashMap<String, ArrayList<Story>>();
					archiveAuthorMap = new HashMap<String, ArrayList<Story>>();
					archiveFandomMap = new HashMap<String, ArrayList<Story>>();
					// Create initial story array and output folder
					stories = new Story[storyFolders.length];
					File storiesOutputFolder = new File(output, "stories");
					// Build story objects for each folder, create their output files
					// And add them to relevant tagset/author/fandom/etc hashmaps
					for (int i = 0; i < stories.length; i++) {
						stories[i] = new Story(storyFolders[i], storiesOutputFolder);
						System.out.println("Building story " + stories[i].getStoryTitle() + "...");
						stories[i].buildStory();
						addToStoryMap(archiveTagMap, stories[i].getTagSet(), stories[i]);
						addToStoryMap(archiveAuthorMap, stories[i].getAuthor(), stories[i]);
						addToStoryMap(archiveFandomMap, stories[i].getFandom(), stories[i]);
					}		
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
						String[] allByTitle = buildCategoryPages("", "by_title", stories, titleIndexLabel);
						for (int i = 0; i < allByTitle.length; i++) {
							buildPage(buildStandardPageString(allByTitle[i], titleIndexLabel + " (Page " + (i+1) + ")"), new File(allByTitleFolder, (i+1) + ".html"));
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
						String[] allByLatest = buildCategoryPages("", "latest", stories, latestIndexLabel);
						for (int i = 0; i < allByLatest.length; i++) {
							buildPage(buildStandardPageString(allByLatest[i], latestIndexLabel + " (Page " + (i+1) + ")"), new File(allByLatestFolder, (i+1) + ".html"));
						}
					}					
					// Create fandom index if we have at least one fandom
					if (!skipFandomIndex) {
						// Generate fandom index page
						currentIndex = writeIntoTemplate(workIndexContentTemplate, buildAlphabeticalIndexOf(archiveFandomMap, "fandoms", "Fandoms"));
						// BUILD INDEX PAGE
						buildPage(buildStandardPageString(currentIndex, buildPageTitle("By Fandom")), new File(output, "by_fandom.html"));
						if (verbose) {
							System.out.println("Fandom index page created.");
						}
						// Build fandoms pages
						File fandomFolder = new File(output, "fandoms");
						if (!fandomFolder.exists()) {
							fandomFolder.mkdirs();
						}
						System.out.println("Generating fandom pages...");
						buildArchiveCategory(archiveFandomMap, fandomFolder, "Fandoms", "Stories in ");
					}					
					// Create authors index if we have at least one author
					if (!skipAuthorIndex && archiveHasAuthors) {
						// Generate index page
						currentIndex = writeIntoTemplate(workIndexContentTemplate, buildAlphabeticalIndexOf(archiveAuthorMap, "authors", "Authors"));
						// BUILD INDEX PAGE
						buildPage(buildStandardPageString(currentIndex, buildPageTitle("By Author")), new File(output, "by_author.html"));
						if (verbose) {
							System.out.println("Author index page created.");
						}
						// Build authors pages
						File authorFolder = new File(output, "authors");
						if (!authorFolder.exists()) {
							authorFolder.mkdirs();
						}
						System.out.println("Generating author pages...");
						buildArchiveCategory(archiveAuthorMap, authorFolder, "Authors", "Stories by ");
					}
					// Create tag pages, if any tags are used.
					if (!skipTagPages && archiveHasTags) {
						System.out.println("Generating tag pages...");
						// CREATE TAG FOLDER + PAGES
						buildArchiveCategory(archiveTagMap, new File(output, "tags"), "Tags", "Stories tagged ");
					}
					// Create the site homepage.
					String homePageContent = "";
					if (!skipHomepage) {
						File homePage = new File(input, "index.txt");
						if (homePage.exists()) {
							homePageContent = readFileToString(homePage);
						}
						else {
							homePageContent = buildDefaultHomePage();
						}
						// Puts some site stats on the homepage, if the option to do so is enabled.
						if (homePage.exists() && homePageStatsWidget) {
							// Locate insertion points and insert the stats widget data into the homepage body
							ContentTemplate homePageTemplate = buildTemplate(homePageContent, statsWidgetKeywords);
							String[] stats = new String[] {numberWithCommas(stories.length), numberWithCommas(getTotalWordcount(stories)), 
							numberWithCommas(getTotalFandoms(stories)),  numberWithCommas(getTotalAuthors(stories))};
							// Insert the stats into the homepage text body
							homePageContent = writeIntoTemplate(homePageTemplate, stats);
						}
						// Build the home page at index.html
						buildPage(buildStandardPageString(homePageContent, buildPageTitle("Home")), new File(output, "index.html"));
					}
					// Check for CSS files in input directory, and copy them to the output directory
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
							System.out.println(styleSheets.length + " stylesheets found: " + Arrays.toString(styleSheets));
						}
						// Copy all stylesheets to the output folder
						String styleSheetSource = "";
						for (File styleSheet : styleSheets) {
							styleSheetSource = readFileToString(styleSheet);
							buildPage(styleSheetSource, new File(output, styleSheet.getName()));
						}
					}
				}
			} catch (FileNotFoundException e) {
				if (!input.exists()) {
					System.out.println("Error: " + input.getPath() + " does not exist.");
				}
				if (!templateFile.exists()) {
					System.out.println("Error: " + templateFile.getPath() + " does not exist.");
				}
				System.out.println();
				e.printStackTrace();
			}
		}
		// Report how long it took to build the site, but only if it was built.
		long finalTime = System.currentTimeMillis() - startTime;
		if (building && readyToBuild) {
			System.out.println("Time taken: " + (double)finalTime / (double)1000 + " seconds.");
		}
	}
	
	
	// Parses the argument list for main, and sets a number of variables
	// based on this input.
	public static void parseArgs(String[] args) {
		// Print a warning if there are no arguments given
		if (args.length == 0) {
			building = false;
			System.out.println("You must supply at least one argument. Try 'man' if you need the manual.");
		}
		// Go through the arguments in order and read them
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("-i") || args[i].equals("-input")) {
				if (i == args.length - 1) { // If this is the last argument, report an error
					System.out.println("Error: argument " + args[i] + " was given, but no input was supplied.");
				}
				else { // Otherwise, take the next argument as input, and skip it next iteration
					inputPath = args[i+1];
					i++;
				}
			}
			else if (args[i].equals("-o") || args[i].equals("-output")) {
				if (i == args.length - 1) {
					System.out.println("Error: argument " + args[i] + " was given, but no output location was supplied.");
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
			else if (args[i].equals("-s") || args[i].equals("-site-name")) {
				if (i == args.length - 1) {
					System.out.println("Error: argument " + args[i] + " was given, but no site name was supplied.");
				}
				else {
					siteName = args[i+1];
					i++;
				}
			}
			else if (args[i].equals("-tf") || args[i].equals("-title")) {
				if (i == args.length - 1) {
					System.out.println("Error: argument " + args[i] + " was given, but no title was supplied.");
				}
				else {
					titleTemplate = args[i+1];
					i++;
				}
			}
			else if (args[i].equals("-ff") || args[i].equals("-footer")) {
				if (i == args.length - 1) {
					System.out.println("Error: argument " + args[i] + " was given, but no footer was supplied.");
				}
				else {
					footerTemplate = args[i+1];
					i++;
				}
			}
			else if (args[i].equals("-url") || args[i].equals("-site-path")) {
				if (i == args.length - 1) {
					System.out.println("Error: argument " + args[i] + " was given, but no site path was supplied.");
				}
				else {
					sitePath = args[i+1];
					i++;
				}
			}
			else if (args[i].equals("-mpp") || args[i].equals("-per-page")) {
				if (i == args.length - 1) {
					System.out.println("Error: argument " + args[i] + " was given, but no number was supplied.");
				}
				else {
					try {
						maxItemsPerPage = Integer.parseInt(args[i+1]);
						if (maxItemsPerPage < 1) {
							System.out.println("Error: maximum items per page must be at least 1.");
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
			else if (args[i].equals("-sef") || args[i].equals("--skip-empty-fields")) {
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
			else if (args[i].equals("man") || args[i].equals("help")) {
				building = false;
				printManual();
				break; // ignore further arguments
			}
			else if (args[i].equals("license")) {
				building = false;
				printLicense();
				break; // ignore further arguments
			}
			else if (args[i].equals("credits") || args[i].equals("about")) {
				building = false;
				printCredits();
				break; // ignore further arguments
			}
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
				notesLabel = labelReader.nextLine();
				endNotesLabel = labelReader.nextLine();
			} catch (NoSuchElementException e) {
				System.out.println("Error: reached the end of the field labels file too early. Make sure all fields are accounted for!");
				labelReader.close();
				return; // stop trying to read the file
			}
			labelReader.close();
		} catch (FileNotFoundException e) {
			System.out.println("Error: tried to read the field labels file, but it could not be found.");
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
				System.out.println("Error: reached the end of the ratings file too early. Make sure all ratings are accounted for!");
				ratingsReader.close();
				return; // stop trying to read the file
			}
			ratingsReader.close();
		} catch (FileNotFoundException e) {
			System.out.println("Error: tried to read the ratings file, but it could not be found.");
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
				System.out.println("Error: reached the end of the completion statuses file too early. If you want one to be blank, put a blank line!");
				statusReader.close();
				return; // stop trying to read the file
			}
			statusReader.close();
		} catch (FileNotFoundException e) {
			System.out.println("Error: tried to read the completion statuses file, but it could not be found.");
			e.printStackTrace();
		}
	}

	// Accepts a single String array, and returns a HashMap of the strings and 
	// their indexes
	public static HashMap<String, Integer> generateHashMapFromArray(String[] keys) {
		HashMap<String, Integer> map = new HashMap<String, Integer>();
		for (int i = 0; i < keys.length; i++) {
			map.put(keys[i], i);
		}
		return map;
	}
	
	// Builds a ContentTemplate object based on the template string and an array
	// of keywords, using a temporary <String, Integer> hashmap to speed up 
	// keyword lookup and comparison times.
	public static ContentTemplate buildTemplate(String templateInput, String[] keywords) {
		// For quicker string lookup
		HashMap<String, Integer> keywordMap = generateHashMapFromArray(keywords);
		// For the ContentTemplate constructor
		ArrayList<String> strings = new ArrayList<String>();
		ArrayList<Integer> insertPoints2 = new ArrayList<Integer>();
		// To track in case some keywords aren't found
		boolean[] foundKeywords = new boolean[keywords.length];
		for (int i = 0; i < foundKeywords.length; i++) {
			foundKeywords[i] = false;
		}
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
					insertPoints2.add(keywordMap.get(possibleKeyword));
					// Note that we have found this keyword
					foundKeywords[keywordMap.get(possibleKeyword)] = true;
					// Add the accumulated string to the template
					strings.add(currentSection.toString());
					// Reset the string builder
					currentSection = new StringBuilder();
				}
				else {
					// If it's not a template keyword,
					// Add to the template as normal
					currentSection.append(current);
				}
				// Go back to looking for the next keyword
				inKeyword = false;
			}
			else {
				// Add this section to the current stringbuilder
				currentSection.append(current);
				inKeyword = true;
			}
			//System.out.println("Currently reading: " + currentSection.toString());
		}
		if (currentSection.length() != 0) { // add last string to template if it exists
			strings.add(currentSection.toString());
		}
		templateReader.close();
		for (int i = 0; i < foundKeywords.length; i++) {
			if (foundKeywords[i] == false) {
				System.out.println("Warning: keyword " + keywords[i] + " was not found in the file.");
				insertPoints2.add(-1);
			}
		}
		if (verbose) {
			System.out.println("Insertion points: " + insertPoints2);
		}
		return new ContentTemplate(strings, insertPoints2);
	}
	
	// Write Strings into a template from the ContentTemplate object
	public static String writeIntoTemplate(ContentTemplate contentTemplate, String[] contentToInsert) {
		if (contentTemplate.getTemplateStrings().length < 1) { // don't bother for blank template
			return "";
		}
		StringBuilder content = new StringBuilder();
		// So we don't have to keep getting copies of these objects
		String[] templateStringArr = contentTemplate.getTemplateStrings();
		int[] templateInsertPoints = contentTemplate.getInsertionPoints();
		// if there's nothing to insert, return the full text of the template
		if (contentToInsert.length < 1) { 
			for (int i = 0; i < templateStringArr.length; i++) {
				content.append(templateStringArr[i]);
			}
			return content.toString();
		}
		// Interleave the template strings and content to insert
		for (int i = 0; i < templateStringArr.length; i++) {
			if (verbose) {
				System.out.println("Template line: " + templateStringArr[i]);
				if (i < templateInsertPoints.length) {
					System.out.println("Input line index: " + templateInsertPoints[i]);
				}
			}
			content.append(templateStringArr[i]);
			// If i is in tIP's range, and the value isn't -1 (not found)
			// insert the input string
			if (i < templateInsertPoints.length && templateInsertPoints[i] != -1) {
				content.append(contentToInsert[templateInsertPoints[i]]);
			}
		}
		return content.toString();
	}
	
	// Accepts a string array and returns a HashSet of those values.
	// Mostly deprecated, but keeping it around for generating the html tag hashset.
	public static HashSet<String> generateHashSetFromArray(String[] values) {
		HashSet<String> set = new HashSet<String>();
		for (int i = 0; i < values.length; i++) {
			set.add(values[i]);
		}
		return set;
	}
	
	// Builds a webpage and outputs the input String to it
	public static File buildPage(String inputString, File outputFile) {
		if (!outputFile.exists()) {
			if (verbose) {
				System.out.println("Creating '" + outputFile.getPath() + "', since it does not already exist");
			}
			try {
				outputFile.createNewFile();
			} catch (IOException e) {
				System.out.println("Error: something went wrong while building a page in " + outputFile.getPath());
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
			System.out.println("Uh oh. One of those files doesn't seem to exist.");
			e.printStackTrace();
		} catch (IOException e) {			
			System.out.println("Uh oh. Something went wrong.");
			e.printStackTrace();
		}
		return outputFile;
	}
	
	// Used to build a standard page string
	public static String buildStandardPageString(String inputString, String pageTitle) {
		return writeIntoTemplate(pageContentTemplate, new String[] {pageTitle, inputString, standardFooter});
	}
	
	// Reads a string into a file, with optional casual HTML and tab depth
	public static String readFileToString(File inputFile, int leftTabs, boolean useCasualHTML) {
		//long start = System.currentTimeMillis();
		StringBuilder fileContents = new StringBuilder();
		int lines = 0;
		try {
			Scanner inputReader = new Scanner(inputFile);
			if (leftTabs > 0) {
				while (inputReader.hasNextLine()) {
					lines++;
					if (!ignoreTabs) {
						for (int i = 0; i <= leftTabs; i++) { // match tab depth of div
							fileContents.append("\t");
						}
					}
					fileContents.append(inputReader.nextLine() + "\n");
				}
			}
			else {
				// If we don't need to parse line by line,
				// use an intentionally uncommon character as a delimiter 
				// to speed up reading
				inputReader.useDelimiter(fastScanningPattern);
				while (inputReader.hasNext()) {
					fileContents.append(inputReader.next());
				}
			}
			// Close the scanner once we're done
			inputReader.close();
		} catch (FileNotFoundException e) {
			System.out.println("Error: file " + inputFile.getPath() + " does not exist.");
			e.printStackTrace();
		}
		if (verbose) {
			System.out.println("Read " + lines + " lines from " + inputFile.getPath() + " to string:");
		}		
		if (useCasualHTML) { // if using casual HTML, just run it through the converter
			return convertToHTML(fileContents.toString());
		}
		return fileContents.toString();
	}
	
	//@override
	public static String readFileToString(File inputFile, int leftTabs) {
		return readFileToString(inputFile, leftTabs, false); // default to no casual html
	}
	
	//@override
	public static String readFileToString(File inputFile, boolean useCasualHTML) {
		return readFileToString(inputFile, 0, useCasualHTML);
	}
	
	//@override
	public static String readFileToString(File inputFile) {
		return readFileToString(inputFile, 0, false);
	}
		
	// Wraps each line of a string in HTML paragraph tags, unless it appears to be a non-paragraph 
	// such as an empty line, a div tag, a line break, or horizonal line.
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
			// Skip nextLineReader ahead to the next paragraph break/empty line if possible
			while (nextLineReader.hasNextLine() && !stripLeadingTabsAndSpaces(next).equals("")) {
				next = nextLineReader.nextLine(); 
				nextPosition++;
			}
			// Now we look through the lines between current's starting point and next
			// currentReader reads the first line of the current paragraph; moves a line forward
			if (currentReader.hasNextLine()) {
				current = currentReader.nextLine();
				currentPosition++;
			}
			// Check for any opening tags that would suggest we aren't in a paragraph
			isParagraph = !detectNonParagraphHTMLTags(current);
			// If the current line starts with <p>, don't add it.
			if (isParagraph && !stripLeadingTabsAndSpaces(current).startsWith("<p")) {
				formattedOutput.append("<p>");
			}
			formattedOutput.append(current);
			// If we have more lines than the first, keep reading and adding <br> until nextPos is reached.
			while (currentPosition < nextPosition) {
				// keep track of the last line in case it ends with a </p> tag
				if (currentPosition == nextPosition - 1) {
					current2 = current;
				}
				// Get the next line
				current = currentReader.nextLine();
				currentPosition++;
				// If this is clearly not a paragraph, don't format it
				if (detectNonParagraphHTMLTags(stripLeadingTabsAndSpaces(current))) {
					formattedOutput.append(current + "\n"); // just append without formatting
				}
				// Otherwise, insert line breaks as appropriate
				else {
					formattedOutput.append("\n<br>" + current);
				}
			}
			if (isParagraph) {
				// if the 2nd most recent line of currentReader doesn't end in </p>, add it.
				if (nextLineReader.hasNextLine() && !current2.endsWith("/p>")) {
					formattedOutput.append("</p>\n");
				}
				// Otherwise, if we're at the end of the string, check the last line
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
		if (s.equals("") || nonParagraphHTMLTags.contains(s)) { // treat a blank line as not requiring html
			return true;
		}
		// Strip whitespace, and get a substring of only the first maxTagLength+1 characters
		String comparisonSubstring = s.replaceAll("\\s", ""); 
		int maxTagLength = 3;
		if (comparisonSubstring.length() > maxTagLength) { // only get the substring if it's longer than maxTagLength
			comparisonSubstring = comparisonSubstring.substring(0, maxTagLength);
			//System.out.print(comparisonSubstring + "\t");
		}
		// Check progressively shorter versions of the string against the set
		// of non-paragraph HTML tags
		int j = comparisonSubstring.length(); // in case the string was shorter than maxTagLength
		for (int i = j; i > 0; i--) {
			if (nonParagraphHTMLTags.contains(comparisonSubstring)) {
				return true;
			}
			comparisonSubstring = comparisonSubstring.substring(0, i);
		}
		return false;
	}
	
	// Returns a version of a string without whitespace at the beginning
	public static String stripLeadingTabsAndSpaces(String s) {
		if (s.equals("")) { // if length is 0, don't bother
			return s;
		}
		int i = 0;
		while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == '\t')) {
			i++;
		}
		return s.substring(i, s.length());
	}
	
	// Counts and returns how many tabs a string begins with.
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
	
	// Formats the wordcount as a String with commas (i.e. 1,234,567)
	public static String numberWithCommas(int n) {
		StringBuilder numberWithCommas = new StringBuilder();
		int magnitude = 1;
		int chunks = -1; // last 3 digits don't count as a chunk
		// How many comma-separated "chunks" the number can broken into
		while (n / magnitude >= 1) {
			chunks++;
			magnitude *= 1000; // assume western-style 3-digit chunks
		}
		int currentChunk = 0;
		for (int i = chunks; i >= 0; i--) {
			currentChunk = (n - (n % (int)Math.pow(1000, i))); // get the next chunk of digits as an int
			if (i != chunks) { //dont do this the first time
				if (currentChunk < 100) {
					numberWithCommas.append(0);
				}
				if (currentChunk < 10) {
					numberWithCommas.append(0);
				}
			}
			n = n - currentChunk; // remove it from n
			// Add to string the current chunk, without trailing zeroes, and then a comma
			numberWithCommas.append(Integer.toString(currentChunk  / (int)Math.pow(1000, i)));
			if (i != 0) {
				numberWithCommas.append(",");
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
			// Check that c is actually a letter! 64-91 is caps, 96-123 is lower
			if (atStartOfWord && ((c > 64 && c < 91) || (c > 96 && c < 123))) {
				titleBuilder.append(Character.toUpperCase(c));
				atStartOfWord = false;
			}
			else {
				titleBuilder.append(c);
			}
			if (c == ' ' || c == '\t' || c == '.') {
				atStartOfWord = true;
			}
		}
		return titleBuilder.toString();
	}
	
	// Converts potentially unsafe input strings (like story tags) into strings
	// that are safe for URLs. Unsafe characters are replaced with "_##" where
	// "##" is the int value of the character.
	public static String toSafeURL(String s) {
		StringBuilder url = new StringBuilder();
		int c = 0;
		for (int i = 0; i < s.length(); i++) {
			c = s.charAt(i); // get the int value of the ith character
			// 32 = space
			if (c == 32) {
				url.append('-');
			}
			// 45 = '-', 95 = '_', 48-57 = digits 0-9, 65-90 = A-Z, 97-122 = a-z
			else if (!((c > 96 && c < 123) || (c > 64 && c < 91) || (c > 47 && c < 58))) {
				url.append("_" + c);
			}
			else {
				url.append(s.charAt(i));
			}
		}
		return url.toString();
	}
	
	// Removes a leading "the" (and puts stuff into all lowercase) for comparison
	public static String stripLeadingThe(String s) {
		s = s.toLowerCase();
		if (s.startsWith("the ")) {
			s = s.replace("the ", "");
		}
		return s;
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
		if (subtitle.equals("")) {
			return buildPageTitle(title);
		}
		return titleTemplate.replace("{S}", buildPageTitle(subtitle)).replace("{T}", title);
	}
	
	// Used recursively to build page titles
	public static String buildPageTitle(String title) {
		if (title.equals("")) {
			return siteName;
		}
		return titleBase.replace("{T}", title);
	}
	
	// Build page title base
	public static String buildPageTitleBase() {
		if (siteName.equals("")) {
			return "";
		}
		return titleTemplate.replace("{S}", siteName);
	}
	
	public static String buildPageFooter() {
		return footerTemplate.replace("{SITENAME}", siteName);
	}
	
	public static String buildDefaultHomePage() {
		return "<p>Welcome to " + siteName + "!</p>";
	}
	
	// Returns an array of three strings - the listing nav, the category name
	// and the string for the index of categories. Used to build the fandom 
	// and author indexes.
	// Not currently paginated.
	public static String[] buildAlphabeticalIndexOf(HashMap<String, ArrayList<Story>> categories, String categoryFolderURL, String categoryName) {
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
				System.out.println(i + ": Grouping for " + categoryArray[i] + ": " + Character.toString(b));
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
			categoryIndex.append("<li><a href=\"" + sitePath + categoryFolderURL + "/" + toSafeURL(categoryArray[i]) + "/1.html\">" + 
			categoryArray[i] + "</a> (" + categories.get(categoryArray[i]).size() + ")</li>");
		}
		// Close the last unordered list
		if (categoryArray.length != 0) {
			categoryIndex.append("</ul>\n");
		}
		String listingNavString = "";
		if (!listingNav.equals("")) { // only create this if there's something to put in it
			listingNavString = workIndexNavigationTemplate.replace("{C}", listingNav.toString()).replace("{L}", "Navigation");;
		}
		return new String[] {listingNavString, categoryName, "", categoryIndex.toString()};
	}	
	
	// Build all the pages for a category like tags/fandom/author/etc
	public static void buildArchiveCategory(HashMap<String, ArrayList<Story>> map, File categoryFolder, String categoryLabel, String titleLabel) {
		// Create the tag folder if it doesn't already exist
		if (!categoryFolder.exists()) {
			categoryFolder.mkdirs();
		}
		// For each tag, create the tag pages and write them to file with the url
		// [name of the tagOutputFolder]/[URL-safe version of the tag].html
		File categorySubfolder;
		for (String category : map.keySet()) {
			categorySubfolder = new File(categoryFolder, toSafeURL(category));
			categorySubfolder.mkdirs();
			// Sort stories in the category by date updated
			Collections.sort(map.get(category), new DateUpdatedComparator());
			String[] pages = buildCategoryPages(categoryFolder.getName() + "/", category, map.get(category), (titleLabel + toTitleCase(category)));
			if (verbose) {
				System.out.println("Created " + pages.length + " page[s] for category " + category);
			}
			for (int i = 0; i < pages.length; i++) {
				buildPage(buildStandardPageString(pages[i], (titleLabel + category + " (Page " + (i+1) + ")")), 
				new File(categorySubfolder, (i+1) + ".html")); // page URLs start at 1
			}
		}
		if (!brief) {
			System.out.println("Created " + map.keySet().size() + " category folder[s] for " + categoryLabel);
		}
	}
	
	// Create a string array for all pages of all works with a particular tag
	// or category, sorted in reverse chronological order, with pagination
	public static String[] buildCategoryPages(String categoryFolderURL, String category, ArrayList<Story> relatedStories, String categoryLabel) {
		// Figure out how many pages we need to generate
		int totalPages = relatedStories.size() / maxItemsPerPage;
		if (relatedStories.size() % maxItemsPerPage != 0) {
			totalPages++;
		}
		if (verbose) {
			System.out.println("Total pages that should be generated for " + category + ": " + totalPages);
		}
		String[] pageStrings = new String[totalPages]; // array to store the pages
		StringBuilder pageOutput; // for building each page
		String[] indexPageElements; // for inserting into template
		// Build all pages under the category
		for (int i = 0; i < totalPages - 1; i++) {
			pageOutput = new StringBuilder();
			// For entries in relatedStories at ids i * maxPerPage + 0 to i * maxPerPage + maxPerPage - 1
			// (i.e. 0th to maxPerPage-1th from an offset of maxPerPage * however
			// many pages have been created already), build an index page.
			for (int j = 0; j < maxItemsPerPage; j++) {
				// add the infobox for each story tagged on that page
				pageOutput.append(relatedStories.get((i * maxItemsPerPage) + j).getStoryInfo());
			}
			// Generate pagination buttons from template
			pageOutput.append(buildStandardPaginationString(generatePagination(categoryFolderURL + toSafeURL(category), i, totalPages)));
			// Create page elements array
			indexPageElements = new String[] {"", categoryLabel, 
			("Showing " + ((i * maxItemsPerPage) + 1) + "-" + ((i + 1) * maxItemsPerPage) + " of " + relatedStories.size()),
			pageOutput.toString()};
			pageStrings[i] = writeIntoTemplate(workIndexContentTemplate, indexPageElements);
		}
		// Generate the last page, which might not be full length.
		pageOutput = new StringBuilder();
		for (int i = (maxItemsPerPage * (totalPages - 1)); i < relatedStories.size(); i++) {
			pageOutput.append(relatedStories.get(i).getStoryInfo());
		}
		// Generate pagination for final page
		pageOutput.append(buildStandardPaginationString(generatePagination(categoryFolderURL + toSafeURL(category), totalPages - 1, totalPages)));
		// Create page elements array for it
		indexPageElements = new String[] {"", categoryLabel, 
		("Showing " + ((maxItemsPerPage * (totalPages - 1)) + 1) + "-" + relatedStories.size() + " of " + relatedStories.size()), pageOutput.toString()};
		// Insert the resulting page into the last entry of pageStrings
		pageStrings[totalPages - 1] = writeIntoTemplate(workIndexContentTemplate, indexPageElements);
		// Return the array
		return pageStrings;
	}
	
	//@override
	public static String[] buildCategoryPages(String category, ArrayList<Story> relatedStories) {
		return buildCategoryPages((output.getName() + "/" + category), category, relatedStories, "Stories in ");
	}
	
	//@override
	public static String[] buildCategoryPages(String categoryFolderURL, String category, Story[] relatedStories, String label) {
		ArrayList<Story> storiesArrayList = new ArrayList<Story>(relatedStories.length);
		for (Story story : relatedStories) {
			storiesArrayList.add(story);
		}
		return buildCategoryPages(categoryFolderURL, category, storiesArrayList, label);
	}
	
	// Build pagination from a folder URL, current page, and maximum # of pages
	public static String[] generatePagination(String categoryFolderURL, int currentPage, int totalPages) {
		String[] paginationContents = new String[] {"", "", "", "", ""}; // number of elements in pagination template
		// If there's no pagination, don't even bother.
		if (totalPages == 1) {
			return paginationContents;
		}
		currentPage++; // since currentPage starts at 0 but pages start at 1
		// If we have a page 2 pages back, link it
		if (!skipJumpPagination && currentPage > 2) {
			paginationContents[1] = "<a href=\"" + sitePath + categoryFolderURL + "/" + 
			(currentPage - 2) + ".html\">" + (currentPage - 2) + "</a>";
		}
		// If we have a previous page
		if (currentPage > 1) {
			if (!skipJumpPagination) {
				// Page n-1 and n-2 go in the same section
				paginationContents[1] = paginationContents[1] + "\n<a href=\"" + sitePath + categoryFolderURL + "/" + 
				(currentPage - 1) + ".html\">" + (currentPage - 1) + "</a>";
			}
			// Previous button
			paginationContents[0] = "<a href=\"" + sitePath + categoryFolderURL + "/" + 
			(currentPage - 1) + ".html\">" + prevPageLabel + "</a>";
		}
		// The current Page
		if (!skipJumpPagination) {
			paginationContents[2] = Integer.toString(currentPage);
		}
		// If there's a next page
		if (currentPage < totalPages) {
			if (!skipJumpPagination) {
				paginationContents[3] = "<a href=\"" + sitePath + categoryFolderURL + "/" + 
				(currentPage + 1) + ".html\">" + (currentPage + 1) + "</a>";
			}
			// Next button
			paginationContents[4] = "<a href=\"" + sitePath + categoryFolderURL + "/" + 
			(currentPage + 1) + ".html\">" + nextPageLabel + "</a>";
		}
		// If there's a page after the next, link that
		if (!skipJumpPagination && currentPage < (totalPages - 1)) {
			// Page n+1 and n+2 go in the same section
			paginationContents[3] = paginationContents[3] + "\n<a href=\"" + sitePath + categoryFolderURL + "/" + 
			(currentPage + 2) + ".html\">" + (currentPage + 2) + "</a>";
		}
		return paginationContents;
	}
	
	// Used to build a standard pagination string
	public static String buildStandardPaginationString(String[] inputStrings) {
		return writeIntoTemplate(paginationContentTemplate, inputStrings);
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
		int numberOfFandoms = 0;
		// Store strings in a hashset for fast lookup
		HashSet<String> fandoms = new HashSet<String>();
		for (Story s : stories) {
			// if the fandom isn't in the set, add it, and increment total
			if (!fandoms.contains(s.getFandom())) {
				fandoms.add(s.getFandom());
				numberOfFandoms++;
			}
		}
		return numberOfFandoms;
	}
	
	// Gets the total number of authors in the archive
	// May be deprecated if/when a method is added to cover unique qualities in general
	public static int getTotalAuthors(Story[] stories) {
		int numberOfAuthors = 0;
		// Store strings in a hashset for fast lookup
		HashSet<String> authors = new HashSet<String>();
		for (Story s : stories) {
			// if the author isn't in the set, add it, and increment total
			if (!authors.contains(s.getAuthor())) {
				authors.add(s.getAuthor());
				numberOfAuthors++;
			}
		}
		return numberOfAuthors;
	}
	
	// Add tags and associated story to an archive category hashmap
	public static void addToStoryMap(HashMap<String, ArrayList<Story>> map, HashSet<String> newTags, Story story) {
		for (String tag : newTags) {
			addToStoryMap(map, tag, story);
		}
	}
	
	//@override, general 'add to story map' method
	public static void addToStoryMap(HashMap<String, ArrayList<Story>> map, String tag, Story story) {
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
		
	// Get verbosity setting
	public static boolean isVerbose() {
		return verbose;
	}
	
	// Returns the story infobox template
	public static ContentTemplate getInfoBoxTemplate() {
		return infoBoxContentTemplate;
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
	
	// Gets the template used for author bylines.
	// If useByLine is false, this returns the standard field template.
	// TO BE DEPRECATED
	public static String getByLine() {
		if (useByLine) {
			return byLineTemplate;
		}
		return fieldTemplate;
	}
	
	// Returns the chapter pagination template.
	public static String getTagTemplate() {
		return tagTemplate;
	}
	
	// Returns the chapter pagination template.
	public static String getTagLastTemplate() {
		return tagLastTemplate;
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
	
	// Gets whether or not to build fields from template. If false, the infobox template
	// should already have them built in.
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
	
	/***
	// Gets the DateTimeFormatter for formatting dates in output strings
	// Currently commented out so it doesn't cause compiling issues.
	public static DateTimeFormatter getDateFormat() {
		return dateFormat;
	}
	***/
	
	
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
	
	// Prints the manual.
	public static void printManual() {
		System.out.println("\nCHIVEGEN ALPHA: FANFIC ARCHIVE BUILDER " + versionString);
		System.out.println("\nBasic command structure:");
		System.out.println("FicArchiveBuilder -i INPUT -o OUTPUT [... other options go here ...]");
		System.out.println("\nREQUIRED PARAMETERS");
		System.out.println("-i, -input\t\tSpecify input folder.");
	//	System.out.println("-t, -template\t\tSpecify template file or folder.");
		System.out.println("-o, -output\t\tSpecify output folder.");
	//	System.out.println("-mt, -max-title-length\tMaximum character length of preview text in page titles.");
		System.out.println("\nGENERAL ARCHIVE FORMATTING OPTIONS");
		System.out.println("-s, -site-name\t\tSpecify website name for use in page titles, etc.\n\t\t\tDefault is \"" + siteName + "\".");
		System.out.println("-url, -site-path\tSpecify a site folder path for links, i.e. \"/name/\".\n\t\t\tDefaults to \"/\".");
		System.out.println("-c, --casual-html\tEnable casual HTML for story input. (May be slow.)");
		System.out.println("-tf, -title\t\tGive a title template for page titles. \n\t\t\tDefault is \"" + titleTemplate + "\".");
		System.out.println("-ff, -footer\t\tGive a footer template for automatic footers. \n\t\t\tDefault is \"" + footerTemplate + "\".");
		System.out.println("\nSTORY INFOBOX OPTIONS");
		System.out.println("--skip-empty-fields\tDon't show placeholder data in story infoboxes.");
		System.out.println("--show-auto-dates\tShow autofilled update and published dates instead of \n\t\t\t\"Undated\".");
		System.out.println("--use-folder-dates\tDefault to the creation and last modified dates \n\t\t\tof story input folders for missing dates.");
		System.out.println("-nl, --no-labels\tDon't add field labels in infoboxes.");
		System.out.println("\nCHAPTER FORMATTING OPTIONS");
		System.out.println("--show-chapter-numbers\tPreface chapter titles with \"Chapter 1: \", \n\t\t\t\"Chapter 2: \", etc.");
		System.out.println("\nSTORY INDEX OPTIONS");
		System.out.println("-sk, --skip-index\tDon't create any index pages.");
		System.out.println("--skip-title-index\tDon't create an index page for stories by title.");
		System.out.println("--skip-fandom-index\tDon't create index pages for fandoms.");
		System.out.println("--skip-author-index\tDon't create index pages for authors.");
		System.out.println("-st, --skip-tags\tDon't generate tag index pages or valid tag links.");
		System.out.println("-ss, --skip-stats\tDon't bother calculating site stats for the homepage.");
		System.out.println("-sc, --skip-css\t\tDon't copy CSS stylesheets from input folder.");
		System.out.println("-skip-jump\t\tGenerate simple pagination, no jump pagination links.");
		System.out.println("--ignore-leading-the\tIgnore a starting \"The\" when sorting by title,\n\t\t\tfandom, etc.");
		System.out.println("\nOTHER OPTIONS");
		System.out.println("-v, --verbose\t\tVerbose mode. Shows extra print statements.\n\t\t\t(Warning! May show a LOT of text, depending on what I've\n\t\t\tremembered to comment out.)");
		System.out.println("-b, --brief\t\tBrief mode. Show fewer print statements.");
		System.out.println("\nOTHER COMMANDS");
		System.out.println("man, help\t\tPrints the manual. (You probably know this one.)");
		System.out.println("about, credits\t\tPrints the credits.");
		System.out.println("license\t\t\tPrints the license. (Not properly implemented yet.)");
	//	System.out.println("-it, --ignore-tabs\tDon't bother indenting input text inside the main div.");
		System.out.println("\nThere are no docs at the moment, but when there are I'll link them.\n");
	}
	
	// Prints the license.
	// TODO: decide on a license, if any!
	public static void printLicense() {
		// LICENSE GOES HERE
		System.out.println("\nThis program is not yet released.");
		System.out.println("\nUntil then: \n\t1) THIS PROGRAM COMES WITH NO WARRANTY OF ANY KIND, and \n\t2) PLEASE DON'T BE STUPID ENOUGH TO USE THIS FOR ANYTHING OF VALUE.\n");
	}
	
	// Prints the credits.
	public static void printCredits() {
		System.out.println("\nChiveGen " + versionString + " is a project created and developed by alignmentDev.");
		System.out.println("\nIt is written in Java, using gedit as the code editor.\n");
	}

}
