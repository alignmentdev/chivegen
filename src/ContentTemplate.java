import java.util.*;

/***

A class for string/page templates.

***/

public class ContentTemplate {
	// Strings that make up the template
	private ArrayList<String> templateStrings;
	// Order in which content should be inserted
	private int[] insertionPoints;
	
	public ContentTemplate(ArrayList<String> strings, int[] inserts) {
		templateStrings = strings;
		insertionPoints = inserts;
	}
	
	public ArrayList<String> getTemplateStrings() {
		return templateStrings;
	}
	
	public int[] getInsertionPoints() {
		return insertionPoints;
	}
}
