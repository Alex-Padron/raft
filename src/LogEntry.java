
public class LogEntry<T> {
	public final T command;
	public final int term;
	public final int log_index;
		
	public LogEntry(T command, int term, int log_index) {
		this.command = command;
		this.term = term;
		this.log_index = log_index;
	}
}