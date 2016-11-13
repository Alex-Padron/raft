import java.util.ArrayList;

public class AppendEntryArgs<T> {
	int term;
	int leader_id;
	int prev_log_index;
	int prev_log_term;
	ArrayList<LogEntry<T>> entries;
	int leader_commit;
}
