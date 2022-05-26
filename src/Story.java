import java.util.*;
import java.io.*;
import java.time.*;
import java.time.format.*;

/***
A class to represent an individual story, with a folder, chapters and metadata.
***/
public class Story {
	// The folder the story files will be written to
	private File storyOutputFolder;
	// An array of input files for chapters
	private File[] chapters;
	// Read from toc.txt, or else autofilled
	private String[] chapterTitles;
	// Tags associated with the work. The array is for displaying the tags in
	// their original order, and the hashset is used for lookup when building
	// tag pages and sorting stories, etc.
	private String[] storyTags;
	private HashSet<String> storyTagSet = new HashSet<String>();
	// Title
	private String storyTitle = "";
	// Fandom
	private String fandom = "";
	// Story summary. Included in infobox.
	private String summary = "";
	// Story notes. Displayed on page of first chapter.
	private String storyNotes = "";
	// These strings are used for display purposes only right now
	// and may be deprecated later
	private String datePublished = "";
	private String dateUpdated = "";
	// The actual dates, if possible to parse. Input file should use ISO format.
	private LocalDate updated; 
	private LocalDate published;
	// Story author.
	private String author = "";
	// Total story wordcount.
	// It is initially set to -1 because 0 could mean a story 0 words long.
	private int wordcount = -1; 
	// Completion status. Defaults to false (incomplete).
	private boolean isComplete = false;
	// Rating (enum). Initialized to no rating in case none is given.
	private Rating storyRating = Rating.UNRATED;
	// In case no storyinfo.txt file is found
	private boolean hasStoryDataFile = false;
	// In case one or both dates in storyinfo.txt are missing, incorrectly formatted 
	// or otherwise can't be used
	private boolean hasDateUpdated = false;
	private boolean hasDatePublished = false;
	// If there are no tags...
	private boolean hasTags = false;
	// The story infobox, so we don't have to constantly generate it for new pages.
	private String storyInfo = "";

