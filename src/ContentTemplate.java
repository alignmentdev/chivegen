import java.util.*;

/***

A class for string/page templates.

***/

public class ContentTemplate {
	// Strings that make up the template
	private String[] templateStrings;
	// Order in which content should be inserted
	private int[] insertionPoints;
	
	// Constructor	
	public ContentTemplate(ArrayList<String> strings, ArrayList<Integer> inserts) {
		templateStrings = new String[strings.size()];
		templateStrings = strings.toArray(templateStrings);
		insertionPoints = new int[inserts.size()];
		// Since we can't convert directly from ArrayList<Integer> to int[], we
		// have to do this manually.
		for (int i = 0; i < insertionPoints.length; i++) {
			insertionPoints[i] = inserts.get(i);
		}
	}
	
	public String[] getTemplateStrings() {
		return templateStrings;
	}
		
	public int[] getInsertionPoints() {
		return insertionPoints;
	}
}
