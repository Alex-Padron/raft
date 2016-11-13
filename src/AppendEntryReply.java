import java.util.ArrayList;

public class AppendEntryReply<T> {
	public int term;
	public boolean success;
	public int conflicting_entry_term;
	public int conflicting_term_first_index;
	public int prev_log_index;
	public ArrayList<LogEntry<T>> entries;
	
	public AppendEntryReply() {
		term = -1;
		success = false;
		conflicting_entry_term = -1;
		conflicting_term_first_index = -1;
		prev_log_index = -1;
		entries = new ArrayList<>();
	}
	/*
	public AppendEntryReply(int term,
							boolean success,
							int conflicting_entry_term,
							int conflicting_term_first_index,
							int prev_log_index) {
		this.term = term;
		this.success = success;
		this.conflicting_entry_term = conflicting_entry_term;
		this.conflicting_term_first_index = conflicting_term_first_index;
		this.prev_log_index = prev_log_index;
	}
	*/	
}
