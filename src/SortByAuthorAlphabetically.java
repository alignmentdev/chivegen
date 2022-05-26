import java.util.*;

public class SortByAuthorAlphabetically extends SortStoryBy {
	// The name of the kind of comparator
	public String toString() {
		return "Author (A-Z)";
	}
	// Returns whether or not works with a same or similar quality should be
	// grouped under a shared heading
	public boolean isGroupable() {
		return true;
	}
	// Returns the string of the value used to decide grouping
	public String getComparisonField(Story s) {
		return s.getAuthor();
	}
	
	// Gets the header this comparison field is grouped under
	public String getGroupingHeader(Story s) {
	//	return Character.toString(s.getAuthor().charAt(0)).toUpperCase();
		return s.getAuthor();
	}
	
	// Returns the actual comparator
	public Comparator<Story> getComparator() {
		return new AuthorComparator();
	}
}

class AuthorComparator implements Comparator<Story> {
	// The basic comparator
	public int compare(Story a, Story b) {
		return a.getAuthor().toLowerCase().compareTo(b.getAuthor().toLowerCase());
	}
}