	public Story(File inputFolder, File outputFolder) {
		// Set output folder with same name as input, but in output path
		storyOutputFolder = new File(outputFolder, inputFolder.getName());
		// Accept all files following the pattern of "ch[...].txt" as chapters
		chapters = inputFolder.listFiles(new FilenameFilter() {
		public boolean accept(File dir, String name) {
			return (name.toLowerCase().startsWith("ch") && name.toLowerCase().endsWith(".txt"));
		}
		});
		Arrays.sort(chapters);
		if (FicArchiveBuilder.isVerbose()) {
			System.out.println("Creating story from folder " + inputFolder.getPath());
			System.out.println("Chapter files found: " + Arrays.toString(chapters));
		}
		chapterTitles = new String[chapters.length];
		// Check if table of contents file exists, and fill out toc with 
		// generic chapter names if it does not
		File tocFile = new File(inputFolder, "toc.txt");
		if (!tocFile.exists()) {
			if (FicArchiveBuilder.isVerbose()) {
				System.out.println("toc.txt was not found for story " + inputFolder.getName() + 
				". Autofilling chapter titles...");
			}
			for (int i = 0; i < chapters.length; i++) {
				chapterTitles[i] = ("Chapter " + (i+1));
			}
		}
		else {
			try {
				Scanner tocReader = new Scanner(tocFile);
				for (int i = 0; i < chapters.length; i++) {
					if (tocReader.hasNextLine()) {
						chapterTitles[i] = tocReader.nextLine();
					}
					else {
						chapterTitles[i] = ("Chapter " + (i+1));
					}
				}
				tocReader.close();
			} catch (FileNotFoundException e) {
				System.out.println("Warning: toc.txt was not found.");
			}
		}
		// Check for story metadata file. Contents of this file are formatted as:
		// Field Name: File Content
		// Second Field Name: Content
		// ...
		// etc
		File storyDataFile = new File(inputFolder, "storyinfo.txt");
		if (storyDataFile.exists()) {
			hasStoryDataFile = true;
			try {
				Scanner storyDataReader = new Scanner(storyDataFile);
				int i = 0;
				if (FicArchiveBuilder.isVerbose()) {
					System.out.println("Getting story metadata from file...");
				}
				while (storyDataReader.hasNextLine()){
					i++;
					// Split only after the first colon, so summaries, titles, etc can still contain extra colons.
					String[] currentLineData = storyDataReader.nextLine().split(": ", 2);
					if (currentLineData.length > 1) {
						try {
							// Puts data from storyData.txt into the key-value hashset
							// Using the text before the colon as the key
							// The key is converted to lowercase before adding, to avoid case issues
							currentLineData[0] = currentLineData[0].toLowerCase();
							if (currentLineData[0].equals("title")) {
								storyTitle = currentLineData[1];
							}
							else if (currentLineData[0].equals("fandom")) {
								fandom = currentLineData[1];
							}
							else if (currentLineData[0].equals("author") || currentLineData[0].equals("creator")) {
								author = currentLineData[1];
								FicArchiveBuilder.setHasAuthors(true);
							}
							else if (currentLineData[0].equals("summary")) {
								// Since the summary and notes templates are very short 
								// and only used once per story, we can just regex this in
								summary = currentLineData[1];
							}
							else if (currentLineData[0].equals("notes")) {
								storyNotes = currentLineData[1];
							}
							else if (currentLineData[0].equals("tags") || currentLineData[0].equals("characters")) {
								// Read tags as a comma-separated list, and put them in the array and the hashset
								storyTags = currentLineData[1].split(", ");
								for (String tag : storyTags) {
									storyTagSet.add(tag.toLowerCase()); // ignore case for tagsets
								}
								hasTags = true;
								FicArchiveBuilder.setHasTags(true);
							}
							else if (currentLineData[0].equals("words") || currentLineData[0].equals("length")) {
								currentLineData[1] = currentLineData[1].replace(",", ""); // strip commas
								currentLineData[1] = currentLineData[1].replace("\\s", ""); // strip whitespace
								try {
									wordcount = Integer.parseInt(currentLineData[1]);
								} catch (IllegalArgumentException e) {
									System.out.println("Error: wordcount for story in folder " + inputFolder.getName() + 
									" was improperly formatted and could not be parsed. Wordcount will be counted automatically instead.");
									wordcount = -1;
								}
							}
							else if (currentLineData[0].equals("rating")) {
								// Interpret the rating as a Rating enum value
								String r = currentLineData[1].toLowerCase();
								if (r.equals("g") || r.equals("k")) {
									storyRating = Rating.G;
								}
								else if (r.equals("pg") || r.equals("k+")) {
									storyRating = Rating.PG;
								}
								else if (r.equals("t") || r.equals("teen")) {
									storyRating = Rating.TEEN;
								}
								else if (r.equals("m") || r.equals("ma") || r.equals("mature")) {
									storyRating = Rating.MATURE;
								}
								else if (r.equals("e") || r.equals("x") || r.equals("explicit")) {
									storyRating = Rating.EXPLICIT;
								}
							}
							else if (currentLineData[0].equals("complete")) {
								if (currentLineData.length == 1) { // if the line is just "complete", treat it like "complete: true"
									isComplete = true;
								}
								else {
									currentLineData[1] = currentLineData[1].toLowerCase();
									if (currentLineData[1].equals("1") || currentLineData[1].equals("yes") || currentLineData[1].equals("true")) {
										isComplete = true;
									}
									else { // default to incomplete if no status given
										isComplete = false;
									}
								}
							}
							else if (currentLineData[0].equals("updated") || currentLineData.equals("date updated")) {
								dateUpdated = currentLineData[1];
								try {
									updated = LocalDate.parse(currentLineData[1].replaceAll("\\s", "")); //strip whitespace just in case
									hasDateUpdated = true;
								} catch (DateTimeParseException e) {
									System.out.println("Error in update date for story '" + inputFolder.getName() + "': date input must be in ISO format (YYYY-MM-DD).");
									hasDateUpdated = false;
								}
							}
							else if (currentLineData[0].equals("published") || currentLineData[0].equals("posted") ||
							currentLineData[0].equals("date published") || currentLineData[0].equals("date posted")) {
								datePublished = currentLineData[1];
								try {
									published = LocalDate.parse(currentLineData[1].replaceAll("\\s", ""));
									hasDatePublished = true;
								} catch (DateTimeParseException e) {
									System.out.println("Error in pub date for story '" + inputFolder.getName() + "': date input must be in ISO format (YYYY-MM-DD).");
									hasDatePublished = false;
								}
							}
						} catch (IndexOutOfBoundsException e) {
							System.out.println("Error: badly formatted metadata entry in line " + i + " of " + storyDataFile.getPath() + "");
						}
					}
				}
				storyDataReader.close();
			} catch (FileNotFoundException e) {
				System.out.println("Error: story data file was unexpectedly missing.");
				e.printStackTrace();
			}
		}
		else {
			System.out.println("Warning: storyinfo.txt not found for story folder " + inputFolder.getPath());
		}
		// Fill out story data with default values if missing
		// Summary, date updated, and notes are acceptable to skip.
		if (!hasStoryDataFile || storyTitle.equals("")) {
			storyTitle = inputFolder.getName();
		}
		if (!FicArchiveBuilder.skipEmptyFields() && (!hasStoryDataFile || fandom.equals(""))) {
			fandom = "Unknown Fandom";
		}
		if (!FicArchiveBuilder.skipEmptyFields() && (!hasStoryDataFile || datePublished.equals(""))) {
			datePublished = "Undated";
		}
		if (!hasStoryDataFile || dateUpdated.equals("")) {
			if (!FicArchiveBuilder.skipEmptyFields()) {
				dateUpdated = "";
			}
			else {
				dateUpdated = "Undated";
			}
		}
		if (!hasDatePublished || !hasStoryDataFile) {	
			published = LocalDate.of(1970, 1, 1); // placeholder data
		}
		if (!hasDateUpdated || !hasStoryDataFile) {
			updated = published;
		}
		if (!hasStoryDataFile || summary.equals("")) {
			summary = "";
		}
		if (!hasStoryDataFile || storyNotes.equals("")) {
			storyNotes = "";
		}
		// If no valid wordcount is supplied in file, get it manually
		if (wordcount == -1) {
			wordcount = countWords();
		}
		if (hasDateUpdated) { // so we can implement custom formatting for dates later if so desired
			dateUpdated = updated.toString();
		}
		if (hasDatePublished) {
			datePublished = published.toString();
		}
		storyInfo = getStoryInfo();
	}
	
