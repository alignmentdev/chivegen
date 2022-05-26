import java.util.*;
import java.time.*;

public class SortByDateUpdated extends SortStoryBy {
	// The name of the kind of comparator
	public String toString() {
		return "Date Updated";
	}
	// Returns whether or not works with a same or similar quality should be
	// grouped under a shared heading
	public boolean isGroupable() {
		return false;
	}
	// Returns the string of the value used to decide grouping
	public String getComparisonField(Story s) {
		return s.getDateUpdated().toString();
	}
	// Gets the header this comparison field is grouped under
	public String getGroupingHeader(Story s) {
		return s.getDateUpdated().toString().substring(0,3);
	}
	// Returns the actual comparator
	public Comparator<Story> getComparator() {
		return new DateUpdatedComparator();
	}
}

class DateUpdatedComparator implements Comparator<Story> {
	// The basic comparator
	public int compare(Story a, Story b) {
		// More recent works should appear first
		return -(a.getDateUpdated().compareTo(b.getDateUpdated()));
	}
}
