/*
 * Copyright (C) 2010 Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package org.esa.beam.framework.ui.tool;

import java.awt.Component;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.EventObject;

/**
 * A special event type which is fired by tool-aware components to inform a tool about user inputs via the mouse or the
 * keyboard.
 *
 * @author Norman Fomferra
 * @version $Revision$  $Date$
 * @deprecated since BEAM 4.7, no replacement
 */
@Deprecated
public class ToolInputEvent extends EventObject {

    private static final long serialVersionUID = 2919123041717315375L;

    private final MouseEvent _mouseEvent;
    private final KeyEvent _keyEvent;
    private final int _pixelX;
    private final int _pixelY;
    private final boolean _pixelPosValid;

    /**
     * Constructs a new tool input event generated by a mouse event.
     */
    public ToolInputEvent(Component source, MouseEvent mouseEvent, int pixelX, int pixelY, boolean pixelPosValid) {
        super(source);
        _mouseEvent = mouseEvent;
        _keyEvent = null;
        _pixelX = pixelX;
        _pixelY = pixelY;
        _pixelPosValid = pixelPosValid;
    }

    /**
     * Constructs a new tool input event generated by a key event.
     */
    public ToolInputEvent(Component source, KeyEvent keyEvent, int pixelX, int pixelY, boolean pixelPosValid) {
        super(source);
        _mouseEvent = null;
        _keyEvent = keyEvent;
        _pixelX = pixelX;
        _pixelY = pixelY;
        _pixelPosValid = pixelPosValid;
    }

    /**
     * Returns the source component.
     */
    public Component getComponent() {
        return (Component) getSource();
    }

    /**
     * Returns the key event or <code>null</code> if this event was generated by a mouse event.
     *
     * @see #getMouseEvent
     */
    public KeyEvent getKeyEvent() {
        return _keyEvent;
    }

    /**
     * Returns the mouse event or <code>null</code> if this event was generated by a key event.
     *
     * @see #getKeyEvent
     */
    public MouseEvent getMouseEvent() {
        return _mouseEvent;
    }

    /**
     * Gets the current pixel x co-ordinate of the image.
     */
    public int getPixelX() {
        return _pixelX;
    }

    /**
     * Gets the current pixel y co-ordinate of the image.
     */
    public int getPixelY() {
        return _pixelY;
    }

    /**
     * Gets the current pixel co-ordinate of the image as point.
     */
    public Point getPixelPos() {
        return new Point(_pixelX, _pixelY);
    }

    /**
     * Returns whether or not the pixel position is valid.
     */
    public boolean isPixelPosValid() {
        return _pixelPosValid;
    }

    /**
     * Returns a String representation of this EventObject.
     *
     * @return A a String representation of this EventObject.
     */
    @Override
    public String toString() {
        return getClass().getName()
               + "[source=" + source
               + ",mouseEvent=" + _mouseEvent
               + ",keyEvent=" + _keyEvent
               + ",pixelX=" + _pixelX
               + ",pixelY=" + _pixelY
               + "]";
    }
}
