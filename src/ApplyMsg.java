
public class ApplyMsg<T> {
	public final int index;
	public final T command;
	public final boolean use_snapshot;
	public final byte[] snapshot;
	
	public ApplyMsg(int index, T command, boolean use_snapshot, byte[] snapshot) {
		this.index = index;
		this.command = command;
		this.use_snapshot = use_snapshot;
		this.snapshot = snapshot;
	}
}
