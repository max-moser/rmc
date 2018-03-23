package net.skwod.player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * This class handles the construction and execution of commands, specific for the Player "totem" for Linux.
 *
 * @author Maxmanski
 */
public class Totem implements Player {

	private String playerCommand;
	private String musicDir;

	public Totem(String playerDir, String musicDir) {
		this(playerDir, "totem", musicDir);
	}

	public Totem(String playerDir, String playerExec, String musicDir) {
		this.playerCommand = playerDir;
		if(!playerCommand.endsWith(File.separator)){
			playerCommand += File.separator;
		}
		playerCommand += playerExec;
		playerCommand = playerCommand.trim();
		this.musicDir = musicDir;
	}

	@Override
	public void play() throws IOException {
		Runtime.getRuntime().exec(new String[]{playerCommand, "--play"});

	}

	@Override
	public void pause() throws IOException {
		Runtime.getRuntime().exec(new String[]{playerCommand, "--pause"});

	}

	@Override
	public void stop() throws IOException {
		Runtime.getRuntime().exec(new String[]{playerCommand, "--quit"});
	}

	@Override
	public void playSongs(List<String> songs) throws IOException {
		songs = new ArrayList<>(songs);
		try {
			Thread.sleep(100);
		} catch (InterruptedException e) {}
		Runtime.getRuntime().exec(new String[]{playerCommand, musicDir + songs.get(0)});
		songs.remove(0);
		try {
			Thread.sleep(100);
		} catch (InterruptedException e) {}
		addSongs(songs);
	}

	@Override
	public void addSongs(List<String> songs) throws IOException {
		for(String str: songs){
			Runtime.getRuntime().exec(new String[]{playerCommand, "--enqueue", musicDir + str});
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {}
		}
	}

	@Override
	public void next() throws IOException {
		Runtime.getRuntime().exec(new String[]{playerCommand, "--next"});

	}

	@Override
	public void previous() throws IOException {
		Runtime.getRuntime().exec(new String[]{playerCommand, "--previous"});
	}

	@Override
	public void random() throws IOException {
		throw new UnsupportedOperationException("random");
//		Runtime.getRuntime().exec(new String[]{playerCommand});
	}

}
