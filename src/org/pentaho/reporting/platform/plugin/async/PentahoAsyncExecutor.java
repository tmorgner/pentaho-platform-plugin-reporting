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

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.pentaho.platform.api.engine.ILogoutListener;
import org.pentaho.platform.api.engine.IPentahoSession;
import org.pentaho.platform.engine.core.system.PentahoSystem;
import org.pentaho.platform.engine.security.SecurityHelper;
import org.pentaho.platform.util.StringUtil;
import org.pentaho.reporting.libraries.base.util.ArgumentNullException;
import org.pentaho.reporting.platform.plugin.staging.AsyncJobFileStagingHandler;
import org.pentaho.reporting.platform.plugin.staging.IFixedSizeStreamingContent;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class PentahoAsyncExecutor<TReportState extends IAsyncReportState>
  implements ILogoutListener, IPentahoAsyncExecutor<TReportState> {

  public static final String BEAN_NAME = "IPentahoAsyncExecutor";

  private static final Log log = LogFactory.getLog( PentahoAsyncExecutor.class );

  private Map<CompositeKey, ListenableFuture<IFixedSizeStreamingContent>> futures = new ConcurrentHashMap<>();
  private Map<CompositeKey, IAsyncReportExecution<TReportState>> tasks = new ConcurrentHashMap<>();

  private ListeningExecutorService executorService;

  private final int autoSchedulerThreshold;
  private final MemorizeSchedulingLocationListener schedulingLocationListener;
  private List<ISchedulingListener> writeToJcrListeners;

  /**
   * @param capacity
   * @param autoSchedulerThreshold
   */
  public PentahoAsyncExecutor( final int capacity, final int autoSchedulerThreshold ) {
    this.autoSchedulerThreshold = autoSchedulerThreshold;
    log.info( "Initialized reporting  async execution fixed thread pool with capacity: " + capacity );
    executorService =
      new DelegatedListenableExecutor( new ThreadPoolExecutor( capacity, capacity, 0L, TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>() ) );
    PentahoSystem.addLogoutListener( this );
    this.writeToJcrListeners = new ArrayList<>();
    schedulingLocationListener = new MemorizeSchedulingLocationListener();
    this.writeToJcrListeners.add( schedulingLocationListener );
  }


  @Deprecated
  public PentahoAsyncExecutor( final int capacity ) {
    this( capacity, 0 );
  }

  // default visibility for testing purpose
  protected static class CompositeKey {

    private String sessionId;
    private String uuid;

    CompositeKey( final IPentahoSession session, final UUID id ) {
      this.uuid = id.toString();
      this.sessionId = session.getId();
    }

    private String getSessionId() {
      return sessionId;
    }

    @Override public boolean equals( final Object o ) {
      if ( this == o ) {
        return true;
      }
      if ( o == null || getClass() != o.getClass() ) {
        return false;
      }
      final CompositeKey that = (CompositeKey) o;
      return Objects.equals( sessionId, that.sessionId ) && Objects.equals( uuid, that.uuid );
    }

    @Override public int hashCode() {
      return Objects.hash( sessionId, uuid );
    }
  }

  @Override public UUID addTask( final IAsyncReportExecution<TReportState> task, final IPentahoSession session ) {

    final UUID id = UUID.randomUUID();

    final CompositeKey key = new CompositeKey( session, id );

    task.notifyTaskQueued( id,
      Collections.singletonList( new AutoScheduleListener( id, session, autoSchedulerThreshold, this ) ) );

    log.debug( "register async execution for task: " + task.toString() );

    final ListenableFuture<IFixedSizeStreamingContent> result = executorService.submit( task );
    futures.put( key, result );
    tasks.put( key, task );
    return id;
  }

  @Override public Future<IFixedSizeStreamingContent> getFuture( final UUID id, final IPentahoSession session ) {
    validateParams( id, session );
    return futures.get( new CompositeKey( session, id ) );
  }

  @Override public void cleanFuture( final UUID id, final IPentahoSession session ) {
    final CompositeKey key = new CompositeKey( session, id );
    futures.remove( key );
    tasks.remove( key );
  }

  @Override public void requestPage( final UUID id, final IPentahoSession session, final int page ) {
    validateParams( id, session );
    final IAsyncReportExecution<TReportState> runningTask = tasks.get( new CompositeKey( session, id ) );
    if ( runningTask != null ) {
      runningTask.requestPage( page );
    }
  }

  @Override
  public void schedule( final UUID id, final IPentahoSession session ) {
    validateParams( id, session );
    final CompositeKey compositeKey = new CompositeKey( session, id );
    final IAsyncReportExecution<TReportState> runningTask = tasks.get( compositeKey );
    if ( runningTask != null ) {
      if ( runningTask.schedule() ) {
        final ListenableFuture<IFixedSizeStreamingContent> future = futures.get( compositeKey );
        Futures.addCallback( future, new FutureCallback<IFixedSizeStreamingContent>() {
          @Override
          public void onSuccess( final IFixedSizeStreamingContent result ) {
            try {
              if ( session != null && !StringUtil.isEmpty( session.getName() ) ) {

                SecurityHelper.getInstance().runAsUser( session.getName(), () -> {
                  final Serializable writtenTo = new WriteToJcrTask( runningTask, result.getStream() ).call();
                  //We can be sure it succeed here and are ready to notify writeToJcrListeners
                  final Iterator<ISchedulingListener> iterator = writeToJcrListeners.iterator();
                  while ( iterator.hasNext() ) {
                    final ISchedulingListener next = iterator.next();
                    final boolean executed = next.onSchedulingCompleted( compositeKey, writtenTo );
                    if ( executed ) {
                      iterator.remove();
                    }
                  }
                  return null;
                } );
              }

            } catch ( final Exception e ) {
              log.error( "Can't execute callback. : ", e );
            } finally {
              //Time to remove future - nobody will ask for content at this moment
              //We need to keep task because status polling may still occur ( or it already has been removed on logout )
              //Also we can try to remove directory
              futures.remove( compositeKey );
              AsyncJobFileStagingHandler.cleanSession( session );
            }
          }

          @Override public void onFailure( final Throwable t ) {
            log.error( "Can't execute callback. Parent task failed: ", t );
            futures.remove( compositeKey );
            AsyncJobFileStagingHandler.cleanSession( session );
          }
        } );
      }
    }
  }

  @Override
  public void updateSchedulingLocation( final UUID id, final IPentahoSession session, final Serializable folderId ) {
    validateParams( id, session );
    final CompositeKey key = new CompositeKey( session, id );
    final Serializable fileId = schedulingLocationListener.getLocationMap().get( key );
    final UpdateSchedulingLocationListener listener = new UpdateSchedulingLocationListener( key, folderId );
    if ( null != fileId ) {
      //Report is already finished and saved to default scheduling directory
      listener.onSchedulingCompleted( key, fileId );
    } else {
      //Report is not finished yet,
      writeToJcrListeners.add( listener );
    }
    schedulingLocationListener.getLocationMap().remove( key );
  }

  @Override public TReportState getReportState( final UUID id, final IPentahoSession session ) {
    validateParams( id, session );
    // link to running task
    final IAsyncReportExecution<TReportState> runningTask = tasks.get( new CompositeKey( session, id ) );
    return runningTask == null ? null : runningTask.getState();
  }

  protected void validateParams( final UUID id, final IPentahoSession session ) {
    ArgumentNullException.validate( "uuid", id );
    ArgumentNullException.validate( "session", session );
  }

  @Override public void onLogout( final IPentahoSession session ) {
    if ( log.isDebugEnabled() ) {
      // don't expose full session id.
      log.debug( "killing async report execution cache for user: " + session.getName() );
    }

    for ( final Map.Entry<CompositeKey, ListenableFuture<IFixedSizeStreamingContent>> entry : futures.entrySet() ) {
      if ( ObjectUtils.equals( entry.getKey().getSessionId(), session.getId() ) ) {

        final IAsyncReportExecution<TReportState> task = tasks.get( entry.getKey() );

        final ListenableFuture<IFixedSizeStreamingContent> value = entry.getValue();

        if ( task != null && task.getState() != null && AsyncExecutionStatus.SCHEDULED
          .equals( task.getState().getStatus() ) ) {
          //After the session end nobody can poll status, we can remove task
          //Keep future to have content in place
          tasks.remove( entry.getKey() );
          continue;
        }

        //Close tmp files for completed futures
        Futures.addCallback( value, new FutureCallback<IFixedSizeStreamingContent>() {
          @Override public void onSuccess( final IFixedSizeStreamingContent result ) {
            if ( result != null ) {
              result.cleanContent();
            }
          }

          @Override public void onFailure( final Throwable ignored ) {
            //we don't care here anymore
          }
        } );

        // attempt to cancel running task
        value.cancel( true );

        // remove all links to release GC
        futures.remove( entry.getKey() );
        tasks.remove( entry.getKey() );
      }
    }

    //User can't update scheduling directory after logout, so we can clean location locationMap
    final ConcurrentHashMap<CompositeKey, Serializable> locationMap = schedulingLocationListener.getLocationMap();
    for ( final Map.Entry<CompositeKey, Serializable> entry : locationMap.entrySet() ) {
      if ( session.getId().equals( entry.getKey().getSessionId() ) ) {
        locationMap.remove( entry.getKey() );
      }
    }

    //If some files are still open directory won't be removed
    AsyncJobFileStagingHandler.cleanSession( session );

  }

  @Override
  public void shutdown() {
    // attempt to stop all
    for ( final Future<IFixedSizeStreamingContent> entry : futures.values() ) {
      entry.cancel( true );
    }
    // forget all
    this.futures.clear();
    this.tasks.clear();
    this.writeToJcrListeners.clear();
    this.schedulingLocationListener.getLocationMap().clear();

    AsyncJobFileStagingHandler.cleanStagingDir();
  }


}
