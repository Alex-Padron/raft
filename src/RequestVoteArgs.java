
public class RequestVoteArgs {
	public final int term;
	public final int candidate_id;
	public final int last_log_index;
	public final int last_log_entry_term;
	public final int election_number;
	
	public RequestVoteArgs(int term, 
						int candidate_id,
						int last_log_index,
						int last_log_entry_term,
						int election_number) 
	{
		this.term = term;
		this.candidate_id = candidate_id;
		this.last_log_index = last_log_index;
		this.last_log_entry_term = last_log_entry_term;
		this.election_number = election_number;
	}
}
