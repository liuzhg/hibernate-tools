package org.hibernate.tool.api.metadata;

import java.util.Properties;

import org.hibernate.boot.Metadata;

public interface MetadataDescriptor {
	
	// Metadata createMetadata();

	Metadata getMetadata();

	Properties getProperties();

}
