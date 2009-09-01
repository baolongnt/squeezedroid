package net.chrislehmann.squeezedroid.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.chrislehmann.squeezedroid.eventhandler.EventHandler;
import net.chrislehmann.squeezedroid.exception.ApplicationException;
import net.chrislehmann.squeezedroid.model.Album;
import net.chrislehmann.squeezedroid.model.Artist;
import net.chrislehmann.squeezedroid.model.BrowseResult;
import net.chrislehmann.squeezedroid.model.Genre;
import net.chrislehmann.squeezedroid.model.Item;
import net.chrislehmann.squeezedroid.model.Player;
import net.chrislehmann.squeezedroid.model.PlayerStatus;
import net.chrislehmann.squeezedroid.model.Song;
import net.chrislehmann.util.SerializationUtils;
import net.chrislehmann.util.SerializationUtils.Unserializer;

/**
 * Implementation of {@link SqueezeService} that uses the SqueezeCenter command
 * line interface
 * 
 * @author lehmanc
 */
public class CliSqueezeService implements SqueezeService {

	/**
	 * Host to connect to
	 */
	private String host = "localhost";
	/**
	 * Port to connect to
	 */
	private int cliPort = 9090;
	private int httpPort = 9000;

	private Socket clientSocket;
	private Writer clientWriter;
	private BufferedReader clientReader;

	private EventThread eventThread;

	public CliSqueezeService(String host, int cliPort, int httpPort) {
		super();
		this.host = host;
		this.cliPort = cliPort;
		this.httpPort = httpPort;
	}

	private Pattern countPattern = Pattern.compile("count%3A([^ ]*)");
	private Pattern artistsResponsePattern = Pattern.compile("id%3A([^ ]*) artist%3A([^ ]*)");
	private Pattern genresResponsePattern = Pattern.compile("id%3A([^ ]*) genre%3A([^ ]*)");
	private Pattern albumsResponsePattern = Pattern.compile("id%3A([^ ]*) album%3A([^ ]*)( artwork_track_id%3A([0-9]+)){0,1} artist%3A([^ ]*)");
	private Pattern playersResponsePattern = Pattern.compile("playerid%3A([^ ]*) uuid%3A([^ ]*) ip%3A([^ ]*) name%3A([^ ]*)");
	private Pattern songsResponsePattern = Pattern.compile("id%3A([^ ]*) title%3A([^ ]*) genre%3A([^ ]*) artist%3A([^ ]*) album%3A([^ ]*)");
	private Pattern playlistResponsePattern = Pattern.compile("id%3A([^ ]*) title%3A([^ ]*) artist%3A([^ ]*) artist_id%3A([^ ]*) album%3A([^ ]*) album_id%3A([^ ]*)");
	private Pattern playlistCountPattern = Pattern.compile("playlist_tracks%3A([^ ]*)");
	private Pattern playerStatusResponsePattern = Pattern.compile("playlist_cur_index%3A([0-9]*)");

	/**
	 * Connect to the squeezecenter server and log in if required. Will throw an
	 * {@link ApplicationException} if the connection fails.
	 */
	public void connect() {
		try {
			clientSocket = new Socket(host, cliPort);
			clientWriter = new OutputStreamWriter(clientSocket.getOutputStream());
			clientReader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
		} catch (Exception e) {
			throw new ApplicationException("Cannot connect to host '" + host + "' at port '" + cliPort, e);
		}

		eventThread = new EventThread(host, cliPort);
		eventThread.start();
	}

	/**
	 * Disconnect from the server. Throws an {@link ApplicationException} if an
	 * error occours
	 */
	public void disconnect() {
		if (clientSocket != null && clientSocket.isConnected()) {
			try {
				clientSocket.close();
				clientReader = null;
				clientWriter = null;
			} catch (Exception e) {
				throw new ApplicationException("Error closing socket", e);
			}
			clientSocket = null;
		}

		eventThread.stop();

	}

	public boolean isConnected() {
		return clientSocket != null && clientSocket.isConnected();
	}

	synchronized private String executeCommand(String command) {
		writeCommand(command);
		return readResponse();
	}

	private String readResponse() {
		try {
			return clientReader.readLine();
		} catch (IOException e) {
			throw new RuntimeException("Error reading from server", e);
		}
	}

	private void writeCommand(String command) {
		try {
			clientWriter.write(command + "\n");
			clientWriter.flush();
		} catch (IOException e) {
			throw new RuntimeException("Error communitcating with server.", e);
		}
	}

	public BrowseResult<Genre> browseGenres(Item parent, int start, int numberOfItems) {
	
		String command = "genres " + start + " " + numberOfItems;
		String result = executeCommand(command);
		Unserializer<Genre> unserializer = new Unserializer<Genre>() {

			public Genre unserialize(Matcher matcher) {
				Genre genre = new Genre();
				genre.setId(matcher.group(1));
				genre.setName(SerializationUtils.decode(matcher.group(2)));
				return genre;
			}
		};
		List<Genre> genres = SerializationUtils.unserializeList(genresResponsePattern, result, unserializer);
		BrowseResult<Genre> browseResult = new BrowseResult<Genre>();
		browseResult.setResutls(genres);
		browseResult.setTotalItems(unserializeCount(result));
		return browseResult;
	}

	
	
