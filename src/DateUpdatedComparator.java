import java.util.*;
import java.time.*;

public class DateUpdatedComparator implements Comparator<Story> {
  // The basic comparator
  public int compare(Story a, Story b) {
    // More recent works should appear first
    return -(a.getDateUpdated().compareTo(b.getDateUpdated()));
  }
}
