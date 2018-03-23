package net.skwod.player.ex;

public class NoSuchPlayerException extends Exception {

	private static final long serialVersionUID = 1L;

	public NoSuchPlayerException() {
		super("No such Player implemented");
	}

	public NoSuchPlayerException(String playerName) {
		super("No such Player implemented: " + playerName);
	}

}
