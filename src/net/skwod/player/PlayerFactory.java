package net.skwod.player;

import net.skwod.player.ex.NoSuchPlayerException;

/**
 * A factory for creating instances of Players from the configured playerExecutable's name.
 *
 * @author Maxmanski
 */
public class PlayerFactory {

	/**
	 * Creates an instance of a Player, depending on the specified playerExec (name of the executable) string.
	 * The music directory will be passed on to the player, as it might be needed for finding the audio files.
	 *
	 * @param playerDir the directory in which the executable can be found
	 * @param playerExec the name of the executable
	 * @param musicDir the music directory to pass on to the player
	 * @return an instance of a Player, as decided from the playerExec String
	 * @throws NoSuchPlayerException when no Player can be determined from the playerExec String
	 */
	public static Player getPlayer(String playerDir, String playerExec, String musicDir) throws NoSuchPlayerException{
		String lowerPlayerExec = playerExec.toLowerCase();
		if(lowerPlayerExec.contains("foobar2000")){
			return new Foobar2000(playerDir, playerExec, musicDir);
		}else if(lowerPlayerExec.contains("totem")){
			return new Totem(playerDir, playerExec, musicDir);
		}else if(lowerPlayerExec.contains("mpc")){
			return new MPC(playerDir, playerExec);
		}else{
			throw new NoSuchPlayerException(playerExec);
		}
	}
}
