package net.chrislehmann.squeezedroid.activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import net.chrislehmann.squeezedroid.model.Player;
import net.chrislehmann.squeezedroid.service.DownloadService;
import net.chrislehmann.squeezedroid.service.ServiceConnectionManager.SqueezeServiceAwareThread;
import net.chrislehmann.squeezedroid.service.SqueezeService;

/**
 * Base activity that contains some methods to manage the {@link SqueezeService} and the
 * currently selected {@link Player} objects.
 *
 * @author lehmanc
 */
public class SqueezedroidActivitySupport extends ActivitySupport {
    //private static final String LOGTAG = "SqueezeDroidActivitySupport";


    /**
     * {@link BroadcastReceiver} to listen for connection changes and re-connect the service.
     */
    BroadcastReceiver onConnectionChanged = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            boolean isDisconnected = intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
            if (isDisconnected) {
                getSqueezeDroidApplication().resetService();
            }
        }
    };
    private boolean lookingForPlayer = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(onConnectionChanged, filter);

        String dialogName = getIntent().getStringExtra(SqueezeDroidConstants.IntentDataKeys.KEY_DIALOG_NAME);
        if (dialogName != null) {
            setTitle(dialogName);
        }

    }

    /**
     * Gets the currently selected player.  This will try the following, in this order:
     * <p/>
     * 1) Use the player held in the {@link SqueezeDroidApplication}
     * 2) Retrieve the last used player from this application's {@link SharedPreferences} and load that
     * from ther server
     * 3) Start an activity that will prompt the user to choose a player
     */
    public Player getSelectedPlayer() {
        return getSelectedPlayer(false);
    }

    public Player getSelectedPlayer(boolean forceUpdate) {
        Player selectedPlayer = getSqueezeDroidApplication().getSelectedPlayer();
        if (selectedPlayer == null || forceUpdate) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this.getBaseContext());
            String lastPlayerId = prefs.getString(SqueezeDroidConstants.Preferences.LAST_SELECTED_PLAYER, null);
            SqueezeService service = getService();
            if (service != null) {
                if (lastPlayerId != null) {
                    selectedPlayer = service.getPlayer(lastPlayerId);
                }
                if (selectedPlayer == null) {
                    if (!lookingForPlayer) {
                        launchSubActivity(ChoosePlayerActivity.class, choosePlayerIntentCallback);
                        lookingForPlayer = true;
                    }
                } else {
                    getSqueezeDroidApplication().setSelectedPlayer(selectedPlayer);
                }
            }
        }
        return selectedPlayer;
    }

    /**
     * Sets the currently selected player.
     *
     * @player the currently selected player
     */
    protected void setSelectedPlayer(Player player) {
        getSqueezeDroidApplication().setSelectedPlayer(player);
    }

    /**
     * Returns true if a player is selected
     */
    protected boolean isPlayerSelected() {
        return getSelectedPlayer() != null;
    }

    /**
     * Helper method to simply get the application and cast it to a {@link SqueezeDroidApplication}
     *
     * @return
     */
    public SqueezeDroidApplication getSqueezeDroidApplication() {
        return (SqueezeDroidApplication) getApplication();
    }


    /**
     * Ensures that the {@link SqueezeService} is connected (possibly by forwarding to the {@link ConnectToServerActivity} and
     * calls the {@link SqueezeServiceAwareThread#runWithService(SqueezeService)} with a connected {@link SqueezeService}
     *
     * @param onConnect   {@link SqueezeServiceAwareThread} to run after the server connection has been obtained.
     * @param runOnThread If set to true, a new thread will be spawned to run the onConnect
     */
    public void runWithService(final SqueezeServiceAwareThread onConnect, boolean runOnThread) {

        if (runOnThread) {
            new SqueezeServiceAwareThread() {
                public void runWithService(final SqueezeService service) {
                    new Thread() {
                        public void run() {
                            onConnect.runWithService(service);
                        }

                        ;
                    }.start();
                }
            };
        }

        getSqueezeDroidApplication().getConnectionManager().getService(this, true, onConnect);
    }

    /**
     * Ensures that the {@link SqueezeService} is connected (possibly by forwarding to the {@link ConnectToServerActivity} and
     * calls the {@link SqueezeServiceAwareThread#runWithService(SqueezeService)} with a connected {@link SqueezeService}
     *
     * @param onConnect {@link SqueezeServiceAwareThread} to run after the server connection has been obtained.
     */
    public void runWithService(final SqueezeServiceAwareThread onConnect) {
        runWithService(onConnect, false);
    }


    /**
     * Gets the {@link SqueezeService}.  If the connect parameter is set to true and the {@link SqueezeService} is not connected,
     * this method will start the {@link ConnectToServerActivity} and return null.  Your code should take this into account.
     *
     * @param connect If true, try and connect to the service if it is not connected
     */
    public SqueezeService getService(boolean connect) {
        return getSqueezeDroidApplication().getConnectionManager().getService(this, connect, null);
    }

    /**
     * Gets the {@link SqueezeService}.  If the {@link SqueezeService} is not connected,
     * this method will start the {@link ConnectToServerActivity} and return null.  Your code should take this into account.
     */
    public SqueezeService getService() {
        return getService(true);
    }


    /**
     * Child Activity callback {@link IntentResultCallback}s
     */
    protected IntentResultCallback choosePlayerIntentCallback = new IntentResultCallback() {
        public void resultOk(String resultString, Bundle resultMap) {

            Player selectedPlayer = (Player) resultMap.getSerializable(SqueezeDroidConstants.IntentDataKeys.KEY_SELECTED_PLAYER);
            if (selectedPlayer == null) {
                closeApplication();
            }
            getSqueezeDroidApplication().setSelectedPlayer(selectedPlayer);
            lookingForPlayer = false;
        }

        public void resultCancel(String resultString, Bundle resultMap) {
            if (getSqueezeDroidApplication().getSelectedPlayer() == null) {
                closeApplication();
            }

        }
    };


//    private DownloadService _downloadService;
//    private boolean _isBound;
//
//    private ServiceConnection _connection = new ServiceConnection() {
//        public void onServiceConnected(ComponentName className, IBinder service) {
//            _downloadService = ((DownloadService.LocalBinder) service).getService();
//        }
//
//        public void onServiceDisconnected(ComponentName className) {
//            _downloadService = null;
//        }
//    };

    protected void addDownload( String url, String path) {
        Intent i = new Intent(this, DownloadService.class);
        i.putExtra(DownloadService.DOWNLOAD_SERVICE_REQUESTED_URL, url);
        i.putExtra(DownloadService.DOWNLOAD_SERVICE_REQUESTED_PATH, path);
        startService(i);
//        bindService(i, _connection, Context.BIND_AUTO_CREATE);
    }
//
//    private void doUnbindService() {
//        if (_isBound) {
//            unbindService(_connection);
//            _isBound = false;
//        }
//    }
//
//    @Override
//    protected void onDestroy() {
//        super.onDestroy();
//        doUnbindService();
//    }

}