	// Reads through all chapter files to get a total story wordcount.
	public int countWords() {
		if (FicArchiveBuilder.isVerbose()) {
			System.out.println("Getting wordcount manually for story " + storyTitle + " using files: " + Arrays.toString(chapters));
		}
		int totalWords = 0;
		for (int i = 0; i < chapters.length; i++) {
			try {
				if (FicArchiveBuilder.isVerbose()) {
					System.out.println("Getting wordcount for chapter " + i);
				}
				Scanner wordCounter = new Scanner(chapters[i]);
				wordCounter.useDelimiter(" ");
				while (wordCounter.hasNext()) {
					totalWords++;
					wordCounter.next();
				}
				wordCounter.close();
			} catch (FileNotFoundException e) {
				System.out.println("Error: while getting wordcount for chapter " + i +
				", file '" + chapters[i].getName() + "' was not found.");
				e.printStackTrace();
			}
		}
		return totalWords;
	}
	
	// Calls buildChapter to get full chapter page strings, writes them to file, 
	// and creates a corresponding table of contents
	public void buildStory() {
		if (FicArchiveBuilder.isVerbose()) {
			System.out.println("Writing story " + storyTitle + " to output folder...");
		}
		if (FicArchiveBuilder.isVerbose()) {
			System.out.println("Creating story output folder...");
		}		
		// Create the story output folder
		storyOutputFolder.mkdirs();
		// Create table of contents string to write to file later
		StringBuilder toc = new StringBuilder("<ol>\n");
		// Stores the current chapter being made
		String currentChapter = "";
		// Iterate through chapters
		for (int i = 0; i < chapters.length; i++) {
			if (FicArchiveBuilder.isVerbose()) {
				System.out.println("Building page for chapter " + (i+1) + "  of " + chapters.length);
			}
			// Create the page from the string output of buildChapter()
			// with file path of storyOutputFolder + the name of the input file as .html
			FicArchiveBuilder.buildPage(this.buildChapter(i), new File(storyOutputFolder, chapters[i].getName().replace(".txt", ".html")));
			toc.append("<li><a href=\"" + chapters[i].getName().replace(".txt", ".html") + "\">" + chapterTitles[i] + "</a></li>\n");
		}		
		toc.append("</ol>\n");
		// Build the table of contents
		if (FicArchiveBuilder.isVerbose()) {
			System.out.println("Building table of contents...");
		}	
		// CREATE TABLE OF CONTENTS PAGE
		FicArchiveBuilder.buildPage(FicArchiveBuilder.buildStandardPageString(toc.toString(), 
		FicArchiveBuilder.buildPageTitle("Table of Contents", storyTitle)), new File(storyOutputFolder, "toc.html"));
	}
	
