import java.util.*;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.time.*;
import java.time.format.*;

/***
A class to represent an individual story, with a folder, chapters and metadata.
***/
public class Story {
	// The folder the story files will be written to
	private File storyOutputFolder;
	// Title
	private String storyTitle = "";
	// An array of input files for chapters
	private File[] chapters;
	// Read from toc.txt, or else autofilled
	private String[] chapterTitles;
	// Tags associated with the work. The array is for displaying the tags in
	// their original order, and the hashset is used for lookup when building
	// tag pages and sorting stories, etc.
	private String[] storyTags;
	private HashSet<String> storyTagSet = new HashSet<String>();
	// Fandoms.
	private String[] fandoms;
	private HashSet<String> fandomHashSet = new HashSet<String>();
	// Story authors.
	private String[] authors;
	private HashSet<String> authorHashSet = new HashSet<String>();
	// Story summary. Included in infobox.
	private String summary = "";
	// Story notes. Displayed on page of first chapter.
	private String storyNotes = "";
	// End notes. Displayed after last chapter. Currently unused.
	private String storyEndNotes = "";
	// The published and last updated dates. Input file should use ISO format.
	private LocalDate updated; 
	private LocalDate published;
	// Total story wordcount.
	// It is initially set to -1 because 0 could mean a story 0 words long.
	private int wordcount = -1; 
	// Completion status. Defaults to false (incomplete).
	private boolean isComplete = false;
	// Rating (enum). Initialized to no rating in case none is given.
	private Rating storyRating = Rating.UNRATED;
	// In case no storyinfo.txt file is found
	private boolean hasStoryDataFile = false;
	// In case fields in storyinfo.txt are missing, incorrectly formatted 
	// or otherwise can't be used
	private boolean hasDateUpdated = false;
	private boolean hasDatePublished = false;
	private boolean hasFandom = false;
	private boolean hasCompletionStatus = false;
	private boolean hasAuthor = false;
	// If there are no tags...
	private boolean hasTags = false;
	// The story infoboxes, so we don't have to constantly generate them for new pages.
	private String storyInfo = "";
	private String indexStoryInfo = "";

