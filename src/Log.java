import java.util.ArrayList;
import java.util.List;

public class Log<T> {
	public List<LogEntry<T>> log;
	private int log_start_index;
	private int last_log_term;
	public int last_included_index;
	public int last_included_term;
	
	public Log() {
		this.log = new ArrayList<>();
	}
	
	// returns -1 if we are no longer storing the entry for this term
	public int get_term_at_index(int index) {
		if (index < 1) return 0;
		if (index == last_included_index) {
			return last_included_term;
		} else if (index > last_included_index && index <= real_log_length()) {
			return log.get(index - last_included_index - 1).term;
		} else {
			return -1;
		}
	}
	
	public LogEntry<T> get(int index) {
		System.out.println("CHECKING INDEX " + index + " total length " + real_log_length());
		return log.get(index - 1);
	}
	
	public void wipe() {
		this.log = new ArrayList<LogEntry<T>>();
	}
	
	public LogEntry<T> get_entry_at(int index) {
		return log.get(index - last_included_index - 1);
	}
	
	public int real_log_length() {
		return log.size() + last_included_index;
	}
	
	public int last_log_entry_term() {
		if (log.size() == 0) return last_included_term;
		return log.get(log.size() - 1).term;
	}
	
	@Override
	public String toString() {
		String s = "";
		s += "length: " + log.size();
		s += "\nstart index: " + log_start_index;
		s += "\nlast log term: " + last_log_term;
		s += "\nlast included index: " + last_included_index;
		s += "\nlast included term: " + last_included_term;
		return s;
	}
}
