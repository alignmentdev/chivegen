import java.util.*;

public class SortByFandom extends SortStoryBy {
	// The name of the kind of comparator
	public String toString() {
		return "Fandom";
	}
	// Returns whether or not works with a same or similar quality should be
	// grouped under a shared heading
	public boolean isGroupable() {
		return true;
	}
	// Returns the string of the value used to decide grouping
	public String getComparisonField(Story s) {
		return s.getFandom();
	}
	
	// Gets the header this comparison field is grouped under
	public String getGroupingHeader(Story s) {
		return s.getFandom();
	//	return Character.toString(s.getFandom().charAt(0)).toUpperCase();
	}
	
	public Comparator<Story> getComparator() {
		return new FandomComparator();
	}
}

class FandomComparator implements Comparator<Story> {
	// The basic comparator
	public int compare(Story a, Story b) {
		// Ignore leading "the"s if specified
		if (FicArchiveBuilder.skipThe()) {
			String f1 = FicArchiveBuilder.stripLeadingThe(a.getFandom());
			String f2 = FicArchiveBuilder.stripLeadingThe(b.getFandom());
			return f1.compareTo(f2);
		}
		return a.getFandom().toLowerCase().compareTo(b.getFandom().toLowerCase());
	}
}
