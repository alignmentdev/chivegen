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
	// End notes. Displayed after last chapter. Currently unused.
	private String storyEndNotes = "";
	// The published and last updated dates. Input file should use ISO format.
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
	// In case fields in storyinfo.txt are missing, incorrectly formatted 
	// or otherwise can't be used
	private boolean hasDateUpdated = false;
	private boolean hasDatePublished = false;
	private boolean hasFandom = false;
	private boolean hasCompletionStatus = false;
	private boolean hasAuthor = false;
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
		if (chapters.length < 1) {
			System.out.println("Warning: no chapter files found in folder " + inputFolder.getPath());
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
								hasFandom = true;
							}
							else if (currentLineData[0].equals("author") || currentLineData[0].equals("creator")) {
								author = currentLineData[1];
								FicArchiveBuilder.setHasAuthors(true);
								hasAuthor = true;
							}
							else if (currentLineData[0].equals("summary")) {
								summary = currentLineData[1];
							}
							else if (currentLineData[0].equals("notes")) {
								storyNotes = currentLineData[1];
							}
							else if (currentLineData[0].equals("end notes")) {
								storyEndNotes = currentLineData[1];
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
							else if (currentLineData[0].equals("rating") || currentLineData[0].equals("rated")) {
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
								else if (r.equals("e") || r.equals("x") || r.equals("nc-17") || r.equals("explicit")) {
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
								hasCompletionStatus = true;
							}
							else if (currentLineData[0].equals("updated") || currentLineData.equals("date updated")) {
								try {
									updated = LocalDate.parse(currentLineData[1].replaceAll("\\s", "")); //strip whitespace just in case
									hasDateUpdated = true;
								} catch (DateTimeParseException e) {
									printInvalidDateWarning("updated");
									hasDateUpdated = false;
								}
							}
							else if (currentLineData[0].equals("published") || currentLineData[0].equals("posted") ||
							currentLineData[0].equals("date published") || currentLineData[0].equals("date posted")) {
								try {
									published = LocalDate.parse(currentLineData[1].replaceAll("\\s", ""));
									hasDatePublished = true;
								} catch (DateTimeParseException e) {
									printInvalidDateWarning("published");
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
		// Placholder is used for sorting purposes
		if (!hasFandom) {
			if (FicArchiveBuilder.isVerbose()) {
				System.out.println("Autofilling fandom...");
			}
			fandom = "No Fandom Given";
		}
		if (!hasAuthor) {
			if (FicArchiveBuilder.isVerbose()) {
				System.out.println("Autofilling author...");
			}
			author = "Unknown Author";
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
				// use metadata from input folder
				setDatesFromMetadata(inputFolder);
			}
		}
		// If no valid wordcount is supplied in file, get it manually
		if (wordcount == -1) {
			wordcount = countWords();
		}
		// Generate the story infobox.
		if (chapters.length > 0) {
			storyInfo = buildStoryInfoBox();
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
	// doesn't seem to work currently. Might be a Linux issue?
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
			pageTitle = "Chapter " + (chapterNumber+1) + ": " + chapterTitles[chapterNumber];
		}
		// Get the full string for the output webpage
		return FicArchiveBuilder.buildStandardPageString(chapterString, 
		FicArchiveBuilder.buildPageTitle(pageTitle, storyTitle));
	}
		
	// Gets the prebuilt story info box, complete with link.
	public String getStoryInfo() {
		return storyInfo;
	}	
	
	// Builds a string for the story infobox.
	public String buildStoryInfoBox() {
		// Create infobox from template
		return FicArchiveBuilder.writeIntoTemplate(FicArchiveBuilder.getInfoBoxTemplate(), createInfoBoxContentArray());
	}
	
	// Gets the local URL of any arbitrary chapter.
	public String getChapterURL(int chapterNumber) {
		return chapters[chapterNumber].getName().replace(".txt", ".html");
	}
	
	// Creates HashMap of fields and content for a story infobox.
	public String[] createInfoBoxContentArray() {
		// Get the URL for linking to this story
		String url = (FicArchiveBuilder.getSitePath() + "stories/" + storyOutputFolder.getName() + "/" + chapters[0].getName().replace(".txt", ".html"));
		String titleLink = "<a href=\"" + url + "\">" + storyTitle + "</a>";
		// Put Yes/No for Completion Status based on isComplete
		String completionStatus = "No";
		if (isComplete) {
			completionStatus = "Yes";
		}
		// Fields: title (link), fandom, wordcount, chapter #, published, updated, summary, completion status, author, tags, rating
		//String[] storyPageData;
		if (FicArchiveBuilder.generateInfoBoxTemplateFields()) {
			String field = FicArchiveBuilder.getFieldTemplate(); // since this will be reused a lot
			return new String[] {titleLink, buildField(field, FicArchiveBuilder.getFandomLabel(), getSkippableFandom()), 
			buildField(field, FicArchiveBuilder.getWordcountLabel(), FicArchiveBuilder.numberWithCommas(wordcount)), 
			buildField(field, FicArchiveBuilder.getChapterCountLabel(), Integer.toString(chapters.length)), 
			buildField(field, FicArchiveBuilder.getDatePublishedLabel(), getDateString(published, hasDatePublished)), 
			buildField(field, FicArchiveBuilder.getDateUpdatedLabel(), getDateString(updated, hasDateUpdated)), 
			buildField(FicArchiveBuilder.getSummaryTemplate(), FicArchiveBuilder.getSummaryLabel(), summary), 
			buildField(field, FicArchiveBuilder.getCompletionLabel(), getSkippableCompletionStatus()), 
			buildField(FicArchiveBuilder.getByLine(), FicArchiveBuilder.getAuthorLabel(), getSkippableAuthor()),
			buildField(field, FicArchiveBuilder.getTagsLabel(), getFormattedTags()),
			buildField(field, FicArchiveBuilder.getRatingLabel(), FicArchiveBuilder.getRatingString(storyRating))};
		}
		return new String[] {titleLink, getSkippableFandom(), Integer.toString(wordcount), Integer.toString(chapters.length),
		getDateString(published, hasDatePublished), getDateString(updated, hasDateUpdated), summary, getSkippableCompletionStatus(), getSkippableAuthor(), getFormattedTags(), 
		FicArchiveBuilder.getRatingString(storyRating)};
	}
	
	// Creates chapter content arrays
	public String[] createChapterContentArray(int chapterNumber) {
		// Fields: infobox, chapter title, chapter body, pagination (top and bottom)
		if (FicArchiveBuilder.isVerbose()) {
			System.out.println("Creating array of chapter content...");
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
		// Story infobox, chapter title, story notes, chapter file input, end notes, pagination x2
		return new String[] {getStoryInfo(), chapterTitle, getFormattedStoryNotes(chapterNumber), 
		FicArchiveBuilder.readFileToString(chapters[chapterNumber], FicArchiveBuilder.useCasualHTML()), 
		getFormattedEndNotes(chapterNumber), chapterPagination, chapterPagination};
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
		return FicArchiveBuilder.getChapterPaginationTemplate().replace("{Next}", next).replace("{Previous}", previous);
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
					tagURL = FicArchiveBuilder.getSitePath() + "tags/" + FicArchiveBuilder.toSafeURL(storyTags[i].toLowerCase()) + "/1.html";
				}
				// Create the formatted tag and link
				if (i == (storyTags.length - 1)) { // last tag has special class for CSS usage
					tagList.append(FicArchiveBuilder.getTagLastTemplate().replace("{C}", "<a href=\"" + tagURL + "\">" + storyTags[i] + "</a>"));
				}
				else {
					tagList.append(FicArchiveBuilder.getTagTemplate().replace("{C}", "<a href=\"" + tagURL + "\">" + storyTags[i] + "</a>"));
				}
			}
		}
		return tagList.toString();
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
		return updated;
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
	
	// Gets either the fandom, or (if none was given and we're skipping blank
	// fields) a blank string.
	private String getSkippableFandom() {
		if (!hasFandom && FicArchiveBuilder.skipEmptyFields()) {
			return "";
		}
		if (FicArchiveBuilder.skipFandomIndex()) {
			return fandom;
		}
		return "<a href=\"" + FicArchiveBuilder.getSitePath() + "fandoms/" + FicArchiveBuilder.toSafeURL(fandom) + "/1.html\">" + fandom + "</a>";
	}
	
	// Same as getSkippableFandom(), but for the author field.
	private String getSkippableAuthor() {
		if (!hasAuthor && FicArchiveBuilder.skipEmptyFields()) {
			return "";
		}
		if (FicArchiveBuilder.skipAuthorIndex()) {
			return author;
		}
		return "<a href=\"" + FicArchiveBuilder.getSitePath() + "authors/" + FicArchiveBuilder.toSafeURL(author) + "/1.html\">" + author + "</a>";
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