	// Builds a string containing the content of the chapter, notes, infobox, etc.
	public String buildChapter(int chapterNumber) {
		if (FicArchiveBuilder.isVerbose()) {
			System.out.println("Creating string for chapter " + chapterNumber + "...");
		}
		File currentChapter = chapters[chapterNumber];
		// Read the entire chapter's file to the string chapterContent
		if (FicArchiveBuilder.isVerbose()) { 
			System.out.println("Inserting chapter content into chapter template...");
		}
		// Get the full chapter string for writing into a page
		String chapterString = FicArchiveBuilder.writeIntoTemplate(FicArchiveBuilder.getChapterTemplate(), 
		FicArchiveBuilder.reassociateIntegers(createChapterHashMap(chapterNumber), FicArchiveBuilder.getStandardChapterInsertionPoints()));
		String pageTitle = "";
		if (chapters.length > 1) { // Skip chapter title if only 1 chapter exists
			pageTitle = "Chapter " + (chapterNumber+1) + ": " + chapterTitles[chapterNumber];
		}
		// Get the full string for the output webpage
		return FicArchiveBuilder.buildStandardPageString(chapterString, 
		FicArchiveBuilder.buildPageTitle(pageTitle, storyTitle));
	}
		
	// Gets the prebuilt story info box, complete with link.
	public String getStoryInfo() {
		// If we haven't build the infobox yet, do so
		// Otherwise, return the version that already exists
		if (storyInfo == "") {
			storyInfo = buildStoryInfoBox();
		}
		return storyInfo;
	}	
	
	// Builds a string for the story infobox.
	public String buildStoryInfoBox() {
		// Reset to blank
		storyInfo = "";
		// Create infobox from template
		return FicArchiveBuilder.writeIntoTemplate(FicArchiveBuilder.getStoryInfoTemplate(), 
		FicArchiveBuilder.reassociateIntegers(createStoryInfoBoxHashMap(), FicArchiveBuilder.getStandardInfoBoxInsertionPoints()));
	}
	
	// Gets the local URL of any arbitrary chapter.
	public String getChapterURL(int chapterNumber) {
		return chapters[chapterNumber].getName().replace(".txt", ".html");
	}
	