	// Build a new story from an input folder and output folder
	public Story(File inputFolder, File outputFolder) {
		// First, check if inputFolder is actually a folder, or an HTML file.
		// (Might change how this works later.)
		if (!inputFolder.isDirectory()) {
			System.out.println("Error: " + inputFolder.getPath() + " is not a directory.");
		}
		// Set output folder with same name as input, but in output path
		storyOutputFolder = new File(outputFolder, inputFolder.getName());
		// Accept all files following the pattern of "ch[...].txt" as chapters
		chapters = inputFolder.listFiles(new FilenameFilter() {
		public boolean accept(File dir, String name) {
			return (name.toLowerCase().startsWith("ch") && name.toLowerCase().endsWith(".txt"));
		}
		});
		if (chapters.length < 1) {
			File storyHTMLFile = new File(inputFolder + "/story.html");
			if (storyHTMLFile.exists() && !storyHTMLFile.isDirectory()) {
				//TODO
				// parse as an ao3 story file...
			}
			else {
				System.out.println("Warning: no chapter files found in folder " + inputFolder.getPath());
			}
		}
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
			//read config file
			// for each line: use a hashset to check that the label is valid, 
			// then switch statement using 1st char and disambig from there
			hasStoryDataFile = true;
			try {
				Scanner storyDataReader = new Scanner(storyDataFile);
				int i = 0; // track current line for error reporting
				if (FicArchiveBuilder.isVerbose()) {
					System.out.println("Getting story metadata from storyinfo.txt...");
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
							// Check if our metadata's field name is valid, and if so
							// figure out which field it is.
							if (FicArchiveBuilder.getValidStoryMetadataSet().contains(currentLineData[0])) {
								// Since we know it's only a few possibilities
								// we can just check a few chars instead of the
								// whole string
								switch (currentLineData[0].charAt(0)) {
									case 'a':
										parseAsAuthor(currentLineData[1]);
										break;
									case 'c':
										// creator
										if (currentLineData[0].charAt(1) == 'r') {
											parseAsAuthor(currentLineData[1]);
										}
										else if (currentLineData[0].charAt(1) == 'o') {
											// completion status is dealt with later
											break;
										}
										// characters
										else {
											parseAsTags(currentLineData[1]);
										}
										break;
									case 'd':
										// date ...
										if (currentLineData[0].charAt(5) == 'u') {
											// updated
											parseAsDateUpdated(currentLineData[1]);
										}
										else { // published, posted
											parseAsDatePublished(currentLineData[1]);
										}
										break;
									case 'e': // end notes
										storyEndNotes = currentLineData[1];
										break;
									case 'f':
										parseAsFandom(currentLineData[1]);
										break;
									case 'l': // length
										wordcount = parseAsWordcount(currentLineData[1], inputFolder.getName());
										break;
									case 'n':
										storyNotes = currentLineData[1];
										break;
									case 'p': // published, posted
										parseAsDatePublished(currentLineData[1]);
										break;
									case 'r': //rating, rated
										storyRating = parseAsRating(currentLineData[1]);
										break;
									case 's':
										summary = currentLineData[1];
										break;
									case 't':
										// tags
										if (currentLineData[0].charAt(1) == 'a') {
											parseAsTags(currentLineData[1]);
										}
										else { // title
											storyTitle = currentLineData[1];
										}
										break;
									case 'u': // updated
										parseAsDateUpdated(currentLineData[1]);
										break;
									case 'w': //words, wordcount
										wordcount = parseAsWordcount(currentLineData[1], inputFolder.getName());
										break;
									default:
										// do nothing
								}
							}
							if (currentLineData[0].equals("complete")) {
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
								hasCompletionStatus = true;
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
		if (!hasStoryDataFile || storyTitle.equals("")) {
			storyTitle = inputFolder.getName();
		}
		// Summary and notes are acceptable to skip by default.
		if (!hasStoryDataFile || summary.equals("")) {
			summary = "";
		}
		if (!hasStoryDataFile || storyNotes.equals("")) {
			storyNotes = "";
		}
		// Placeholders are used in these fields for sorting purposes
		if (!hasFandom) {
			if (FicArchiveBuilder.isVerbose()) {
				System.out.println("Autofilling fandom...");
			}
			fandoms = new String[] {"No Fandom Given"};
			fandomHashSet.add("no fandom given");
		}
		if (!hasAuthor) {
			if (FicArchiveBuilder.isVerbose()) {
				System.out.println("Autofilling author...");
			}
			authors = new String[] {"Unknown Author"};
			authorHashSet.add("unknown author");
		}
		if (!hasTags) {
			storyTags = new String[]{}; // to prevent a null pointer if accessing storyTags in some way
		}
		// If any date info is missing, fill it in
		if (!hasDateUpdated || !hasDatePublished) {
			if (FicArchiveBuilder.isVerbose()) {
				System.out.println("Autofilling dates...");
			}
			if (FicArchiveBuilder.defaultToEpochDate()) {
				if (!hasDatePublished) {
					published = LocalDate.EPOCH;
				}
				if (!hasDateUpdated && chapters.length > 1) {
					// Get update date if it's not a oneshot
					updated = LocalDate.EPOCH;
				}
				else if (!hasDateUpdated) {
					// Otherwise just use the pub date
					updated = published;
				}
			}
			else {
				// use metadata from input folder. NOT CURRENTLY WORKING
				// setDatesFromMetadata(inputFolder);
			}
		}
		// If no valid wordcount is supplied in file, get it manually
		if (wordcount == -1) {
			wordcount = countWords();
		}
	}
	
	// Parses fandom data
	public void parseAsFandom(String f) {
		fandoms = f.split(", ");
		for (String fandom : fandoms) {
			fandomHashSet.add(fandom.toLowerCase()); // ignore case for hashsets
		}
		hasFandom = true;
	}
	
	// Parses author data
	public void parseAsAuthor(String a) {
		authors = a.split(", ");
		for (String author : authors) {
			authorHashSet.add(author.toLowerCase()); // ignore case for hashsets
		}
		hasAuthor = true;
		// tell main we have at least 1 author
		FicArchiveBuilder.setHasAuthors(true);
	}
	
	// Parse a string as a rating and return it
	public Rating parseAsRating(String r) {
		// Interpret the rating as a Rating enum value
		r = r.toLowerCase();
		if (r.equals("g") || r.equals("k") || r.equals("generalaudiences")) {
			return Rating.G;
		}
		else if (r.equals("pg") || r.equals("k+")) {
			return Rating.PG;
		}
		else if (r.equals("t") || r.equals("teen")) {
			return Rating.TEEN;
		}
		else if (r.equals("m") || r.equals("ma") || r.equals("mature")) {
			return Rating.MATURE;
		}
		else if (r.equals("e") || r.equals("x") || r.equals("nc-17") || r.equals("explicit")) {
			return Rating.EXPLICIT;
		}
		// if it's not any other rating, it's unrated
		return Rating.UNRATED;
	}
	
	// Parse string as wordcount
	public int parseAsWordcount(String w, String wordcountFolderName) {
		w = w.replace(",", "").replace("\\s", ""); // strip whitespace and commas
		try {
			int wc = Integer.parseInt(w);
			return wc;
		} catch (IllegalArgumentException e) {
			System.out.println("Error: wordcount for story in folder " + wordcountFolderName + 
			" was improperly formatted and could not be parsed. Wordcount will be counted automatically instead.");
			return -1;
		}
	}
	
	// Parse a string as tags
	public void parseAsTags(String t) {
		// Read tags as a comma-separated list, and put them in the array and the hashset
		storyTags = t.split(", ");
		for (String tag : storyTags) {
			storyTagSet.add(tag.toLowerCase()); // ignore case for tagsets
		}
		hasTags = true;
		// tell ficarchivebuilder we have at least 1 tag
		FicArchiveBuilder.setHasTags(true);
	}
	
	// Parse string as update date
	public void parseAsDateUpdated(String u) {
		try {
			updated = LocalDate.parse(u.replaceAll("\\s", "")); //strip whitespace just in case
			hasDateUpdated = true;
		} catch (DateTimeParseException e) {
			printInvalidDateWarning("updated");
			hasDateUpdated = false;
		}
	}
	
	public void parseAsDatePublished(String p) {
		try {
			published = LocalDate.parse(p.replaceAll("\\s", ""));
			hasDatePublished = true;
		} catch (DateTimeParseException e) {
			printInvalidDateWarning("published");
			hasDatePublished = false;
		}
	}
	
	// Gets the date string. If defaultDate is true, the date was autofilled
	// from metadata or another default like 1970-01-01.
	public String getDateString(LocalDate date, boolean notDefaultDate) {
		if (notDefaultDate || ((FicArchiveBuilder.showDefaultDates() && !FicArchiveBuilder.skipEmptyFields()))) {
			return date.toString();
		}
		if (FicArchiveBuilder.skipEmptyFields()) {
			return "";
		}
		return "Undated";
	}
	
	public String getDateString(LocalDate date) {
		return getDateString(date, true);
	}
	
	// Gets dates from the file attributes, if no dates are given
	// Published and created should be potentially different dates, but this
	// doesn't seem to work currently. Might be a Linux-specific issue?
	/***
	public void setDatesFromMetadata(File inputStoryFolder) {
		try {
			BasicFileAttributes storyFolderAttributes = Files.readAttributes(Paths.get(inputStoryFolder.getPath()), BasicFileAttributes.class);
			if (!hasDatePublished) {
				published = LocalDate.ofInstant(storyFolderAttributes.creationTime().toInstant(), ZoneId.systemDefault());
			}
			if (!hasDateUpdated && chapters.length > 1) {
				// If there's multiple chapters and no date updated, use the folder's Last Modified date
				updated = LocalDate.ofInstant(storyFolderAttributes.lastModifiedTime().toInstant(), ZoneId.systemDefault());
			}
			else if (!hasDateUpdated) {
				// Otherwise, if it's a oneshot with no valid update date, use the publication date
				updated = published;
			}
		} catch(IOException e) {
			System.out.println("Error: tried to get date-time data from folder '" + inputStoryFolder.getPath() + "' but something went wrong.");
			updated = LocalDate.of(1970, 1, 1); // fallback
			published = LocalDate.of(1970, 1, 1);
			e.printStackTrace();
		}
	}
	***/
	
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
		if (chapters.length < 1) {
			return; // do nothing for an empty story
		}
		if (FicArchiveBuilder.isVerbose()) {
			System.out.println("Writing story " + storyTitle + " to output folder...");
		}	
		// Create the story output folder if it doesn't exist yet
		if (!storyOutputFolder.exists()) {
			storyOutputFolder.mkdirs();
		}
		// Create table of contents string to write to file later
		StringBuilder toc = new StringBuilder("<ol>\n");
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
	
	// Build the story infoboxes
	public void buildInfoboxes() {
		if (chapters.length > 0) {
			storyInfo = buildStoryInfoBox();
			indexStoryInfo = buildIndexStoryInfoBox();
		}	
	}
	
	// Builds a string containing the content of the chapter, notes, infobox, etc.
	public String buildChapter(int chapterNumber) {
		if (FicArchiveBuilder.isVerbose()) {
			System.out.println("Creating string for chapter " + chapterNumber + "...");
		}
		// Read the entire chapter's file to the string chapterContent
		if (FicArchiveBuilder.isVerbose()) { 
			System.out.println("Inserting chapter content into chapter template...");
		}
		// Get the full chapter string for writing into a page
		String chapterString = FicArchiveBuilder.writeIntoTemplate(FicArchiveBuilder.getChapterTemplate(), createChapterContentArray(chapterNumber));
		String pageTitle = "";
		if (chapters.length > 1) { // Skip chapter title if only 1 chapter exists
			pageTitle = getFormattedChapterTitle(chapterNumber);
		}
		// Get the full string for the output webpage
		return FicArchiveBuilder.buildStandardPageString(chapterString, 
		FicArchiveBuilder.buildPageTitle(pageTitle, storyTitle));
	}
		
	// Gets the prebuilt story info box for chapter pages.
	public String getStoryInfo() {
		return storyInfo;
	}	
	
	// Gets the prebuilt story infobox for index listings, complete with link.
	public String getInfoboxForIndex() {
		return indexStoryInfo;
	}	
	
	// Builds a string for the story infobox.
	public String buildStoryInfoBox() {
		// Create infobox from template
		return FicArchiveBuilder.writeIntoTemplate(FicArchiveBuilder.getInfoBoxTemplate(), createInfoBoxContentArray(false));
	}
	
	// Builds a string for the index version of the story infobox.
	public String buildIndexStoryInfoBox() {
		// Create infobox from template
		return FicArchiveBuilder.writeIntoTemplate(FicArchiveBuilder.getIndexInfoBoxTemplate(), createInfoBoxContentArray(true));
	}
	
	// Gets the local URL of any arbitrary chapter.
	public String getChapterURL(int chapterNumber) {
		return chapters[chapterNumber].getName().replace(".txt", ".html");
	}
	
	// Creates HashMap of fields and content for a story infobox.
	public String[] createInfoBoxContentArray(boolean hasLink) {
		// Get the URL for linking to this story
		String url = (FicArchiveBuilder.getSitePath() + "stories/" + storyOutputFolder.getName() + "/" + chapters[0].getName().replace(".txt", ".html"));
		String titleLink = storyTitle;
		if (hasLink) { // only needs the link for the index version
			titleLink = "<a href=\"" + url + "\">" + storyTitle + "</a>";
		}
		// Put Yes/No for Completion Status based on isComplete
		String completionStatus = "No";
		if (isComplete) {
			completionStatus = "Yes";
		}
		// Fields: title (link), fandom, wordcount, chapter #, published, updated, summary, completion status, author, tags, rating
		//String[] storyPageData;
		if (FicArchiveBuilder.generateInfoBoxTemplateFields()) {
			return new String[] {titleLink, buildField(FicArchiveBuilder.getFandomLabel(), getSkippableFandom()), 
			buildField(FicArchiveBuilder.getWordcountLabel(), FicArchiveBuilder.numberWithCommas(wordcount)), 
			buildField(FicArchiveBuilder.getChapterCountLabel(), Integer.toString(chapters.length)), 
			buildField(FicArchiveBuilder.getDatePublishedLabel(), getDateString(published, hasDatePublished)), 
			buildField(FicArchiveBuilder.getDateUpdatedLabel(), getDateString(updated, hasDateUpdated)), 
			buildField(FicArchiveBuilder.getSummaryContentTemplate(), FicArchiveBuilder.getSummaryLabel(), summary), 
			buildField(FicArchiveBuilder.getCompletionLabel(), getSkippableCompletionStatus()), 
			buildField(FicArchiveBuilder.getByLineTemplate(), FicArchiveBuilder.getAuthorLabel(), getSkippableAuthor()),
			buildField(FicArchiveBuilder.getTagsLabel(), getFormattedTags()),
			buildField(FicArchiveBuilder.getRatingLabel(), FicArchiveBuilder.getRatingString(storyRating))};
		}
		return new String[] {titleLink, getSkippableFandom(), Integer.toString(wordcount), Integer.toString(chapters.length),
		getDateString(published, hasDatePublished), getDateString(updated, hasDateUpdated), summary, getSkippableCompletionStatus(), getSkippableAuthor(), getFormattedTags(), 
		FicArchiveBuilder.getRatingString(storyRating)};
	}
	
	// Creates content arrays for chapter pages
	public String[] createChapterContentArray(int chapterNumber) {
		// Fields: infobox, chapter title, chapter body, pagination (top and bottom)
		if (FicArchiveBuilder.isVerbose()) {
			System.out.println("Creating array of chapter content...");
		}
		String chapterPagination = getChapterPagination(chapterNumber);
		// Don't bother showing chapter title for a single-chapter work
		String chapterTitle = "";
		if (FicArchiveBuilder.showChapterNumbers() && chapters.length > 1) {
			chapterTitle = getFormattedChapterTitle(chapterNumber);
		}
		else if (chapters.length != 1) {
			chapterTitle = chapterTitles[chapterNumber];
		}
		// Story infobox, chapter title, story notes, chapter file input, end notes, pagination
		return new String[] {getStoryInfo(), chapterTitle, getFormattedStoryNotes(chapterNumber), "",
		FicArchiveBuilder.readFileToString(chapters[chapterNumber], FicArchiveBuilder.useCasualHTML()), "",
		getFormattedEndNotes(chapterNumber), chapterPagination};
	}
	
	// Gets story notes, but only for the first chapter
	public String getFormattedStoryNotes(int n) {
		if (n == 0) {
			return buildField(FicArchiveBuilder.getSummaryTemplate(), FicArchiveBuilder.getNotesLabel(), storyNotes);
		}
		return "";
	}
	
	// Gets story end notes, but only for the last chapter
	public String getFormattedEndNotes(int n) {
		if (n == chapters.length - 1) {
			return buildField(FicArchiveBuilder.getSummaryTemplate(), FicArchiveBuilder.getEndNotesLabel(), storyEndNotes);
		}
		return "";
	}
	
	// Get the formatted chapter title
	public String getFormattedChapterTitle(int chapterNumber) {
		return FicArchiveBuilder.getChapterTitleTemplate().replace("{{C}}", 
		chapterTitles[chapterNumber]).replace("{{L}}", Integer.toString(chapterNumber + 1));
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
		return FicArchiveBuilder.writeIntoTemplate(FicArchiveBuilder.getChapterPaginationContentTemplate(), new String[] {previous, next});
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
			return field.replace("{{C}}", content).replace("{{L}}", label);
		}
		return content; // if content is blank, don't create a formatted field
	}
	
	// Builds a field using the generic fieldContentTemplate template.
	public String buildField(String label, String content) {
		if (content.equals("")) {
			return "";
		}
		if (label.equals("")) { // for fields with no label string
			return content;
		}
		return FicArchiveBuilder.writeIntoTemplate(FicArchiveBuilder.getFieldContentTemplate(), new String[] {label, content});
	}
	
	// For fields like author, summary, etc that use other templates
	public String buildField(ContentTemplate field, String label, String content) {
		if (content.equals("")) {
			return "";
		}
		else if (label.equals("") && !field.equals(FicArchiveBuilder.getByLineTemplate())) {
			return content;
		}
		return FicArchiveBuilder.writeIntoTemplate(field, new String[] {label, content});
	}

	
	public String getFormattedTags() {
		return buildFormattedArrayField(storyTags, "tags", FicArchiveBuilder.skipTagPages());
	}
	
	// Build a formatted tagset-style string for fields with multiple contents 
	// like authors/fandoms/tags
	public String buildFormattedArrayField(String[] arrayField, String URLCategory, boolean skipCategoryPages) {
		StringBuilder field = new StringBuilder();
		String linkURL = "#";
		for (int i = 0; i < arrayField.length; i++) {
			if (!skipCategoryPages) {
				linkURL = FicArchiveBuilder.getSitePath() + URLCategory + "/" + 
				FicArchiveBuilder.toSafeURL(arrayField[i].toLowerCase()) + 
				FicArchiveBuilder.getPaginationDivider() + "1.html";
			}
			if (i < arrayField.length - 1) {
				field.append(buildField(FicArchiveBuilder.getTagTemplate(), arrayField[i], linkURL));
			}
			else { // since we don't use this much, it's fine to leave as regex for now
				field.append(FicArchiveBuilder.getTagLastTemplate().replace("{{C}}", linkURL).replace("{{L}}", arrayField[arrayField.length - 1]));
			}
		}
		return field.toString();
	}
	
	// Gets the tags array
	public String[] getStoryTags() {
		return storyTags;
	}
	
	// Sets the tags array
	public void setStoryTags(String[] newTags) {
		storyTags = newTags;
	}
	
	// Gets the tagset
	public HashSet<String> getTagSet() {
		return storyTagSet;
	}
	
	// Gets the story title.
	public String getStoryTitle() {
		return storyTitle;
	}
	
	// Sets the story title
	public void setStoryTitle(String newTitle) {
		storyTitle = newTitle;
	}
	
	// Gets the fandoms.
	public String[] getFandoms() {
		return fandoms;
	}
	
	// Sets the fandoms
	public void setFandoms (String[] newFandoms) {
		fandoms = newFandoms;
	}
	
	// Gets the fandom hashset.
	public HashSet<String> getFandomHashSet() {
		return fandomHashSet;
	}
	
	// Gets the authors.
	public String[] getAuthors() {
		return authors;
	}
	
	// Sets the author.
	public void setAuthors(String[] newAuthors) {
		authors = newAuthors;
	}
	
	// Gets the author hashset.
	public HashSet<String> getAuthorHashSet() {
		return authorHashSet;
	}
	
	// Gets the date updated, or failing that, the date published.
	public LocalDate getDateUpdated() {
		return updated;
	}
	
	// Gets the wordcount.
	public int getWordCount() {
		return wordcount;
	}
	
	// Gets chapter count
	public int getChapterCount() {
		return chapters.length;
	}
	
	// Gets the rating enum
	public Rating getRating() {
		return storyRating;
	}
	
	// Basic toString() method.
	public String toString() {
		if (storyTitle.equals("")) {
			return storyOutputFolder.getName();
		}
		return storyTitle;
	}
	
	// Gets either the formatted list of fandoms, or (if none was given and we're 
	// skipping blank fields) a blank string.
	private String getSkippableFandom() {
		if (!hasFandom && FicArchiveBuilder.skipEmptyFields()) {
			return "";
		}
		return buildFormattedArrayField(fandoms, "fandoms", FicArchiveBuilder.skipFandomIndex());
	}
	
	// Same as getSkippableFandom(), but for the author field.
	private String getSkippableAuthor() {
		if (!hasAuthor && FicArchiveBuilder.skipEmptyFields()) {
			return "";
		}
		return buildFormattedArrayField(authors, "authors", FicArchiveBuilder.skipAuthorIndex());
	}
	
	// Same as getSkippableFandom(), but with completion status field.
	private String getSkippableCompletionStatus() {
		if (!hasCompletionStatus && FicArchiveBuilder.skipEmptyFields()) {
			return "";
		}
		// Get completion statuses from FicArchiveBuilder's settings
		return FicArchiveBuilder.getCompletionStatusString(isComplete);
	}
	
	// For printing a warning when a date in storyinfo.txt is invalid.
	public void printInvalidDateWarning(String dateField) {
		System.out.println("Warning: invalid date " + dateField + " for story '" + 
		storyTitle + "' (date input must be in ISO format (YYYY-MM-DD).)");
		System.out.print("Default fallback (" );
		if (FicArchiveBuilder.defaultToEpochDate()) {
			System.out.print("1970-01-01");
		}
		else {
			System.out.print("folder creation and/or last modified dates from metadata");
		}
		System.out.println(") will be used instead.");
	}
}
