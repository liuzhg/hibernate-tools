package org.hibernate.tool.internal.metadata;

import java.io.File;
import java.util.Properties;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.BootstrapServiceRegistry;
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.tool.api.metadata.MetadataDescriptor;

public class NativeMetadataDescriptor implements MetadataDescriptor {
	
	private Properties properties = new Properties();
	private MetadataSources metadataSources = null;
	private StandardServiceRegistryBuilder ssrb = null;
		
	public NativeMetadataDescriptor(
			File cfgXmlFile, 
			File[] mappingFiles, 
			Properties properties) {
		BootstrapServiceRegistry bsr = new BootstrapServiceRegistryBuilder().build();
		ssrb = new StandardServiceRegistryBuilder(bsr);
		if (cfgXmlFile != null) {
			ssrb.configure(cfgXmlFile);
		}
		if (properties != null) {
			this.properties.putAll(properties);
		}
		ssrb.applySettings(getProperties());
		metadataSources = new MetadataSources(bsr);
		if (mappingFiles != null) {
			for (File file : mappingFiles) {
				if (file.getName().endsWith(".jar")) {
					metadataSources.addJar(file); 
				} else {
					metadataSources.addFile(file);
				}
			}
		}
	}
	
	public Properties getProperties() {
		Properties result = new Properties();
		result.putAll(properties);
		return result;
	}
	
	public Metadata createMetadata() {
		return metadataSources.buildMetadata(ssrb.build());
	}

	private Metadata metadata;

	public Metadata getMetadata() {
		if (metadata == null) {
			metadata = createMetadata();
		}
		return metadata;
	}

}
