
public class SnapshotArgs {
	public final int term;
	public final int leader_id;
	public final int last_included_index;
	public final int last_included_term;
	public final byte[] snapshot;
	
	public SnapshotArgs(int term,
						int leader_id,
						int last_included_index,
						int last_included_term,
						byte[] snapshot) {
		this.term = term;
		this.leader_id = leader_id;
		this.last_included_index = last_included_index;
		this.last_included_term = last_included_term;
		this.snapshot = snapshot;
	}
}
