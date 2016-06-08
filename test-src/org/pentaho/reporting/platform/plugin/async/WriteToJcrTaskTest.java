/*!
 * This program is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License, version 2.1 as published by the Free Software
 * Foundation.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this
 * program; if not, you can obtain a copy at http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 * or from the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * Copyright (c) 2002-2016 Pentaho Corporation..  All rights reserved.
 */

package org.pentaho.reporting.platform.plugin.async;

import org.apache.commons.io.input.NullInputStream;
import org.eclipse.jetty.util.ConcurrentHashSet;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.pentaho.platform.api.repository2.unified.IUnifiedRepository;
import org.pentaho.platform.api.repository2.unified.RepositoryFile;
import org.pentaho.platform.engine.core.system.PentahoSystem;
import org.pentaho.platform.engine.core.system.boot.PlatformInitializationException;
import org.pentaho.reporting.libraries.repository.ContentCreationException;
import org.pentaho.reporting.libraries.repository.ContentEntity;
import org.pentaho.reporting.libraries.repository.ContentIOException;
import org.pentaho.reporting.libraries.repository.ContentItem;
import org.pentaho.reporting.libraries.repository.ContentLocation;
import org.pentaho.reporting.libraries.repository.Repository;
import org.pentaho.reporting.platform.plugin.MicroPlatformFactory;
import org.pentaho.reporting.platform.plugin.repository.ReportContentRepository;
import org.pentaho.reporting.platform.plugin.staging.IFixedSizeStreamingContent;
import org.pentaho.test.platform.engine.core.MicroPlatform;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class WriteToJcrTaskTest {

  private static MicroPlatform microPlatform;

  @Before
  public void setUp() throws PlatformInitializationException {
    microPlatform = MicroPlatformFactory.create();
    final IUnifiedRepository repository = mock( IUnifiedRepository.class );
    final ISchedulingDirectoryStrategy strategy = mock( ISchedulingDirectoryStrategy.class );
    final RepositoryFile targetDir = mock( RepositoryFile.class );
    when( strategy.getSchedulingDir( repository ) ).thenReturn( targetDir );
    when( targetDir.getPath() ).thenReturn( "/test" );
    microPlatform.defineInstance( "IUnifiedRepository", repository );
    microPlatform.defineInstance( "ISchedulingDirectoryStrategy", strategy );
    microPlatform.start();
  }

  @After
  public void tearDown() {
    microPlatform.stop();
  }

  @Test
  public void testPositiveScenario() throws Exception {
    final UUID uuid = UUID.randomUUID();
    final RepositoryFile file = mock( RepositoryFile.class );
    when( file.getId() ).thenReturn( uuid.toString() );
    when( PentahoSystem.get( IUnifiedRepository.class ).getFile( "/test/report.pdf" ) ).thenReturn(
      file );
    final FakeLocation fakeLocation = new FakeLocation();
    final IFixedSizeStreamingContent content = mock( IFixedSizeStreamingContent.class );
    final IAsyncReportExecution reportExecution = mock( IAsyncReportExecution.class );
    final IAsyncReportState state = mock( IAsyncReportState.class );
    when( state.getMimeType() ).thenReturn( "application/pdf" );
    when( state.getPath() ).thenReturn( "report.prpt" );
    when( reportExecution.getState() ).thenReturn( state );
    final NullInputStream inputStream = new NullInputStream( 100 );
    when( content.getStream() ).thenReturn( inputStream );
    final ReportContentRepository contentRepository = mock( ReportContentRepository.class );
    when( contentRepository.getRoot() ).thenReturn( fakeLocation );
    final WriteToJcrTask toJcrTask = new WriteToJcrTask( reportExecution, inputStream ) {
      @Override protected ReportContentRepository getReportContentRepository( final RepositoryFile outputFolder ) {
        return contentRepository;
      }
    };
    assertNotNull( toJcrTask.call() );
    assertTrue( fakeLocation.exists( "report.pdf" ) );
  }

  @Test
  public void testNoNames() throws Exception {
    final UUID uuid = UUID.randomUUID();
    final RepositoryFile file = mock( RepositoryFile.class );
    when( file.getId() ).thenReturn( uuid.toString() );
    when( PentahoSystem.get( IUnifiedRepository.class ).getFile( "/test/content.txt" ) ).thenReturn(
      file );
    final FakeLocation fakeLocation = new FakeLocation();
    final IFixedSizeStreamingContent content = mock( IFixedSizeStreamingContent.class );
    final IAsyncReportExecution reportExecution = mock( IAsyncReportExecution.class );
    final IAsyncReportState state = mock( IAsyncReportState.class );
    when( state.getMimeType() ).thenReturn( null );
    when( state.getPath() ).thenReturn( "/" );
    when( reportExecution.getState() ).thenReturn( state );
    final NullInputStream inputStream = new NullInputStream( 100 );
    when( content.getStream() ).thenReturn( inputStream );
    final ReportContentRepository contentRepository = mock( ReportContentRepository.class );
    when( contentRepository.getRoot() ).thenReturn( fakeLocation );
    final WriteToJcrTask toJcrTask = new WriteToJcrTask( reportExecution, inputStream ) {
      @Override protected ReportContentRepository getReportContentRepository( final RepositoryFile outputFolder ) {
        return contentRepository;
      }
    };

    assertEquals( uuid.toString(), toJcrTask.call() );
    assertTrue( fakeLocation.exists( "content.txt" ) );
  }

  @Test
  public void testAlreadyExists() throws Exception {
    final UUID uuid = UUID.randomUUID();
    final RepositoryFile file = mock( RepositoryFile.class );
    when( file.getId() ).thenReturn( uuid.toString() );
    when( PentahoSystem.get( IUnifiedRepository.class ).getFile( "/test/report.pdf" ) ).thenReturn(
      file );
    when( PentahoSystem.get( IUnifiedRepository.class ).getFile( "/test/report(1).pdf" ) ).thenReturn(
      file );
    final FakeLocation fakeLocation = new FakeLocation();
    final IFixedSizeStreamingContent content = mock( IFixedSizeStreamingContent.class );
    final IAsyncReportExecution reportExecution = mock( IAsyncReportExecution.class );
    final IAsyncReportState state = mock( IAsyncReportState.class );
    when( state.getMimeType() ).thenReturn( "application/pdf" );
    when( state.getPath() ).thenReturn( "report.prpt" );
    when( reportExecution.getState() ).thenReturn( state );
    final NullInputStream inputStream = new NullInputStream( 100 );
    when( content.getStream() ).thenReturn( inputStream );
    final ReportContentRepository contentRepository = mock( ReportContentRepository.class );
    when( contentRepository.getRoot() ).thenReturn( fakeLocation );
    final WriteToJcrTask toJcrTask = new WriteToJcrTask( reportExecution, inputStream ) {
      @Override protected ReportContentRepository getReportContentRepository( final RepositoryFile outputFolder ) {
        return contentRepository;
      }
    };
    assertNotNull( toJcrTask.call() );
    assertTrue( fakeLocation.exists( "report.pdf" ) );
    assertNotNull( toJcrTask.call() );
    assertTrue( fakeLocation.exists( "report.pdf" ) );
    assertTrue( fakeLocation.exists( "report(1).pdf" ) );
  }

  @Test
  public void testFail() throws Exception {
    final FakeLocation fakeLocation = new FakeLocation();
    final IFixedSizeStreamingContent content = mock( IFixedSizeStreamingContent.class );
    final IAsyncReportExecution reportExecution = mock( IAsyncReportExecution.class );
    final IAsyncReportState state = mock( IAsyncReportState.class );
    when( state.getMimeType() ).thenReturn( "application/pdf" );
    when( state.getPath() ).thenReturn( "report.prpt" );
    when( reportExecution.getState() ).thenReturn( state );
    final InputStream inputStream = mock( InputStream.class );
    when( inputStream.read( any() ) ).thenThrow( new IOException( "Test" ) );
    when( content.getStream() ).thenReturn( inputStream );
    final ReportContentRepository contentRepository = mock( ReportContentRepository.class );
    when( contentRepository.getRoot() ).thenReturn( fakeLocation );
    final WriteToJcrTask toJcrTask = new WriteToJcrTask( reportExecution, inputStream ) {
      @Override protected ReportContentRepository getReportContentRepository( final RepositoryFile outputFolder ) {
        return contentRepository;
      }
    };
    assertNull( toJcrTask.call() );
  }


  @Test
  public void testConcurrentSave() throws Exception {

    final UUID uuid = UUID.randomUUID();
    final RepositoryFile file = mock( RepositoryFile.class );
    when( file.getId() ).thenReturn( uuid.toString() );
    when( PentahoSystem.get( IUnifiedRepository.class ).getFile( startsWith( "/test" ) ) ).thenReturn(
      file );

    final CountDownLatch latch1 = new CountDownLatch( 1 );

    final FakeLocation fakeLocation = new FakeLocation( latch1 );


    final IAsyncReportExecution reportExecution = mock( IAsyncReportExecution.class );
    final IAsyncReportState state = mock( IAsyncReportState.class );
    when( state.getMimeType() ).thenReturn( "application/pdf" );
    when( state.getPath() ).thenReturn( "report.prpt" );
    when( reportExecution.getState() ).thenReturn( state );

    final ReportContentRepository contentRepository = mock( ReportContentRepository.class );

    when( contentRepository.getRoot() ).thenReturn( fakeLocation );


    final WriteToJcrTask toJcrTask = new WriteToJcrTask( reportExecution, new InputStream() {
      @Override public int read() throws IOException {
        try {
          Thread.sleep( 100 );
        } catch ( final InterruptedException e ) {
          e.printStackTrace();
        }
        return -1;
      }
    } ) {
      @Override protected ReportContentRepository getReportContentRepository( final RepositoryFile outputFolder ) {
        return contentRepository;
      }
    };

    final ExecutorService executorService = Executors.newFixedThreadPool( 10 );

    final List<Future<Serializable>> results = new ArrayList<>();

    for ( int i = 0; i < 10; i++ ) {
      results.add( executorService.submit( toJcrTask ) );
    }


    latch1.countDown();

    for ( final Future<Serializable> res : results ) {
      assertNotNull( res.get() );
    }


  }


  private class FakeLocation implements ContentLocation {

    private CountDownLatch latch;


    public FakeLocation() {
    }

    public FakeLocation( final CountDownLatch firstLatch ) {
      this.latch = firstLatch;
    }

    private Set<String> files = new ConcurrentHashSet<>();

    @Override public ContentEntity[] listContents() throws ContentIOException {
      throw new UnsupportedOperationException();
    }

    @Override public ContentEntity getEntry( final String s ) throws ContentIOException {
      throw new UnsupportedOperationException();
    }

    @Override public ContentItem createItem( final String s ) throws ContentCreationException {
      try {
        if ( latch != null ) {
          latch.countDown();
          latch.await();
        }
        Thread.sleep( 100 );
      } catch ( final InterruptedException e ) {
        e.printStackTrace();
      }
      if ( exists( s ) ) {
        throw new ContentCreationException();
      } else {
        files.add( s );
        final ContentItem mock = mock( ContentItem.class );
        try {
          when( mock.getOutputStream() ).thenReturn( new org.apache.commons.io.output.NullOutputStream() );
        } catch ( ContentIOException | IOException e ) {
          e.printStackTrace();
        }
        return mock;
      }

    }

    @Override public ContentLocation createLocation( final String s ) throws ContentCreationException {
      throw new UnsupportedOperationException();
    }

    @Override public boolean exists( final String s ) {
      return files.contains( s );
    }

    @Override public String getName() {
      throw new UnsupportedOperationException();
    }

    @Override public Object getContentId() {
      throw new UnsupportedOperationException();
    }

    @Override public Object getAttribute( final String s, final String s1 ) {
      return null;
    }

    @Override public boolean setAttribute( final String s, final String s1, final Object o ) {
      throw new UnsupportedOperationException();
    }

    @Override public ContentLocation getParent() {
      throw new UnsupportedOperationException();
    }

    @Override public Repository getRepository() {
      throw new UnsupportedOperationException();
    }

    @Override public boolean delete() {
      throw new UnsupportedOperationException();
    }
  }

}
