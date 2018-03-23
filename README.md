# RMC - Remote Music Control
Remote Music Control was a pet project aiming at improving the usefulness of my home server by turning it into a jukebox that can be controlled over the network (e.g. through an Android App).


## Usage

### Dependencies
RMC requires only Java and one of the supported music players to be installed (see below).


### Setup
Compile the sources and either run them directly or package them in a runnable JAR file.  
Create a fitting configuration (see below) and run RMC.


### Connection
The connection does not require a specific client, but can be done with any tool that builds up a plaintext TCP connection (such as `telnet`, `netcat`, etc.).

Note: Currently, only a single session can be set up at a time.


## Configuration
The program can be configured via a file named `.rmc` in the user's home directory (`/home/max/.rmc`, `C:\Users\Max\.rmc`, ...).

An example configuration file looks like the following:
```
#jRMC Path Configuration File
#Sat Jan 28 13:07:56 CET 2017
port=2000
playerExec=foobar2000.exe
musicDir=C\:\\Users\\Max\\Music
playerDir=C:\\Program Files\\foobar2000
timeout=90
maxSessionLength=300
```

* `port`: The TCP port on which RMC should listen for incoming connections
* `playerDir`: The directory in which the music player executable resides
* `playerExec`: The name of the executable of the music player
* `musicDir`: The directory in which the music files can be found
* `timeout`: A timeout in seconds; if no command is received from the session in this amount of time, it will be disconnected
* `maxSessionLength`: The maximum duration of a single session in seconds, to prevent a single user from indefinitely blocking the service

Note: Currently, only the music players `foobar2000`, `totem` and `mpc` are supported.


## Protocol
The server listens for incoming messages from a connected session (i.e. connected user). If a command is recognised, it is executed and a reply is sent.  
This reply starts with either `ACK` or `NACK` followed by additional information, depending on whether the command was executed successfully or not.

### Available Commands
* `HELP`: Show overview over the possible commands
* `PLAY song1;song2;...`: Create a new playlist and start playing
* `ADD song1;song2;...`: Add songs to the current playlist
* `PAUSE`: Pause playback
* `PLAY`: Resume playback
* `NEXT`: Skip to the next song in the playlist
* `PREVIOUS`: Skip to the previous song in the playlist
* `RANDOM`: Play a random song from the current playlist
* `STOP`: Stop playback and delete current playlist
* `LIST directory`: List MP3 songs in the directory
* `PLAYLIST`: List contents of the current playlist

Note: For the commands taking files and directories as arguments (`PLAY`, `ADD` and `LIST`), it suffices to specify unique substrings. For instance, `PLAY rains` will be the same as `Play 'The Rains of Castamere.mp3'`, as long as there is no other file containing the substring `rains`.  
Similarly, `PLAY arc/do` will be the same as `PLAY 'Arctic Monkeys/Do I Wanna Know.mp3'`, as long as there is no other top-level directory containing the substring `arc` and no other file inside `Arctic Monkeys/` containing the substring `do`.