	// Creates HashMap of fields and content for a story infobox.
	public HashMap<String, String> createStoryInfoBoxHashMap() {
		String url = (FicArchiveBuilder.getSitePath() + "stories/" + storyOutputFolder.getName() + "/" + chapters[0].getName().replace(".txt", ".html"));
		String titleLink = "<a href=\"" + url + "\">" + storyTitle + "</a>";
		String completionStatus = "No";
		if (isComplete) {
			completionStatus = "Yes";
		}
		// Fields: title (link), fandom, wordcount, chapter #, published, updated, summary, completion status
		String[] storyPageData;
		if (FicArchiveBuilder.generateInfoBoxTemplateFields()) {
			String field = FicArchiveBuilder.getFieldTemplate(); // since this will be reused a lot
			storyPageData = new String[] {titleLink, buildField(field, FicArchiveBuilder.getFandomLabel(), fandom), 
			buildField(field, FicArchiveBuilder.getWordcountLabel(), numberWithCommas(wordcount)), 
			buildField(field, FicArchiveBuilder.getChapterCountLabel(), Integer.toString(chapters.length)), 
			buildField(field, FicArchiveBuilder.getDatePublishedLabel(), datePublished), 
			buildField(field, FicArchiveBuilder.getDateUpdatedLabel(), dateUpdated), 
			buildField(FicArchiveBuilder.getSummaryTemplate(), FicArchiveBuilder.getSummaryLabel(), summary), 
			buildField(field, FicArchiveBuilder.getCompletionLabel(), completionStatus), 
			buildField(FicArchiveBuilder.getByLine(), FicArchiveBuilder.getAuthorLabel(), author),
			buildField(field, FicArchiveBuilder.getTagsLabel(), getFormattedTags())};
		}
		else {
			storyPageData = new String[] {titleLink, fandom, Integer.toString(wordcount), Integer.toString(chapters.length),
			datePublished, dateUpdated, summary, completionStatus, author, getFormattedTags()};
		}
		return FicArchiveBuilder.generateHashMapFromArrays(FicArchiveBuilder.getStoryInfoKeywords(), storyPageData);
	}
	
	// Creates HashMap of fields and content to insert for a full individual chapter page.
	public HashMap<String, String> createChapterHashMap(int chapterNumber) {
		// Fields: infobox, chapter title, chapter body, pagination (top and bottom)
		if (FicArchiveBuilder.isVerbose()) {
			System.out.println("Creating map of chapter content...");
		}
		String chapterPagination = getChapterPagination(chapterNumber);
		// Don't bother showing chapter title for a single-chapter work
		String chapterTitle = "";
		if (FicArchiveBuilder.showChapterNumbers() && chapters.length > 1) {
			chapterTitle = "Chapter " + Integer.toString(chapterNumber+1) + ": " + chapterTitles[chapterNumber];
		}
		else if (chapters.length != 1) {
			chapterTitle = chapterTitles[chapterNumber];
		}
		// Only show the story notes on the first chapter
		String storyNotesToShow = "";
		if (chapterNumber == 0) {
			storyNotesToShow = buildField(FicArchiveBuilder.getSummaryTemplate(), FicArchiveBuilder.getNotesLabel(), storyNotes);
		}
		// Story infobox, chapter title, story notes, 
		String[] chapterContents = new String[] {getStoryInfo(), chapterTitle, storyNotesToShow, 
		FicArchiveBuilder.readFileToString(chapters[chapterNumber], FicArchiveBuilder.useCasualHTML()), 
		chapterPagination, chapterPagination};
		return FicArchiveBuilder.generateHashMapFromArrays(FicArchiveBuilder.getChapterKeywords(), chapterContents);
	}
	
	// Gets the pagination links for a given chapter.
	public String getChapterPagination(int chapterNumber) {
		if (chapters.length == 1) { // don't bother with paginating oneshots
			return "";
		}
		String previous = "";
		String next = "";
		// If previous/next chapters exist, have the {Previous} and {Next} be replaced with links. 
		// Otherwise, replace them with a blank string.
		if (chapterNumber > 0) {
			previous = "<a href=\"" + getChapterURL(chapterNumber-1) + "\">" + FicArchiveBuilder.getPrevChapterLabel() + "</a>";
		}
		if (chapterNumber < chapters.length - 1) {
			next = "<a href=\"" + getChapterURL(chapterNumber+1) + "\">" + FicArchiveBuilder.getNextChapterLabel() + "</a>";
		}
		// This is small enough regex is probably fine
		return FicArchiveBuilder.getPaginationTemplate().replace("{Previous}", previous).replace("{Next}", next);
	}
	
