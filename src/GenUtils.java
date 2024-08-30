/***
  *  General utility functions. May be deleted, since there's only one thing
  *  here and it's potentially deprecated/obsolete.
 ***/

import java.util.*;

public class GenUtils {

    // Accepts a string array and returns a HashSet of those values.
    public static HashSet<String> hashSetFromArray(String[] values) {
        HashSet<String> set = new HashSet<String>();
        for (int i = 0; i < values.length; i++) {
            set.add(values[i]);
        }
        return set;
    }
    
  // Accepts a single String array, and returns a HashMap of the Strings
  // mapped with their original indices in the array as values.
  public static HashMap<String, Integer> hashMapStringToIndex(String[] keys) {
    HashMap<String, Integer> map = new HashMap<String, Integer>();
    for (int i = 0; i < keys.length; i++) {
      map.put(keys[i], i);
    }
    return map;
  }

}
