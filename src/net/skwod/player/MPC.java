package net.skwod.player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * This class handles the construction and execution of commands, specific for the Player "mpd" / "mpc" for Linux,
 * which can be used on servers and completely without GUI.
 *
 * @author Maxmanski
 */
public class MPC implements Player {

	private String playerCommand;
	private int playlistSize;

	public MPC(String playerDir) {
		this(playerDir, "mpc");
	}

	public MPC(String playerDir, String playerExec) {
		this.playerCommand = playerDir;
		if(!playerCommand.endsWith(File.separator)){
			playerCommand += File.separator;
		}
		playerCommand += playerExec;
		playerCommand = playerCommand.trim();
		playlistSize = 0;
	}

	@Override
	public void play() throws IOException {
		Runtime.getRuntime().exec(new String[]{playerCommand, "play"});
	}

	@Override
	public void pause() throws IOException {
		Runtime.getRuntime().exec(new String[]{playerCommand, "pause"});
	}

	@Override
	public void stop() throws IOException {
		Runtime.getRuntime().exec(new String[]{playerCommand, "stop"});
		sleep(50);
		this.clear();
	}

	@Override
	public void playSongs(List<String> songs) throws IOException {
		playlistSize = 0;
		songs = new ArrayList<>(songs);
		stop();
		sleep(50);
		addSongs(songs);
		sleep(50);
		play();
	}

	@Override
	public void addSongs(List<String> songs) throws IOException {
		for(String str: songs){
			playlistSize++;
			if(str.startsWith("./")){
				str = str.substring(2);
			}
			Runtime.getRuntime().exec(new String[]{playerCommand, "add", str});
		}
	}

	@Override
	public void next() throws IOException {
		Runtime.getRuntime().exec(new String[]{playerCommand, "next"});

	}

	@Override
	public void previous() throws IOException {
		Runtime.getRuntime().exec(new String[]{playerCommand, "prev"});
	}

	@Override
	public void random() throws IOException {
		if(playlistSize <= 0){
			return;
		}

		int pos = ((int)(Math.random() * playlistSize) + 1);
		Runtime.getRuntime().exec(new String[]{playerCommand, "play", Integer.toString(pos)});
	}

	public void clear() throws IOException {
		playlistSize = 0;
		Runtime.getRuntime().exec(new String[]{playerCommand, "clear"});
	}

	private void sleep(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException ignored) {}
	}
}