	// Use replace() to quickly insert data into certain short templated fields
	// This uses the replace() method, because it's currently simpler for a short
	// template
	// This might be replaced with a normal hashmap templating method, but
	// for now the time difference is negligible.
	public String buildField(String field, String label, String content) {
		if (!content.equals("")) {
			// Replace {C} first since it's typically later in the string
			// so searching for {L} won't take as long
			return field.replace("{C}", content).replace("{L}", label);
		}
		return content; // if content is blank, don't create a formatted field
	}
	
	// Gets the tags in order, with formatting (not currently templated)
	public String getFormattedTags() {
		StringBuilder tagList = new StringBuilder();
		String tagURL = "#";
		if (hasTags) {
			for (int i = 0; i < storyTags.length; i++) {
				// don't bother building valid links if no tag pages will be generated
				if (!FicArchiveBuilder.skipTagPages()) { 
					tagURL = FicArchiveBuilder.getSitePath() + "tags/" + FicArchiveBuilder.toSafeURL(storyTags[i].toLowerCase()) + ".html";
				}
				// Create the formatted tag and link
				if (i == (storyTags.length - 1)) { // last tag has special class for CSS usage
					tagList.append("<div class=\"tag last\"><a href=\"" + tagURL + "\">" + storyTags[i] + "</a></div>");
				}
				else {
					tagList.append("<div class=\"tag\"><a href=\"" + tagURL + "\">" + storyTags[i] + "</a></div>");
				}
			}
		}
		return tagList.toString();
	}
	
	// Formats the wordcount as a String with commas (i.e. 1,234,567)
	public String numberWithCommas(int n) {
		StringBuilder numberWithCommas = new StringBuilder();
		int magnitude = 1;
		int chunks = -1; // last 3 digits don't count as a chunk
		// How many comma-separated "chunks" the number can broken into
		while (n / magnitude >= 1) {
			chunks++;
			magnitude *= 1000; // assume western-style 3-digit chunks
		}
		int currentChunk = 0;
		for (int i = chunks; i > 0; i--) {
			currentChunk = (n - (n % (1000^i))); // get the next chunk of digits as an int
			n = n - currentChunk; // remove it from n
			// Add to string the current chunk, without trailing zeroes, and then a comma
			numberWithCommas.append(Integer.toString(currentChunk  / (1000^i)) + ",");
		}
		return numberWithCommas.append(Integer.toString(n)).toString();
	}
	
	// Gets the tags array
	public String[] getStoryTags() {
		return storyTags;
	}
	
	// Gets the tagset
	public HashSet<String> getTagSet() {
		return storyTagSet;
	}
	
	// Gets the story title.
	public String getStoryTitle() {
		return storyTitle;
	}
	
	// Gets the fandom.
	public String getFandom() {
		return fandom;
	}
	
	// Gets the wordcount.
	public int getWordCount() {
		return wordcount;
	}
	
	// Gets the author.
	public String getAuthor() {
		return author;
	}
	
	// Gets the date updated, or failing that, the date published.
	public LocalDate getDateUpdated() {
		if (hasDateUpdated) {
			return updated;
		}
		return published;
	}
	
	// Gets the rating enum
	public Rating getRating() {
		return storyRating;
	}
	
	// Gets a string for the rating
	// (Note: modify this later to allow custom ratings?)
	public String getRatingString() {
		switch (storyRating) {
			case UNRATED: 
				return "No Rating";
			case G:
				return "G";
			case PG:
				return "PG";
			case TEEN:
				return "Teen";
			case MATURE:
				return "Mature";
			case EXPLICIT:
				return "Explicit";
			default:
				return "No Rating"; // just in case
		}
	}
	
	public String toString() {
		if (storyTitle.equals("")) {
			return storyOutputFolder.getName();
		}
		return storyTitle;
	}
}
