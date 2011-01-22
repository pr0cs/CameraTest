package net.pixelsystems.test.net.pixelsystems.event;

/**
 * Copyright 1/21/11 William May
 * This file is part of CameraTest.
 * CameraTest is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * CameraTest is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Foobar.
 * If not, see http://www.gnu.org/licenses/.
 *
 */

/**
 * Created by IntelliJ IDEA.
 * User: hbl4144
 * Date: 1/21/11
 * Time: 11:07 AM
 * Simple listener interface that is triggered when a new image appears in the image table.
 */
public interface ImageEventListener {
    public void newImageAvailable(String path);
}
