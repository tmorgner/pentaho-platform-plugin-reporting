/* !
 * This program is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License, version 2.1 as published by the Free Software
 * Foundation.
 *
 *  You should have received a copy of the GNU Lesser General Public License along with this
 *  program; if not, you can obtain a copy at http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 *  or from the Free Software Foundation, Inc.,
 *  51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  Copyright (c) 2002-2016 Pentaho Corporation..  All rights reserved.
 *
 */
package org.pentaho.reporting.platform.plugin.cache;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Default interface for cache backend
 */
public class FileSystemCacheBackend implements ICacheBackend {

  private static final Log logger = LogFactory.getLog( FileSystemCacheBackend.class );
  public static final String REPLACEMENT = "_";
  public static final String SLASHES = "[/\\\\]+";

  private String cachePath;

  public void setCachePath( final String cachePath ) {
    this.cachePath = getSystemTmp() + cachePath;
  }

  @Override
  public boolean write( final List<String> key, final Serializable value ) {
    final String filePath = cachePath + StringUtils.join( cleanKey( key ), File.separator );
    final File file = new File( filePath );
    lock( filePath );
    try {
      //create file structure
      file.getParentFile().mkdirs();
      if ( !file.exists() ) {
        file.createNewFile();
      }
      //closable resources
      try (
        final FileOutputStream fout = new FileOutputStream( file );
        final ObjectOutputStream oos = new ObjectOutputStream( fout ) ) {
        oos.writeObject( value );
      }
    } catch ( final IOException e ) {
      logger.error( "Can't write cache: ", e );
      return false;
    } finally {
      unlock( filePath );
    }
    return true;

  }

  private void lock( final String filePath ) {
    final File lock = new File( filePath + ".lock" );
    while ( lock.exists() ) {
      try {
        Thread.sleep( 100 );
      } catch ( final InterruptedException e ) {
        logger.error( "Can't create lock: ", e );
        return;
      }
    }
    //create file structure
    lock.getParentFile().mkdirs();
    if ( !lock.exists() ) {
      try {
        lock.createNewFile();
      } catch ( final IOException e ) {
        logger.error( "Can't create lock: ", e );
      }
    }
  }

  private void unlock( final String filePath ) {
    final File lock = new File( filePath + ".lock" );
    lock.delete();
  }

  @Override
  public Serializable read( final List<String> key ) {
    Object result = null;
    final String filePath = cachePath + StringUtils.join( cleanKey( key ), File.separator );
    lock( filePath );
    try (
      final FileInputStream fis = new FileInputStream( filePath );
      final ObjectInputStream ois = new ObjectInputStream( fis ) ) {
      result = ois.readObject();
    } catch ( final Exception e ) {
      logger.debug( "Can't read cache: ", e );
    } finally {
      unlock( filePath );
    }
    return (Serializable) result;

  }

  @Override
  public boolean purge( final List<String> key ) {
    try {
      final File file = new File( cachePath + StringUtils.join( cleanKey( key ), File.separator ) );
      if ( file.isDirectory() ) {
        FileUtils.deleteDirectory( file );
        return !file.exists();
      }
      return file.delete();
    } catch ( final Exception e ) {
      logger.debug( "Can't delete cache: ", e );
      return false;
    }
  }

  @Override
  public Set<String> listKeys( final List<String> key ) {
    final Set<String> resultSet = new HashSet<>();
    final File directory = new File( cachePath + StringUtils.join( key, File.separator ) );
    final File[] fList = directory.listFiles();
    if ( fList != null ) {
      for ( final File file : fList ) {
        resultSet.add( file.getName() );
      }
    }
    return resultSet;
  }

  private String getSystemTmp() {
    final String s = System.getProperty( "java.io.tmpdir" ); //$NON-NLS-1$
    final char c = s.charAt( s.length() - 1 );
    if ( ( c != '/' ) && ( c != '\\' ) ) {
      System.setProperty( "java.io.tmpdir", s + "/" ); //$NON-NLS-1$//$NON-NLS-2$
    }
    return s;
  }

  private static List<String> cleanKey( final List<String> key ) {
    final List<String> clean = new ArrayList<>( key.size() );
    for ( final String keyPart : key ) {
      clean.add( keyPart.replaceAll( SLASHES, REPLACEMENT ) );
    }
    return clean;
  }

}