	public BrowseResult<Album> browseAlbums(Item parent, int start, int numberOfItems) {
		return browseAlbums(parent, start, numberOfItems, Sort.TITLE);
	}
	
	public BrowseResult<Album> browseAlbums(Item parent, int start, int numberOfItems, Sort sort) {
		String command = "albums " + start + " " + numberOfItems;
		if (parent instanceof Artist) {
			command += " artist_id:" + parent.getId();
		}
		if( sort != Sort.TITLE)
		{
			command += " sort:" + sort.toString().toLowerCase();
		}
		
		command += " tags:laj";
		String result = executeCommand(command);

		List<Album> albums = SerializationUtils.unserializeList(albumsResponsePattern, result, new Unserializer<Album>() {
			public Album unserialize(Matcher matcher) {
				Album album = new Album();
				album.setId(matcher.group(1));
				album.setName(SerializationUtils.decode(matcher.group(2)));
				album.setArtist(SerializationUtils.decode(matcher.group(5)));
				album.setCoverThumbnailUrl("http://" + host + ":" + httpPort + "/music/" + matcher.group(4) + "/cover_50x50_o");
				album.setCoverUrl("http://" + host + ":" + httpPort + "/music/" + matcher.group(4) + "/cover_320x320	_o");
				return album;
			}
		});

		BrowseResult<Album> browseResult = new BrowseResult<Album>();
		browseResult.setTotalItems(unserializeCount(result));
		browseResult.setResutls(albums);
		return browseResult;
	}
	
	public BrowseResult<Artist> browseArtists(Item parent, int start, int numberOfItems) {
		String command = "artists " + start + " " + numberOfItems;
		String result = executeCommand(command);

		Matcher matcher = artistsResponsePattern.matcher(result);

		List<Artist> artists = new ArrayList<Artist>();
		while (matcher.find()) {
			Artist artist = new Artist();
			artist.setId(matcher.group(1));
			artist.setName(SerializationUtils.decode(matcher.group(2)));
			artists.add(artist);
		}
		
		BrowseResult<Artist> browseResult = new BrowseResult<Artist>();
		browseResult.setResutls(artists);
		browseResult.setTotalItems(unserializeCount(result));
		return browseResult;
	}

	public BrowseResult<Song> browseSongs(Item parent, int start, int numberOfItems) {
		String command = "titles " + start + " " + numberOfItems;

		BrowseResult<Song> browseResult = new BrowseResult<Song>();
		
		if (parent instanceof Artist) {
			command += " artist_id:" + parent.getId();
		} else if (parent instanceof Album) {
			command += " album_id:" + parent.getId();
		}

		String result = executeCommand(command);

		List<Song> songs = SerializationUtils.unserializeList(songsResponsePattern, result, new SerializationUtils.Unserializer<Song>() {
			public Song unserialize(Matcher matcher) {
				Song song = new Song();
				song.setId(matcher.group(1));
				song.setName(SerializationUtils.decode(matcher.group(2)));
				song.setImageUrl("http://" + host + ":" + httpPort + "/music/" + matcher.group(1) + "/cover_320x320_o");
				song.setImageThumbnailUrl("http://" + host + ":" + httpPort + "/music/" + matcher.group(1) + "/cover_50x50_o");
				return song;
			}
		});
		
		browseResult.setTotalItems(unserializeCount(result) );
		browseResult.setResutls(songs);
		return browseResult;
	}

	private Integer unserializeCount(String result) {
		Integer numSongs = 0;
		Matcher countMatcher = countPattern.matcher(result);
		if( countMatcher.find() )
		{
			String countString = countMatcher.group(1);
			numSongs = Integer.valueOf(countString);
		}
		else
		{
			android.util.Log.e(this.getClass().getCanonicalName(), "Cannot find match for count from response '" + result + "'");
		}
		return numSongs;
	}

	public List<Player> getPlayers() {

		String command = new String("players 0 1000");
		String result = executeCommand(command);

		return SerializationUtils.unserializeList(playersResponsePattern, result, new SerializationUtils.Unserializer<Player>() {
			public Player unserialize(Matcher matcher) {
				Player player = new Player();
				player.setId(SerializationUtils.decode(matcher.group(1)));
				player.setName(SerializationUtils.decode(matcher.group(4)));
				return player;
			}
		});
	}

