import java.util.*;

public class SortStoryBy {
	// The name of the kind of comparator
	public String toString() {
		return "SortStoryBy";
	}
	// Returns whether or not works with a same or similar quality should be
	// grouped under a shared heading
	public boolean isGroupable() {
		return false;
	}
	// Returns the string of the value used for sorting
	public String getComparisonField(Story s) {
		return "Placeholder";
	}
	// Gets the header this comparison field is grouped under
	public String getGroupingHeader(Story s) {
		return "P";
	}	
	// Gets the actual comparator for sorting
	public Comparator<Story> getComparator() {
		return new GenericStoryComparator();
	}
}

// This is really just here to keep Java from freaking out about
// StorySortBy not having a getComparator method that returns a Comparator<Story>
// of some kind. It's not really used anywhere.
class GenericStoryComparator implements Comparator<Story> {
	public int compare(Story a, Story b) {
		return a.getStoryTitle().compareTo(b.getStoryTitle());
	}
}
