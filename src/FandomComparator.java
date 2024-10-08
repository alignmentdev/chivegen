import java.util.*;

public class FandomComparator implements Comparator<Story> {
  // The basic comparator
  public int compare(Story a, Story b) {
    // Ignore leading "the"s if specified
    if (FicArchiveBuilder.skipThe()) {
      String f1 = HtmlUtils.stripLeadingThe(a.getFandom());
      String f2 = HtmlUtils.stripLeadingThe(b.getFandom());
      return f1.compareTo(f2);
    }
    return a.getFandom().toLowerCase().compareTo(b.getFandom().toLowerCase());
  }
}