	public PlayerStatus getPlayerStatus(Player player) {
		String command = new String(player.getId() + " status - 1 tags:asleJpP");
		String result = executeCommand(command);

		PlayerStatus status = SerializationUtils.unserialize(playlistResponsePattern, result, new SerializationUtils.Unserializer<PlayerStatus>() {
			public PlayerStatus unserialize(Matcher matcher) {
				PlayerStatus status = new PlayerStatus();

				Song song = new Song();
				song.setId(matcher.group(1));
				song.setName(SerializationUtils.decode(matcher.group(2)));
				song.setArtist(SerializationUtils.decode(matcher.group(3)));
				song.setArtistId(SerializationUtils.decode(matcher.group(4)));
				song.setAlbum(SerializationUtils.decode(matcher.group(5)));
				song.setAlbumId(SerializationUtils.decode(matcher.group(6)));
				song.setImageUrl("http://" + host + ":" + httpPort + "/music/" + matcher.group(1) + "/cover_320x320_o");
				song.setImageThumbnailUrl("http://" + host + ":" + httpPort + "/music/" + matcher.group(1) + "/cover_50x50_o");
				status.setCurrentSong(song);
				return status;
			}
		});

		Matcher statusMatcher = playerStatusResponsePattern.matcher(result);
		if (status != null && statusMatcher.find() && statusMatcher.group(1) != null) {
			status.setCurrentIndex(Integer.parseInt(statusMatcher.group(1)));
		}
		return status;

	}

	public BrowseResult<Song> getCurrentPlaylist(Player player, Integer start, Integer numberOfItems) {
		String command = player.getId() + " status " + start + " " + numberOfItems + " tags:asleJpP";
		String result = executeCommand(command);

		BrowseResult<Song> browseResult = new BrowseResult<Song>();
		List<Song> songs = SerializationUtils.unserializeList(playlistResponsePattern, result, new SerializationUtils.Unserializer<Song>() {
			public Song unserialize(Matcher matcher) {
				Song song = new Song();
				song.setId(matcher.group(1));
				song.setName(SerializationUtils.decode(matcher.group(2)));
				song.setArtist(SerializationUtils.decode(matcher.group(3)));
				song.setArtistId(SerializationUtils.decode(matcher.group(4)));
				song.setAlbum(SerializationUtils.decode(matcher.group(5)));
				song.setAlbumId(SerializationUtils.decode(matcher.group(6)));
				song.setImageUrl("http://" + host + ":" + httpPort + "/music/" + matcher.group(1) + "/cover_320x320_o");
				song.setImageThumbnailUrl("http://" + host + ":" + httpPort + "/music/" + matcher.group(1) + "/cover_50x50_o");
				return song;
			}
		});
		browseResult.setResutls(songs);
		
		Matcher countMatcher = playlistCountPattern.matcher(result);
		if( countMatcher.find() )
		{
			String countString = countMatcher.group(1);
			browseResult.setTotalItems(Integer.valueOf(countString));
		}
		else
		{
			android.util.Log.e(this.getClass().getCanonicalName(), "Cannot find match for count from status response '" + result + "'");
		}
		return browseResult;
	}

	public void addItem(Player player, Item item) {
		String extraParams = getParamName(item);

		String command = player.getId() + " playlist addtracks " + extraParams + "=" + item.getId();
		executeCommand(command);
	}

	public void playItem(Player player, Item item) {
		String extraParams = getParamName(item);

		String command = player.getId() + " playlist loadtracks " + extraParams + "=" + item.getId();
		executeCommand(command);
	}

	private String getParamName(Item item) {
		String extraParams = null;
		if (item instanceof Album) {
			extraParams = "album.id";
		} else if (item instanceof Artist) {
			extraParams = "contributor.id";
		} else if (item instanceof Artist) {
			extraParams = "track.id";
		}
		return extraParams;
	}

	public void subscribe(Event event, String playerId, EventHandler handler) {
		if (eventThread != null) {
			eventThread.subscribe(event, playerId, handler);
		}
	}

	public void unsubscribe(Event event, String playerId, EventHandler handler) {
		if (eventThread != null) {
			eventThread.unsubscribe(event, playerId, handler);
		}
	}

	public void unsubscribeAll(Event event) {
		if (eventThread != null) {
			eventThread.unsubscribeAll(event);
		}
	}

	public void jump(Player player, String position) {

		executeCommand(player.getId() + " playlist index " + position);

	}

	public void togglePause(Player player) {
		executeCommand(player.getId() + " pause");
	}

	public void pause(Player player) {
		executeCommand(player.getId() + " pause 1");
	}

	public void play(Player player) {
		executeCommand(player.getId() + " play");
	}

	public void stop(Player player) {
		executeCommand(player.getId() + " stop");
	}

	public void removeAllItemsByArtist(Player player, String artistId) {
		executeCommand(player.getId() + " playlistcontrol cmd:delete artist_id:" + artistId);
	}

	public void removeAllItemsInAlbum(Player player, String albumId) {
		executeCommand(player.getId() + " playlistcontrol cmd:delete album_id:" + albumId);
	}

	public void removeItem(Player player, int playlistIndex) {
		executeCommand(player.getId() + " playlist delete " + playlistIndex);
	}

}