/*
 * This program is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License, version 2 as published by the Free Software
 * Foundation.
 *
 * You should have received a copy of the GNU General Public License along with this
 * program; if not, you can obtain a copy at http://www.gnu.org/licenses/gpl-2.0.html
 * or from the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 *
 * Copyright 2006 - 2016 Pentaho Corporation.  All rights reserved.
 */

package org.pentaho.reporting.platform.plugin.async;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.pentaho.platform.api.repository2.unified.IUnifiedRepository;
import org.pentaho.platform.api.repository2.unified.RepositoryFile;
import org.pentaho.platform.engine.core.system.PentahoSystem;
import org.pentaho.reporting.libraries.base.util.ArgumentNullException;

import java.io.Serializable;

/**
 * Moves report to the location provided by user
 */
class UpdateSchedulingLocationListener implements ISchedulingListener {

  private static Log log = LogFactory.getLog( UpdateSchedulingLocationListener.class );

  private final PentahoAsyncExecutor.CompositeKey savedKey;
  private final Serializable targetfolderId;

  UpdateSchedulingLocationListener( final PentahoAsyncExecutor.CompositeKey key, final Serializable targetfolderId ) {
    ArgumentNullException.validate( "savedKey", key );
    ArgumentNullException.validate( "targetLocation", targetfolderId );
    this.savedKey = key;
    this.targetfolderId = targetfolderId;
  }

  @Override
  public boolean onSchedulingCompleted( final PentahoAsyncExecutor.CompositeKey key, final Serializable fileId ) {

    if ( savedKey.equals( key ) ) {

      try {
        final IUnifiedRepository repo = PentahoSystem.get( IUnifiedRepository.class );
        final RepositoryFile savedFile = repo.getFileById( fileId );
        final RepositoryFile outputFolder = repo.getFileById( targetfolderId );

        if ( savedFile != null && !savedFile.isFolder() && outputFolder != null && outputFolder.isFolder() ) {
          repo.moveFile( savedFile.getId(), outputFolder.getPath(), "Moved to the location selected by user" );
        }
      } catch ( final Exception e ) {
        log.error( "Can't move report to selected location: ", e );
      }


      return Boolean.TRUE;
    }

    return Boolean.FALSE;
  }
}
