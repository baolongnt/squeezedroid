package net.chrislehmann.squeezedroid.service;

import net.chrislehmann.squeezedroid.model.PlayerStatus;


public interface PlayerStatusHandler
{
   public void onPlaylistChanged( PlayerStatus status );
   
   public void onSongChanged( PlayerStatus status );
   
   public void onTimeChanged( int newPosition );

}
