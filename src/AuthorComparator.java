import java.util.*;

public class AuthorComparator implements Comparator<Story> {
  // The basic comparator
  public int compare(Story a, Story b) {
    return a.getAuthor().toLowerCase().compareTo(b.getAuthor().toLowerCase());
  }
}
