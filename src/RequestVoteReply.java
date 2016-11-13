
public class RequestVoteReply {
	public final int term;
	public final boolean vote_granted;
	public final int voter_id;
	public final int candidate_id;
	public final int candidate_term;
	public final int election_number;
	
	public RequestVoteReply(int term,
							boolean vote_granted,
							int voter_id,
							int candidate_id,
							int candidate_term,
							int election_number) 
	{
		this.term = term;
		this.vote_granted = vote_granted;
		this.voter_id = voter_id;
		this.candidate_id = candidate_id;
		this.candidate_term = candidate_term;
		this.election_number = election_number;
	}
}
