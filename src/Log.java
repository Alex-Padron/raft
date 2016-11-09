import java.util.ArrayList;

public class Log<T> {
	private class LogEntry {
		public final T command;
		public final int term;
		public final int log_index;
		
		public LogEntry(T command, int term, int log_index) {
			this.command = command;
			this.term = term;
			this.log_index = log_index;
		}
	}
	private final ArrayList<LogEntry> log;
	private int log_start_index;
	private int last_log_term;
	private int last_included_index;
	private int last_included_term;
	
	public Log() {
		this.log = new ArrayList<>();
	}
	
	public void remove_old_log_entries(int new_last_included_index) {
		int previous_last_included_index = this.last_included_index;
		int delete_to = new_last_included_index - previous_last_included_index - 1;
		if (log.size() > delete_to && delete_to != -1) {
			int potential_term = get_term_at_index(new_last_included_index);
			if (potential_term == -1) return;
		}
	}
	
	// returns -1 if we are no longer storing the entry for this term
	private int get_term_at_index(int index) {
		if (index == last_included_index) {
			return last_included_term;
		} else if (index > last_included_index && index <= real_log_length()) {
			return log.get(index - last_included_index - 1).term;
		} else {
			return -1;
		}
	}
	
	private int real_log_length() {
		return log.size() + last_included_index;
	}
}
