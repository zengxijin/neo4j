/*
 * Copyright (c) 2002-2016 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.server.security.enterprise.auth;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.logging.Log;
import org.neo4j.logging.LogProvider;
import org.neo4j.server.security.auth.exception.FormatException;

/**
 * Stores role data. In memory, but backed by persistent storage so changes to this repository will survive
 * JVM restarts and crashes.
 */
public class FileRoleRepository extends AbstractRoleRepository
{
    private final File roleFile;
    private final Log log;
    private final RoleSerialization serialization = new RoleSerialization();
    private final FileSystemAbstraction fileSystem;

    private long lastLoaded;

    public FileRoleRepository( FileSystemAbstraction fileSystem, File file, LogProvider logProvider )
    {
        this.roleFile = file;
        this.log = logProvider.getLog( getClass() );
        this.fileSystem = fileSystem;
    }

    @Override
    public void start() throws Throwable
    {
        clear();
        loadRoles();
    }

    @Override
    protected void loadRoles() throws IOException
    {
        if ( fileSystem.fileExists( roleFile ) )
        {
            List<RoleRecord> loadedRoles;
            try
            {
                lastLoaded = fileSystem.lastModifiedTime( roleFile );
                loadedRoles = serialization.loadRecordsFromFile( fileSystem, roleFile );
            }
            catch ( FormatException e )
            {
                log.error( "Failed to read role file \"%s\" (%s)", roleFile.getAbsolutePath(), e.getMessage() );
                throw new IllegalStateException( "Failed to read role file '" + roleFile + "'." );
            }

            clear();
            roles = loadedRoles;
            for ( RoleRecord role : roles )
            {
                rolesByName.put( role.name(), role );

                populateUserMap( role );
            }
        }
    }

    @Override
    protected void saveRoles() throws IOException
    {
        serialization.saveRecordsToFile( fileSystem, roleFile, roles );
    }

    @Override
    public void reloadIfNeeded() throws IOException
    {
        if ( lastLoaded < fileSystem.lastModifiedTime( roleFile ) )
        {
            loadRoles();
        }
    }
}
