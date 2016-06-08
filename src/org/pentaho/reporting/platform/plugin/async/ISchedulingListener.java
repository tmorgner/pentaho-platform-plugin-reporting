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

import java.io.Serializable;

/**
 * Listener for report scheduling
 */
public interface ISchedulingListener {

  /**
   * Is executed when report is successfully scheduled
   *
   * @param key  report job key
   * @param fileId report file id
   * @return true if it executed ( even if it didn't succeed), false if didn't executed. This is important for proper
   * memory cleanup.
   */
  boolean onSchedulingCompleted( PentahoAsyncExecutor.CompositeKey key, Serializable fileId );

}
