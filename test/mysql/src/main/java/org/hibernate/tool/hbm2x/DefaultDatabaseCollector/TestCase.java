/*******************************************************************************
 * Copyright (c) 2010 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributor:
 *     Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.hibernate.tool.hbm2x.DefaultDatabaseCollector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Environment;
import org.hibernate.cfg.JDBCReaderFactory;
import org.hibernate.cfg.MetaDataDialectFactory;
import org.hibernate.cfg.reveng.DatabaseCollector;
import org.hibernate.cfg.reveng.DefaultDatabaseCollector;
import org.hibernate.cfg.reveng.DefaultReverseEngineeringStrategy;
import org.hibernate.cfg.reveng.JDBCReader;
import org.hibernate.cfg.reveng.OverrideRepository;
import org.hibernate.cfg.reveng.ReverseEngineeringStrategy;
import org.hibernate.cfg.reveng.SchemaSelection;
import org.hibernate.cfg.reveng.TableIdentifier;
import org.hibernate.cfg.reveng.dialect.MetaDataDialect;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.mapping.Table;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.tool.api.metadata.MetadataDescriptorFactory;
import org.hibernate.tools.test.util.JdbcUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * @author Dmitry Geraskov
 * @author koen
 */
public class TestCase {
		
	@Before
	public void setUp() {
		JdbcUtil.createDatabase(this);
	}
	
	@After
	public void tearDown() {
		JdbcUtil.dropDatabase(this);
	}
	
	// TODO Reenable this test (HBX-1401) 
	@Ignore
	@Test
	public void testReadOnlySpecificSchema() {
		OverrideRepository or = new OverrideRepository();
		or.addSchemaSelection(new SchemaSelection(null, "cat.cat"));
		ReverseEngineeringStrategy res = or.getReverseEngineeringStrategy(new DefaultReverseEngineeringStrategy());
		Metadata metadata = MetadataDescriptorFactory
				.createJdbcDescriptor(res, null, true)
				.getMetadata();
		List<Table> tables = getTables(metadata);
		Assert.assertEquals(2,tables.size());
		Table catchild = (Table) tables.get(0);
		Table catmaster = (Table) tables.get(1);
		if(catchild.getName().equals("cat.master")) {
			catchild = (Table) tables.get(1);
			catmaster = (Table) tables.get(0);
		} 
		TableIdentifier masterid = TableIdentifier.create(catmaster);
		TableIdentifier childid = TableIdentifier.create(catchild);
		Assert.assertEquals(new TableIdentifier(null, "cat.cat", "cat.child"), childid);
		Assert.assertEquals(new TableIdentifier(null, "cat.cat", "cat.master"), masterid);
	}
	
	@Test
	public void testNeedQuote() {
		Properties properties = Environment.getProperties();
		StandardServiceRegistryBuilder ssrb = new StandardServiceRegistryBuilder();
		ssrb.applySettings(properties);
		ServiceRegistry serviceRegistry = ssrb.build();
		MetaDataDialect realMetaData = MetaDataDialectFactory.createMetaDataDialect( serviceRegistry.getService(JdbcServices.class).getDialect(), properties);
		Assert.assertTrue("The name must be quoted!", realMetaData.needQuote("cat.cat"));
		Assert.assertTrue("The name must be quoted!", realMetaData.needQuote("cat.child"));
		Assert.assertTrue("The name must be quoted!", realMetaData.needQuote("cat.master"));
	}
	
	/**
	 * There are 2 solutions:
	 * 1. DatabaseCollector#addTable()/getTable() should be called for not quoted parameters - I think it is preferable way.
	 * 2. DatabaseCollector#addTable()/getTable() should be called for quoted parameters - here users should
	 * use the same quotes as JDBCReader.
	 * Because of this there are 2 opposite methods(and they are both failed as addTable uses quoted names
	 * but getTable uses non-quoted names )
	 */
	// TODO Reenable this test (HBX-1401) 
	@Ignore
	@Test
	public void testQuotedNamesAndDefaultDatabaseCollector() {
		Properties properties = Environment.getProperties();
		StandardServiceRegistryBuilder builder = new StandardServiceRegistryBuilder();
		ServiceRegistry serviceRegistry = builder.build();	
		MetaDataDialect realMetaData = MetaDataDialectFactory.createMetaDataDialect( 
				serviceRegistry.getService(JdbcServices.class).getDialect(), 
				properties);
		JDBCReader reader = JDBCReaderFactory.newJDBCReader( 
				properties, new DefaultReverseEngineeringStrategy(), 
				realMetaData, serviceRegistry );
		DatabaseCollector dc = new DefaultDatabaseCollector(reader.getMetaDataDialect());
		reader.readDatabaseSchema( dc, null, "cat.cat" );
		String defaultCatalog = properties.getProperty(AvailableSettings.DEFAULT_CATALOG);
		Assert.assertNotNull("The table should be found", dc.getTable("cat.cat", defaultCatalog, "cat.child"));
		Assert.assertNotNull("The table should be found", dc.getTable("cat.cat", defaultCatalog, "cat.master"));
		Assert.assertNull("Quoted names should not return the table", dc.getTable(quote("cat.cat"), defaultCatalog, quote("cat.child")));
		Assert.assertNull("Quoted names should not return the table", dc.getTable(quote("cat.cat"), defaultCatalog, quote("cat.master")));
		Assert.assertEquals("Foreign key 'masterref' was filtered!", 1, dc.getOneToManyCandidates().size());
	}
	
	private static String quote(String name) {
		return "\"" + name + "\"";
	}
	
	private List<Table> getTables(Metadata metadata) {
		List<Table> list = new ArrayList<Table>();
		Iterator<Table> iter = metadata.collectTableMappings().iterator();
		while(iter.hasNext()) {
			Table element = iter.next();
			list.add(element);
		}
		return list;
	}
	
}
