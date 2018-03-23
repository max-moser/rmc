package net.skwod;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

import net.skwod.player.Player;
import net.skwod.player.PlayerFactory;
import net.skwod.player.ex.NoSuchPlayerException;

/**
 * Remote Music Control:
 * The entry point for the server application.
 * The entire application logic short of construction of creation and execution of player-specific commands
 * is contained here.
 * This is:
 * - setting up the program
 * - handling of connections
 * - deciding what to do with incoming messages (commands)
 * - handling of the playlist
 *
 * @author Maxmanski
 */
public class RMC {

	private static String musicDir = getDefaultMusicDir();
	private static String playerDir = null;
	private static String playerExec = null;
	private static int port = 2000;
	private static int timeout = 90;
	private static int maxSessionLength = 300;
	private static Player player;
	private static Timer timeoutTimer = null;
	private static Timer sessionTimer = null;
	private static DateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");
	private static boolean connected = false;
	private static ServerSocket serverSocket = null;
	private static Socket client = null;
	private static Object waitLock = new Object();
	private static Object connectedAccessLock = new Object();

	public static void main(String[] args){

		System.out.println("Running jRMC...");

		setupProperties();
		checkProperties();

		boolean running = true;
		long commandNo = 0;
		List<String> playlist = new LinkedList<>();

		try {
			player = PlayerFactory.getPlayer(playerDir, playerExec, musicDir);
		} catch (NoSuchPlayerException e) {
			System.err.println("Could not instantiate Player: " + e.getMessage());
		}

		try {
			serverSocket = new ServerSocket(port);
			Thread acceptThread = new Thread(new RejectConnectionTask(serverSocket));
			acceptThread.setDaemon(true);
			acceptThread.start();

			while(running){

				while(!connected){
					synchronized (waitLock) {
						try {
							waitLock.wait();
						} catch (Exception e) {}
					}
				}

				BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), Charset.forName("UTF-8")));
				PrintWriter writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(client.getOutputStream(), Charset.forName("UTF-8"))));

				String dateTime = sdf.format(Calendar.getInstance().getTime());
				InetAddress connAddr = client.getInetAddress();
				System.out.println(dateTime + " - connected: " + connAddr.getHostAddress() + " (" + connAddr.getHostName() + ")");
				commandNo = 0;
				String line = null;

				startTimeoutTimer(new TimeoutTask(client, "ACK: TIMEOUT"));
				startSessionTimer(new TimeoutTask(client, "ACK: SESSION EXPIRED"));
				writer.println("ACK: jRMC");
				writer.flush();

				try{
					while((line = reader.readLine()) != null){

						startTimeoutTimer(new TimeoutTask(client, "ACK: TIMEOUT"));

						line = line.trim();
						if(line.isEmpty()){
							continue;
						}
						String lowerLine = line.toLowerCase();
						String params = "";
						if(line.indexOf(" ") >= 0){
							params = line.substring(line.indexOf(" "));
						}
						params = params.trim();
						String[] splitParams = params.split(";|:");
						String quote = "\"";

						System.out.println("#" + String.format("%04d", (commandNo++)) + ": " + line);

						if(lowerLine.equals("playlist")){
							for(String str: playlist){
								String tmp = unquote(str);
								writer.println(tmp);
							}
							writer.println("ACK: PLAYLIST");
							writer.flush();

						}else if(lowerLine.equals("play")){

							try {
								player.play();
								writer.println("ACK: PLAY");
							} catch (UnsupportedOperationException e) {
								writer.println("NACK (Unsupported Operation): PLAY");
							}
							writer.flush();

						}else if(lowerLine.startsWith("play ") || lowerLine.startsWith("add ")){

							List<String> problems = new LinkedList<>();
							List<String> usedParams = new LinkedList<>();
							String ackParams = "";

							for(String param: splitParams){

								if(containsDirUp(param)){
									problems.add(param);

								}else{
									param = unquote(param.trim());
									String[] pathSplit = param.split("/|\\\\");
									String constructedPath = musicDir;

									String[] pathParts = new String[pathSplit.length - 1];
									for(int i=0; i<pathParts.length; i++){
										pathParts[i] = pathSplit[i];
									}
									constructedPath = getUniquePath(musicDir, pathParts);

									if((constructedPath == null) || (!new File(constructedPath).exists())){
										problems.add(param);

									}else{

										String fileName = pathSplit[pathSplit.length - 1];
										String path = getUniqueFile(constructedPath, fileName);

										if(path == null){
											problems.add(param);
										}else{
											param = path;
										}
									}
								}

								if(!problems.contains(param)){
									param = (param.startsWith(musicDir)) ? param.substring(musicDir.length()) : param;
									usedParams.add(param);

									String tmp = param.replaceAll("\\\\", "/");
									ackParams += quote + tmp + quote + " ";
								}
							}

							ackParams = ackParams.trim();

							// execute if there were no problems
							// tell the problem otherwise
							if(problems.isEmpty() && !usedParams.isEmpty()){

								String replyString = null;

								if(lowerLine.startsWith("play")){

									try {
										player.playSongs(usedParams);
										replyString = "ACK: PLAY " + ackParams;
									} catch (UnsupportedOperationException e) {
										replyString = "NACK (Unsupported Operation): PLAY";
									}
									playlist.clear();

								}else{

									try {
										player.addSongs(usedParams);
										replyString = "ACK: ADD " + ackParams;
									} catch (Exception e) {
										replyString = "NACK (Unsupported Operation): ADD";
									}
								}

								// add the songs to the playlist list
								for(String str: usedParams){
									String tmp = unquote(str.trim());
									if(tmp.startsWith(musicDir)){
										tmp = tmp.substring(musicDir.length());
									}
									if((tmp.startsWith("/") || tmp.startsWith("\\")) && (tmp.length() >= 1)){
										tmp = tmp.substring(1);
									}
									playlist.add(tmp);
								}

								writer.println(replyString);

							}else if(problems.isEmpty()){
								writer.println("NACK (No songs found): " + line);

							}else{
								String problemString = "";
								for(int i=0; i<problems.size(); i++){
									problemString += problems.get(i);
									if(i < (problems.size() - 1)){
										problemString += "; ";
									}
								}
								writer.println("NACK (" + problemString + "): " + line);
							}

							writer.flush();

						}else if(lowerLine.equals("pause")){

							try {
								player.pause();
								writer.println("ACK: PAUSE");
							} catch (UnsupportedOperationException e) {
								writer.println("NACK (Unsupported Operation): PAUSE");
							}
							writer.flush();

						}else if(lowerLine.equals("list") || lowerLine.startsWith("list ")){
							params = unquote(params);

							String[] pathParts = params.split("/|\\\\");
							String path = getUniquePath(musicDir, pathParts);

							if(!containsDirUp(params) && (path != null)){
								File dir = new File(path);
								File[] files = dir.listFiles();

								Arrays.sort(files, new Comparator<File>() {

									@Override
									public int compare(File o1, File o2) {
										return o1.getName().toLowerCase().compareTo(o2.getName().toLowerCase());
									}
								});

								// print directories first, with "/" at the end
								for(File f: files){
									if(f.isDirectory()){
										writer.println(f.getName() + "/");
									}
								}

								// print music files
								for(File f: files){
									if(f.isFile()){
										if(f.getName().toLowerCase().endsWith("mp3") ||
												f.getName().toLowerCase().endsWith("wav")){

											writer.println(f.getName());
										}
									}
								}
								String subPath = path;
								if(path.startsWith(musicDir)){
									subPath = subPath.substring(musicDir.length());
								}
								subPath = subPath.replaceAll("\\\\", "/");
								if(subPath.startsWith("/")){
									subPath = subPath.substring(1);
								}
								writer.println("ACK: LIST " + subPath);

							}else if(!containsDirUp(params) && (path == null)){
								writer.println("NACK (Path does not exist): " + line);

							}else{
								writer.println("NACK (Path must not contain \"..\"): " + line);
							}

							writer.flush();

						}else if(lowerLine.equals("next")){

							try {
								player.next();
								writer.println("ACK: NEXT");
							} catch (UnsupportedOperationException e) {
								writer.println("NACK (Unsupported Operation): NEXT");
							}
							writer.flush();

						}else if(lowerLine.equals("prev") || lowerLine.equals("previous")){

							try {
								player.previous();
								writer.println("ACK: PREV");
							} catch (UnsupportedOperationException e) {
								writer.println("NACK (Unsupported Operation): PREV");
							}
							writer.flush();

						}else if(lowerLine.equals("rand") || lowerLine.equals("random")){

							try {
								player.random();
								writer.println("ACK: RAND");
							} catch (UnsupportedOperationException e) {
								writer.println("NACK (Unsupported Operation): RAND");
							}
							writer.flush();

						}else if(lowerLine.equals("stop")){

							try {
								player.stop();
								playlist.clear();
								writer.println("ACK: STOP");
							} catch (UnsupportedOperationException e) {
								writer.println("NACK (Unsupported Operation): STOP");
							}
							writer.flush();

						}else if(lowerLine.equals("exit")){
							writer.println("ACK: EXIT");
							writer.flush();

							close(writer);
							close(reader);
							close(client);
							writer = null;
							reader = null;
							client = null;
							setConnected(false);

							break;

						}else if(lowerLine.equals("help")){

							writer.println("PLAY song1;song2;...");
							writer.println("\tCreate a new playlist with the specified songs and start playing");
							writer.println("ADD song1;song2;...");
							writer.println("\tAdds the specified songs to the current playlist");
							writer.println("PAUSE");
							writer.println("\tPause playback");
							writer.println("PLAY");
							writer.println("\tResume playback or re-start playback");
							writer.println("NEXT");
							writer.println("\tSkips to the next song");
							writer.println("PREV");
							writer.println("\tSkips to the previous song");
							writer.println("PREVIOUS");
							writer.println("\tSame as PREV");
							writer.println("RAND");
							writer.println("\tPlays a random song from the current playlist");
							writer.println("RANDOM");
							writer.println("\tSame as RAND");
							writer.println("STOP");
							writer.println("\tStops playback and deletes current playlist");
							writer.println("LIST directory");
							writer.println("\tLists the contents of the directory - folders first");
							writer.println("PLAYLIST");
							writer.println("\tLists all songs from the current playlist");
							writer.println("ACK: HELP");
							writer.flush();

						}else{
							writer.println("NACK (Command not recognised): " + line);
							writer.flush();
						}
					}

				}catch(IOException ex){

				}finally{
					close(reader);
					close(writer);
					close(client);
					reader = null;
					writer = null;
					client = null;
					setConnected(false);

					stopTimeoutTimer();
					stopSessionTimer();
					String dateString = sdf.format(Calendar.getInstance().getTime());
					System.out.println(dateString + " - connection terminated");
				}
			}

		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e){
			e.printStackTrace();
		}finally{
			close(client);
			close(serverSocket);
			client = null;
			serverSocket = null;
		}
	}

	/**
	 * Returns the value of the boolean variable "connected" in a synchronized fashion
	 *
	 * @return the value of the boolean variable "connected"
	 */
	private static boolean isConnected(){
		boolean ret = false;
		synchronized (connectedAccessLock) {
			ret = connected;
		}
		return ret;
	}

	/**
	 * Sets the boolean variable "connected" in a synchronized fashion.
	 *
	 * @param b the value to which the boolean variable "connected" should be set
	 */
	private static void setConnected(boolean b){
		synchronized (connectedAccessLock) {
			connected = b;
		}
	}

	/**
	 * Cancels the timeout timer and sets it to NULL.
	 */
	private static void stopTimeoutTimer(){
		// could be a NullPointer or whatever... we don't actually care
		try {
			timeoutTimer.cancel();
		} catch (Exception e) {}
		timeoutTimer = null;
	}

	/**
	 * (Re-)Starts the timeout timer with the specified task.
	 *
	 * @param task
	 */
	private static void startTimeoutTimer(TimerTask task){
		stopTimeoutTimer();
		if(timeout > 0){
			timeoutTimer = new Timer(true);
			timeoutTimer.schedule(task, (timeout * 1000));
		}
	}

	/**
	 * Cancels the session timer and sets it to NULL.
	 */
	private static void stopSessionTimer(){
		// could be a NullPointer or whatever... we don't actually care
		try {
			sessionTimer.cancel();
		} catch (Exception e) {}
		sessionTimer = null;
	}

	/**
	 * (Re-)Starts the session timer with the specified task.
	 *
	 * @param task
	 */
	private static void startSessionTimer(TimerTask task){
		stopSessionTimer();
		if(maxSessionLength > 0){
			sessionTimer = new Timer(true);
			sessionTimer.schedule(task, (maxSessionLength * 1000));
		}
	}

	/**
	 * Closes closeable if possible without any error handling.
	 *
	 * @param closeable the Closeable to close
	 */
	private static void close(Closeable closeable){
		try {
			if(closeable != null){
				closeable.close();
			}
		} catch (Exception e) {}
	}

	/**
	 * Tries to find a path, based off the base path which contains the path parts.
	 * Each path part does not have to (but can) match a subfolder's name exactly, but has to identify a subfolder uniquely.
	 * If a subfolder cannot be identified uniquely, NULL will be returned.
	 *
	 * Example: For the following folder structure...
	 * basePath/folder 1/subfolder/subsubfolder
	 * basePath/folder 1/subfolder 1/
	 * basePath/folder 2/subfolder/
	 * basePath/forgotten folder/subfolder
	 *
	 * getUniquePath(basePath, {"fo", "subfolder"}) would return NULL - "fo" is not a unique substring in the names
	 * of the subfolders of basePath - "folder 1", "folder 2" and "forgotten folder" contain it.
	 *
	 * getUniquePath(basePath, {"1", "1"}) would return "basePath/folder 1/folder 1" since both path parts uniquely
	 * identify subfolders.
	 *
	 * getUniquePath(basePath, {"1", "subfolder"}) would return "basePath/folder 1/subfolder" since all path parts
	 * uniquely identify subfolders or match their name exactly.
	 *
	 * @param basePath the base path in which to begin the search
	 * @param pathParts an array containing the sequence of unique substrings from the names of subfolders
	 * @return NULL if the construction of a unique path from the parts failed; the entire path otherwise
	 */
	private static String getUniquePath(String basePath, String[] pathParts){

		String constructedPath = basePath;

		// the last element of pathSplit should be the filename: ignore it
		for(int i=0; i<pathParts.length; i++){
			constructedPath = basePath;
			// add all found path segments
			for(int j=0; j<i; j++){
				constructedPath += "/" + pathParts[j];
			}

			File curDir = new File(constructedPath + "/" + pathParts[i]);
			if(curDir.exists() && curDir.isDirectory()){
				// the given directory name exists as is
				constructedPath += "/" + pathParts[i];

			}else if(!curDir.exists()){
				// the given directory name could be a substring
				List<File> fittingDirs = new LinkedList<>();
				for(File f: new File(constructedPath).listFiles()){
					if(f.isDirectory() && f.getName().toLowerCase().contains(pathParts[i].toLowerCase())){
						fittingDirs.add(f);
						if(f.getName().toLowerCase().equals(pathParts[i].toLowerCase())){
							fittingDirs.clear();
							fittingDirs.add(f);
							break;
						}
					}
				}

				if(fittingDirs.size() == 1){
					// it was unique
					pathParts[i] = fittingDirs.get(0).getName();
					constructedPath += "/" + pathParts[i];

				}else{
					// it either does not exist or was not unique: problem
					return null;
				}

			}else{
				// the given directory name exists but is not a directory: problem
				return null;
			}
		}

		return constructedPath;
	}

	/**
	 * Checks if there exists a file in the given directory which is uniquely identified with the specified
	 * file name. If such a file exists, its full name will be returned. Otherwise, NULL will be returned.
	 *
	 * First, a check is performed if the specified filename matches a file exactly.
	 * If no file fits exactly, a count of all files whose names contain the specified file name as a substring
	 * will be created. If this count is one (1), this file's name will be returned.
	 * Otherwise, NULL will be returned.
	 *
	 * @param path the directory where the file should be located in
	 * @param fileName the substring to be checked against unambiguity
	 * @return the unambigiously defined file's name or NULL
	 */
	private static String getUniqueFile(String path, String fileName){
		String ret = null;
		String fullPath = path + "/" + fileName;
		File f = new File(fullPath);

		if(f.exists() && f.isFile()){
			// the given file exists
			ret = new File(fullPath).getAbsolutePath();

		}else{
			// the given file does not exist as is... could be a substring
			List<File> files = new LinkedList<>();
			for(File file: new File(path).listFiles()){
				if(file.isFile() && file.getName().toLowerCase().contains(fileName.toLowerCase())){
					files.add(file);
					if(file.getName().toLowerCase().equals(fileName.toLowerCase())){
						files.clear();
						files.add(file);
						break;
					}
				}
			}

			if(files.size() == 1){
				// the file name was unique...
				ret = files.get(0).getAbsolutePath();

			}else{
				// the file name did not exist or was not unique: problem
				ret = null;
			}
		}
		return ret;
	}

	/**
	 * Eliminates leading and trailing quotes from the specified String.
	 *
	 * @param string the String to remove leading and trailing quotes from
	 * @return the String without the first leading and trailing quote.
	 */
	private static String unquote(String string){
		string = string.trim();
		if(string.startsWith("\"") || string.startsWith("'")){
			string = string.substring(1);
		}
		if(string.endsWith("\"") || string.endsWith("'")){
			string = string.substring(0, string.length() - 1);
		}
		return string;
	}

	/**
	 * Tries to read the configuration from the file in the specified path.
	 * Overwrites global settings variables.
	 *
	 * @param path The path of the file (including file name) to read the configuration from
	 */
	private static void readConfigFrom(String path){
		File propertyFile = new File(path);
		Properties properties = new Properties();

		if(propertyFile.exists() && propertyFile.isFile() && propertyFile.canRead()){
			try {
				properties.load(new FileReader(propertyFile));
				musicDir = properties.getProperty("musicDir", musicDir);
				playerDir = properties.getProperty("playerDir", playerDir);
				playerExec = properties.getProperty("playerExec", playerExec);

				try {
					port = Integer.parseInt(properties.getProperty("port", "2000"));
				} catch (Exception e) {
					port = 2000;
				}

				try {
					timeout = Integer.parseInt(properties.getProperty("timeout", "90"));
				} catch (Exception e) {
					timeout = 90;
				}

				try {
					maxSessionLength = Integer.parseInt(properties.getProperty("maxSessionLength", "300"));
				} catch (Exception e) {
					maxSessionLength = 300;
				}

				// append a separator to the music Dir
				if((musicDir != null) && !(musicDir.endsWith("/") || musicDir.endsWith("\\"))){
					musicDir += File.separator;
				}
			} catch (Exception e) {}
		}
	}

	/**
	 * Reads the properties File (first /etc/rmc/rmc.conf, then USER.HOME/rmc.conf) and sets jRMC's configuration accordingly.
	 * If a property cannot be read, a default value is used.
	 * Afterwards, the read configuration is written back into the configuration File in the user's home directory.
	 */
	private static void setupProperties(){
		readConfigFrom("/etc/rmc/rmc.conf");
		String userHome = System.getProperty("user.home");
		if(userHome != null){
			File personalSettingsFile = new File(userHome + File.separator + ".rmc");
			readConfigFrom(personalSettingsFile.getAbsolutePath());
			Properties properties = new Properties();
			properties.setProperty("musicDir", (musicDir == null) ? "INVALID PATH" : musicDir);
			properties.setProperty("playerDir", (playerDir == null) ? "INVALID PATH" : playerDir);
			properties.setProperty("playerExec", (playerExec == null) ? "INVALID PATH" : playerExec);
			properties.setProperty("port", Integer.toString(port));
			properties.setProperty("timeout", Integer.toString(timeout));
			properties.setProperty("maxSessionLength", Integer.toString(maxSessionLength));
			try {
				if(!personalSettingsFile.exists()){
					personalSettingsFile.createNewFile();
					properties.store(new FileWriter(personalSettingsFile), "jRMC Path Configuration File");
				}
			} catch (IOException e) {}
		}
	}

	/**
	 * Checks the currently set configuration. If it is valid, the function will return and nothing will happen.
	 * If it is invalid, it will print an error message to STDERR and exit the program with an exit code of 1.
	 */
	private static void checkProperties(){
		String userHome = System.getProperty("user.home");
		String configFilePath = "$HOME" + File.separator + ".rmc";
		if(userHome != null){
			configFilePath = new File(userHome + File.separator + ".rmc").getAbsolutePath();
		}

		// check if all items are configured
		if((musicDir == null) || (playerDir == null) || (playerExec == null)){

			System.err.println("One or more configuration Items not set. Please edit \"" + configFilePath + "\" and restart.");
			System.exit(1);
		}

		String errors = "";

		// check if the paths are valid
		File fMusicDir = new File(musicDir);
		if(!fMusicDir.exists() || !fMusicDir.isDirectory()){
			errors += "The Music Directory (" + musicDir + ") is invalid.\n";
		}

		File fPlayerDir = new File(playerDir);
		if(!fPlayerDir.exists() || !fPlayerDir.isDirectory()){
			errors += "The Player Directory (" + playerDir + ") is invalid.\n";
		}

		String pDir = playerDir + ((playerDir.endsWith(File.separator)) ? "" : File.separator);
		File fPlayerExec = new File(pDir + playerExec);
		if(!fPlayerExec.exists() || !fPlayerExec.isFile() || !fPlayerExec.canExecute()){
			errors += "The Player Executable (" + pDir + playerExec + ") is invalid.\n";
		}

		if((port <= 0) || (port >= 65535)){
			errors += "The Port has to be in the valid Port Range: [0, 65535].";
		}

		if((timeout < 0)){
			errors += "The Timeout has to be >= 0, where 0 means no timeout. Any value > 0 means the amount of seconds before a timeout";
		}

		if(!errors.isEmpty()){
			errors += "Please edit \"" + configFilePath + "\" and restart.";
			System.err.println(errors);
			System.exit(1);
		}

	}

	/**
	 * Tries to find the current User's default Music directory and return it.
	 * If it cannot be found, NULL will be returned instead.
	 *
	 * @return the user's found music directory or NULL
	 */
	private static String getDefaultMusicDir(){
		String path = "";
		String userHome = System.getProperty("user.home");
		userHome += (userHome.endsWith(File.separator)) ? "" : File.separator;
		path = userHome + "Music";
		File musicDir = new File(path);
		if(!musicDir.exists() || !musicDir.isDirectory()){
			return null;
		}else{
			return path;
		}
	}

	/**
	 * Checks if the specified string contains something that could be interpreted as a directory-up
	 * ("../" or "..\").
	 *
	 * @param str the String to check
	 * @return TRUE if a directory-up is contained, false otherwise
	 */
	private static boolean containsDirUp(String str){
		return (str.equals("..") || str.contains("../") || str.contains("..\\"));
	}

	/**
	 * A Timer that closes the Socket it was given when it expires.
	 * Before the Socket is closed, the specified message is sent.
	 *
	 * @author Maxmanski
	 *
	 */
	private static class TimeoutTask extends TimerTask{

		private Socket client = null;
		private String message = null;

		public TimeoutTask(Socket client, String message) {
			this.client = client;
			this.message = (message == null) ? ("ACK: TIMEOUT") : (message);
		}

		@Override
		public void run() {
			PrintWriter writer;
			try {
				writer = new PrintWriter(new BufferedOutputStream(client.getOutputStream()));
				writer.println(message);
				writer.flush();
				writer.close();
			} catch (IOException e) {}
			close(client);
		}

	}

	/**
	 * A Thread that handles incoming connections: When there is a currently open connection, a "busy" message
	 * will be sent and the connection will be closed.
	 *
	 * @author Maxmanski
	 *
	 */
	private static class RejectConnectionTask implements Runnable {

		private ServerSocket serverSocket = null;

		public RejectConnectionTask(ServerSocket socket) {
			this.serverSocket = socket;
		}

		public void run() {
			while(true){
				try {
					client = serverSocket.accept();
					if(isConnected()){
						PrintWriter writer = new PrintWriter(new BufferedOutputStream(client.getOutputStream()));
						writer.println("NACK (busy): jRMC");
						writer.flush();
						writer.close();
						close(client);
					}else{
						connected = true;
						synchronized(waitLock){
							waitLock.notifyAll();
						}
					}
				} catch (IOException e) {}
			}
		}
	}
}
