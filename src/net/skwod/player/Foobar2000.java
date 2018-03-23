package net.skwod.player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * This class handles the construction and execution of commands, specific for the Player "Foobar2000" for Windows.
 *
 * @author Maxmanski
 */
public class Foobar2000 implements Player{

	private String playerCommand;
	private String musicDir;
	
	public Foobar2000(String playerDir, String musicDir) {
		this(playerDir, "foobar2000", musicDir);
	}

	public Foobar2000(String playerDir, String playerExec, String musicDir) {
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
		Runtime.getRuntime().exec(new String[]{playerCommand, "/play"});
	}

	@Override
	public void pause() throws IOException {
		Runtime.getRuntime().exec(new String[]{playerCommand, "/pause"});
	}

	@Override
	public void stop() throws IOException {
		Runtime.getRuntime().exec(new String[]{playerCommand, "/exit"});
	}

	@Override
	public void playSongs(List<String> songs) throws IOException {
		songs = new ArrayList<>(songs);
		Runtime.getRuntime().exec(new String[]{playerCommand, musicDir + songs.get(0)});
		songs.remove(0);
		addSongs(songs);
	}

	@Override
	public void addSongs(List<String> songs) throws IOException {
		for(String str: songs){
			Runtime.getRuntime().exec(new String[]{playerCommand, "/add", musicDir + str});
		}
	}

	@Override
	public void next() throws IOException {
		Runtime.getRuntime().exec(new String[]{playerCommand, "/next"});

	}

	@Override
	public void previous() throws IOException {
		Runtime.getRuntime().exec(new String[]{playerCommand, "/prev"});
	}

	@Override
	public void random() throws IOException {
		Runtime.getRuntime().exec(new String[]{playerCommand, "/rand"});
	}

}
