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

package com.bc.ceres.core.runtime;

import com.bc.ceres.core.CoreException;

/**
 * A configurable extension.
 * This interface may be implemented by client supplied extensions.</p>
 *
 * @see ConfigurationElement#createExecutableExtension(Class<T>)
 */
public interface ConfigurableExtension {

    /**
     * Configures this extension with the supplied configuration data.
     *
     * @param config The configuration data.
     *
     * @throws CoreException if an error occurred during configuration.
     */
    void configure(ConfigurationElement config) throws CoreException;
}
