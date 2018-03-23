package net.skwod.player;

import java.io.IOException;
import java.util.List;

/**
 * This interface defines the operations that should be supported by any Player.
 * If an operation is not supported, an UnsupportedOperationException should be thrown.
 *
 * @author Maxmanski
 */
public interface Player {

	/**
	 * Resumes the play-back of the currently loaded playlist or restarts play-back.
	 *
	 * @throws IOException
	 */
	public void play() throws IOException;

	/**
	 * Pauses the play-back of the currently loaded playlist.
	 * Resumption from the same position should be possible after this operation.
	 *
	 * @throws IOException
	 */
	public void pause() throws IOException;

	/**
	 * Stops the play-back of the currently loaded playlist.
	 * May discard position information in the playlist.
	 *
	 * @throws IOException
	 */
	public void stop() throws IOException;

	/**
	 * Creates a new playlist with the specified songs and starts its play-back.
	 *
	 * @param songs the list of Songs to create a new playlist with
	 * @throws IOException
	 */
	public void playSongs(List<String> songs) throws IOException;

	/**
	 * Adds the specified songs to the currently loaded playlist.
	 * Does not alter play-back state of the playlist.
	 *
	 * @param songs the list of Songs to add to the playlist.
	 * @throws IOException
	 */
	public void addSongs(List<String> songs) throws IOException;

	/**
	 * Stops the play-back of the current song and starts play-back of the
	 * next song in the playlist.
	 *
	 * @throws IOException
	 */
	public void next() throws IOException;

	/**
	 * Stops the play-back of the current song and starts play-back of the
	 * previous song in the playlist.
	 *
	 * @throws IOException
	 */
	public void previous() throws IOException;

	/**
	 * Stops the play-back of the current song and starts play-back of a
	 * random song in the playlist.
	 *
	 * @throws IOException
	 */
	public void random() throws IOException;
}
