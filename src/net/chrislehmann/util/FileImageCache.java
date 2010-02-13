package net.chrislehmann.util;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;

import android.net.Uri;
import android.util.Log;
import android.widget.ImageView;

public class FileImageCache implements ImageCache
{
   private static final String LOGTAG = "FileImageCache";
   private File rootDir;
   private long maxCacheSize = 5;

   public FileImageCache(String prefix)
   {
      this.rootDir = new File( prefix );
   }

   public void load(String name, ImageView view)
   {
      if ( has( name ) )
      {
         view.setImageURI( Uri.parse( getFileName( name ) ) );
      }
   }

   public boolean has(String name)
   {
      return new File( getFileName( name ) ).exists();
   }

   private String getFileName(String name)
   {
      String filename = rootDir.getAbsolutePath() + "/" + name.hashCode();
      return filename;
   }

   public void put(String name, URL image)
   {
      try
      {
         FileUtils.forceMkdir( rootDir );
         ensureCacheBelowLimit();
         File f = new File( getFileName( name ) );
         FileUtils.copyURLToFile( image, f );
      }
      catch ( IOException e )
      {
         Log.e( LOGTAG, "Unable to create file", e );
      }
   }

   @SuppressWarnings("unchecked")
   private void ensureCacheBelowLimit()
   {
      long dirSize = FileUtils.sizeOfDirectory( rootDir );
      
      if( dirSize > maxCacheSize * FileUtils.ONE_MB )
      {
         List<File> files = new ArrayList<File>(FileUtils.listFiles( rootDir, FileFilterUtils.fileFileFilter(), null ));
         Comparator<File> isOlderComparator = new Comparator<File>()
         {
            public int compare(File arg0, File arg1)
            {
               int compareToValue = -1;
               if( FileUtils.isFileNewer( arg0, arg1 ) );
               {
                  compareToValue = 1;
               }
               return compareToValue;
            }
         };
         Collections.sort( files, isOlderComparator );
         while ( dirSize > maxCacheSize * FileUtils.ONE_MB && !files.isEmpty())
         {
            File nextFile = files.remove( 0 );
            nextFile.delete();
            dirSize = FileUtils.sizeOfDirectory( rootDir );
         }
      }
   }

   public void clear()
   {
      try
      {
         FileUtils.forceDelete( rootDir );
      }
      catch ( IOException e )
      {
         Log.e( LOGTAG, "Error cleaning cache directory" , e);
      }
   }

}