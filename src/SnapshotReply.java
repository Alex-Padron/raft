
public class SnapshotReply {
	public final int term;
	public final boolean success;
	
	public SnapshotReply(int term, boolean success) {
		this.term = term;
		this.success = success;
	}
}
