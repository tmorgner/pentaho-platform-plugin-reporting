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

import org.pentaho.platform.api.engine.IPentahoSession;

import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.Future;

public interface IPentahoAsyncExecutor {
  UUID addTask( IAsyncReportExecution<InputStream> task, IPentahoSession session );

  Future<InputStream> getFuture( UUID id, IPentahoSession session );

  IAsyncReportState getReportState( UUID id, IPentahoSession session );

  void cleanFuture( UUID id, IPentahoSession session );

  void requestPage( UUID id, IPentahoSession session, int page );

  void schedule( UUID uuid, IPentahoSession session );
}
