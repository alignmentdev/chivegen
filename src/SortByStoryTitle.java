import java.util.*;

public class SortByStoryTitle extends SortStoryBy {
	// The name of the kind of comparator
	public String toString() {
		return "Title";
	}
	// Returns whether or not works with a same or similar quality should be
	// grouped under a shared heading
	public boolean isGroupable() {
		return true;
	}
	// Returns the string of the value used to decide grouping
	public String getComparisonField(Story s) {
		return s.getStoryTitle();
	}
	// Returns the heading this comparison field is grouped under
	public String getGroupingHeader(Story s) {
		if (FicArchiveBuilder.skipThe()) {
			return Character.toString(FicArchiveBuilder.stripLeadingThe(s.getStoryTitle()).charAt(0)).toUpperCase();
		}
		return Character.toString(s.getStoryTitle().charAt(0)).toUpperCase();
	}
	// Returns the actual comparator
	public Comparator<Story> getComparator() {
		return new StoryTitleComparator();
	}
}

class StoryTitleComparator implements Comparator<Story> {
	// The basic comparator
	public int compare(Story a, Story b) {
		// Ignore leading "the"s if specified
		if (FicArchiveBuilder.skipThe()) {
			String title1 = FicArchiveBuilder.stripLeadingThe(a.getStoryTitle());
			String title2 = FicArchiveBuilder.stripLeadingThe(b.getStoryTitle());
			return title1.compareTo(title2);
		}
		return a.getStoryTitle().toLowerCase().compareTo(b.getStoryTitle().toLowerCase());
	}
}
