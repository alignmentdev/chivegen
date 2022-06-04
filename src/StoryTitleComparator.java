import java.util.*;

public class StoryTitleComparator implements Comparator<Story> {
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
