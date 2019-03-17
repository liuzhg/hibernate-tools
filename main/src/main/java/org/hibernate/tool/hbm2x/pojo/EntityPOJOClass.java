package org.hibernate.tool.hbm2x.pojo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

import org.hibernate.boot.Metadata;
import org.hibernate.id.PersistentIdentifierGenerator;
import org.hibernate.id.enhanced.TableGenerator;
import org.hibernate.internal.util.StringHelper;
import org.hibernate.internal.util.collections.JoinedIterator;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.Formula;
import org.hibernate.mapping.KeyValue;
import org.hibernate.mapping.ManyToOne;
import org.hibernate.mapping.OneToMany;
import org.hibernate.mapping.OneToOne;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.RootClass;
import org.hibernate.mapping.Selectable;
import org.hibernate.mapping.SimpleValue;
import org.hibernate.mapping.Subclass;
import org.hibernate.mapping.Table;
import org.hibernate.mapping.ToOne;
import org.hibernate.mapping.UniqueKey;
import org.hibernate.mapping.Value;
import org.hibernate.tool.hbm2x.Cfg2JavaTool;
import org.hibernate.type.ForeignKeyDirection;

public class EntityPOJOClass extends BasicPOJOClass {

	private PersistentClass clazz;

	public EntityPOJOClass(PersistentClass clazz, Cfg2JavaTool cfg) {
		super(clazz, cfg);
		this.clazz = clazz;
		init();
	}

	protected String getMappedClassName() {
		return clazz.getClassName();
	}

	/**
	 * @return whatever the class (or interface) extends (null if it does not extend anything)
	 */
	public String getExtends() {
		String extendz = "";

		if ( isInterface() ) {
			if ( clazz.getSuperclass() != null ) {
				extendz = clazz.getSuperclass().getClassName();
			}
			if ( clazz.getMetaAttribute( EXTENDS ) != null ) {
				if ( !"".equals( extendz ) ) {
					extendz += ",";
				}
				extendz += getMetaAsString( EXTENDS, "," );
			}
		}
		else if ( clazz.getSuperclass() != null ) {
			if ( c2j.getPOJOClass(clazz.getSuperclass()).isInterface() ) {
				// class cannot extend it's superclass because the superclass is marked as an interface
			}
			else {
				extendz = clazz.getSuperclass().getClassName();
			}
		}
		else if ( clazz.getMetaAttribute( EXTENDS ) != null ) {
			extendz = getMetaAsString( EXTENDS, "," );
		}

		return "".equals( extendz ) ? null : extendz;
	}


	@SuppressWarnings("unchecked")
	public String getImplements() {
		List<String> interfaces = new ArrayList<String>();

		//			implement proxy, but NOT if the proxy is the class it self!
		if ( clazz.getProxyInterfaceName() != null && ( !clazz.getProxyInterfaceName().equals( clazz.getClassName() ) ) ) {
			interfaces.add( clazz.getProxyInterfaceName() );
		}

		if ( !isInterface() ) {
			if ( clazz.getSuperclass() != null && c2j.getPOJOClass(clazz.getSuperclass()).isInterface() ) {
				interfaces.add( clazz.getSuperclass().getClassName() );
			}
			if ( clazz.getMetaAttribute( IMPLEMENTS ) != null ) {
				interfaces.addAll( clazz.getMetaAttribute( IMPLEMENTS ).getValues() );
			}
			interfaces.add( Serializable.class.getName() ); // TODO: is this "nice" ? shouldn't it be a user choice ?
		}
		else {
			// interfaces can't implement suff
		}


		if ( interfaces.size() > 0 ) {
			StringBuffer sbuf = new StringBuffer();
			for ( Iterator<String> iter = interfaces.iterator(); iter.hasNext() ; ) {
				//sbuf.append(JavaTool.shortenType(iter.next().toString(), pc.getImports() ) );
				sbuf.append( iter.next() );
				if ( iter.hasNext() ) sbuf.append( "," );
			}
			return sbuf.toString();
		}
		else {
			return null;
		}
	}

	public Iterator<Property> getAllPropertiesIterator() {
		return getAllPropertiesIterator(clazz);
	}


	@SuppressWarnings("unchecked")
	public Iterator<Property> getAllPropertiesIterator(PersistentClass pc) {
		List<Property> properties = new ArrayList<Property>();
		List<Iterator<Property>> iterators = new ArrayList<Iterator<Property>>();
		if ( pc.getSuperclass() == null ) {
			// only include identifier for the root class.
			if ( pc.hasIdentifierProperty() ) {
				properties.add( pc.getIdentifierProperty() );
			}
			else if ( pc.hasEmbeddedIdentifier() ) {
				Component embeddedComponent = (Component) pc.getIdentifier();
				iterators.add( embeddedComponent.getPropertyIterator() );
			}
			/*if(clazz.isVersioned() ) { // version is already in property set
				properties.add(clazz.getVersion() );
			}*/
		}


		//		iterators.add( pc.getPropertyIterator() );
		// Need to skip <properties> element which are defined via "embedded" components
		// Best if we could return an intelligent iterator, but for now we just iterate explicitly.
		Iterator<Property> pit = pc.getPropertyIterator();
		while(pit.hasNext())
		{
			Property element = (Property) pit.next();
			if ( element.getValue() instanceof Component
					&& element.getPropertyAccessorName().equals( "embedded" )) {
				Component component = (Component) element.getValue();
				// need to "explode" property to get proper sequence in java code.
				Iterator<Property> embeddedProperty = component.getPropertyIterator();
				while(embeddedProperty.hasNext()) {
					properties.add(embeddedProperty.next());
				}
			} else {
				properties.add(element);
			}
		}

		iterators.add( properties.iterator() );

		Iterator<Property>[] it = (Iterator<Property>[]) iterators.toArray( new Iterator[iterators.size()] );
		return new SkipBackRefPropertyIterator( new JoinedIterator<Property>( it ) );
	}

	public boolean isComponent() {
		return false;
	}


	public boolean hasIdentifierProperty() {
		return clazz.hasIdentifierProperty() && clazz instanceof RootClass;
	}

	public Property getIdentifierProperty() {
		return clazz.getIdentifierProperty();
	}

	public String generateAnnTableUniqueConstraint() {
		if ( ! ( clazz instanceof Subclass ) ) {
			Table table = clazz.getTable();
			return generateAnnTableUniqueConstraint( table );
		}
		return "";
	}


	public String generateAnnIdGenerator() {
		KeyValue identifier = clazz.getIdentifier();
		String strategy = null;
		Properties properties = null;
		StringBuffer wholeString = new StringBuffer( "    " );
		if ( identifier instanceof Component ) {

			wholeString.append( AnnotationBuilder.createAnnotation( importType("javax.persistence.EmbeddedId") ).getResult());
		}
		else if ( identifier instanceof SimpleValue ) {
			SimpleValue simpleValue = (SimpleValue) identifier;
			strategy = simpleValue.getIdentifierGeneratorStrategy();
			properties = c2j.getFilteredIdentifierGeneratorProperties(simpleValue);
			StringBuffer idResult = new StringBuffer();
			AnnotationBuilder builder = AnnotationBuilder.createAnnotation( importType("javax.persistence.Id") );
			idResult.append(builder.getResult());
			idResult.append(" ");

			boolean isGenericGenerator = false; //TODO: how to handle generic now??
			if ( !"assigned".equals( strategy ) ) {

				if ( !"native".equals( strategy ) ) {
					if ( "identity".equals( strategy ) ) {
						builder.resetAnnotation( importType("javax.persistence.GeneratedValue") );
						builder.addAttribute( "strategy", staticImport("javax.persistence.GenerationType", "IDENTITY" ) );
						idResult.append(builder.getResult());
					}
					else if ( "sequence".equals( strategy ) ) {
						builder.resetAnnotation( importType("javax.persistence.GeneratedValue") )
							.addAttribute( "strategy", staticImport("javax.persistence.GenerationType", "SEQUENCE" ) )
						    .addQuotedAttribute( "generator", clazz.getClassName()+"IdGenerator" );
						idResult.append(builder.getResult());

						builder.resetAnnotation( importType("javax.persistence.SequenceGenerator") )
							.addQuotedAttribute( "name", clazz.getClassName()+"IdGenerator" ) 
							.addQuotedAttribute( "sequenceName", properties.getProperty(  org.hibernate.id.enhanced.SequenceStyleGenerator.SEQUENCE_PARAM, null ) );
							//	TODO HA does not support initialValue and allocationSize
						wholeString.append( builder.getResult() );
					}
					else if ( TableGenerator.class.getName().equals( strategy ) ) {
						builder.resetAnnotation( importType("javax.persistence.GeneratedValue") )
						.addAttribute( "strategy", staticImport("javax.persistence.GenerationType", "TABLE" ) )
					    .addQuotedAttribute( "generator", clazz.getClassName()+"IdGenerator" );
						idResult.append(builder.getResult());
						buildAnnTableGenerator( wholeString, properties );
					}
					else {
						isGenericGenerator = true;
						builder.resetAnnotation( importType("javax.persistence.GeneratedValue") );
						builder.addQuotedAttribute( "generator", clazz.getClassName()+"IdGenerator" );
						idResult.append(builder.getResult());
					}
				} else {
					builder.resetAnnotation( importType("javax.persistence.GeneratedValue") );
					idResult.append(builder.getResult());
				}
			}
			if ( isGenericGenerator ) {
				builder.resetAnnotation( importType("org.hibernate.annotations.GenericGenerator") )
					.addQuotedAttribute( "name", clazz.getClassName()+"IdGenerator" )
					.addQuotedAttribute( "strategy", strategy);

				List<AnnotationBuilder> params = new ArrayList<AnnotationBuilder>();
				//wholeString.append( "parameters = {  " );
				if ( properties != null ) {
					Enumeration<?> propNames = properties.propertyNames();
					while ( propNames.hasMoreElements() ) {

						String propertyName = (String) propNames.nextElement();
						AnnotationBuilder parameter = AnnotationBuilder.createAnnotation( importType("org.hibernate.annotations.Parameter") )
									.addQuotedAttribute( "name", propertyName )
									.addQuotedAttribute( "value", properties.getProperty( propertyName ) );
						params.add( parameter );
					}
				}
				builder.addAttributes( "parameters", params.iterator() );
				wholeString.append(builder.getResult());
			}
			wholeString.append( idResult );
		}
		return wholeString.toString();
	}

	private void buildAnnTableGenerator(StringBuffer wholeString, Properties properties) {

		AnnotationBuilder builder = AnnotationBuilder.createAnnotation( importType("javax.persistence.TableGenerator") );
		builder.addQuotedAttribute( "name", clazz.getClassName()+"IdGenerator" );
		builder.addQuotedAttribute( "table", properties.getProperty( "generatorTableName", "hibernate_sequences" ) );
		if ( ! isPropertyDefault( PersistentIdentifierGenerator.CATALOG, properties ) ) {
			builder.addQuotedAttribute( "catalog", properties.getProperty( PersistentIdentifierGenerator.CATALOG, "") );
		}
		if ( ! isPropertyDefault( PersistentIdentifierGenerator.SCHEMA, properties ) ) {
			builder.addQuotedAttribute( "schema", properties.getProperty( PersistentIdentifierGenerator.SCHEMA, "") );
		}
		if (! isPropertyDefault( TableGenerator.SEGMENT_VALUE_PARAM, properties ) ) {
			builder.addQuotedAttribute( "pkColumnValue", properties.getProperty( TableGenerator.SEGMENT_VALUE_PARAM, "") );
		}
		if ( ! isPropertyDefault( TableGenerator.INCREMENT_PARAM, properties, "50" ) ) {
			builder.addAttribute( "allocationSize", properties.getProperty( TableGenerator.INCREMENT_PARAM, "50" ) );
		}
		if (! isPropertyDefault( TableGenerator.SEGMENT_COLUMN_PARAM, properties ) ) {
			builder.addQuotedAttribute( "pkColumnName", properties.getProperty( TableGenerator.SEGMENT_COLUMN_PARAM, "") );
		}
		if (! isPropertyDefault( TableGenerator.VALUE_COLUMN_PARAM, properties ) ) {
			builder.addQuotedAttribute( "valueColumnName", properties.getProperty( TableGenerator.VALUE_COLUMN_PARAM, "") );
		}
		wholeString.append( builder.getResult() + "\n    " );
	}

	private boolean isPropertyDefault(String property, Properties properties) {
		return StringHelper.isEmpty( properties.getProperty( property ) );
	}

	private boolean isPropertyDefault(String property, Properties properties, String defaultValue) {
		String propertyValue = properties.getProperty( property );
		return propertyValue != null && propertyValue.equals( defaultValue );
	}

	public Object getDecoratedObject() {
		return clazz;
	}

	public boolean isSubclass() {
		return clazz.getSuperclass()!=null;
	}

	public List<Property> getPropertyClosureForFullConstructor() {
		return getPropertyClosureForFullConstructor(clazz);
	}

	protected List<Property> getPropertyClosureForFullConstructor(PersistentClass pc) {
		List<Property> l = new ArrayList<Property>(getPropertyClosureForSuperclassFullConstructor( pc ));
		l.addAll(getPropertiesForFullConstructor( pc ));
		return l;
	}

	public List<Property> getPropertiesForFullConstructor() {
		return getPropertiesForFullConstructor(clazz);
	}

	protected List<Property> getPropertiesForFullConstructor(PersistentClass pc) {
		List<Property> result = new ArrayList<Property>();

		for ( Iterator<Property> myFields = getAllPropertiesIterator(pc); myFields.hasNext() ; ) {
			Property field = (Property) myFields.next();
			// TODO: if(!field.isGenerated() ) ) {
			if(field.equals(pc.getIdentifierProperty()) && !isAssignedIdentifier(pc, field)) {
				continue; // dont add non assigned identifiers
			} else if(field.equals(pc.getVersion())) {
				continue; // version prop
			} else if(field.isBackRef()) {
				continue;
			} else if(isFormula(field)) {
				continue;
			} else {
				result.add( field );
			}
		}

		return result;
	}

	private boolean isFormula(Property field) {
		Value value = field.getValue();
		boolean foundFormula = false;

		if(value!=null && value.getColumnSpan()>0) {
			Iterator<Selectable> columnIterator = value.getColumnIterator();
			while ( columnIterator.hasNext() ) {
				Selectable element = columnIterator.next();
				if(!(element instanceof Formula)) {
					return false;
				} else {
					foundFormula = true;
				}
			}
		} else {
			return false;
		}
		return foundFormula;
	}

	public List<Property> getPropertyClosureForSuperclassFullConstructor() {
		return getPropertyClosureForSuperclassFullConstructor(clazz);
	}

	public List<Property> getPropertyClosureForSuperclassFullConstructor(PersistentClass pc) {
		List<Property> result = new ArrayList<Property>();
		if ( pc.getSuperclass() != null ) {
			// The correct sequence is vital here, as the subclass should be
			// able to invoke the fullconstructor based on the sequence returned
			// by this method!
			result.addAll( getPropertyClosureForSuperclassFullConstructor( pc.getSuperclass() ) );
			result.addAll( getPropertiesForFullConstructor( pc.getSuperclass() ) );
		}

		return result;
	}


	public List<Property> getPropertyClosureForMinimalConstructor() {
		return getPropertyClosureForMinimalConstructor(clazz);
	}

	protected List<Property> getPropertyClosureForMinimalConstructor(PersistentClass pc) {
		List<Property> l = new ArrayList<Property>(getPropertyClosureForSuperclassMinConstructor( pc ));
		l.addAll(getPropertiesForMinimalConstructor( pc ));
		return l;
	}

	public List<Property> getPropertiesForMinimalConstructor() {
		return getPropertiesForMinimalConstructor(clazz);
	}

	protected List<Property> getPropertiesForMinimalConstructor(PersistentClass pc) {
		List<Property> result = new ArrayList<Property>();

		for ( Iterator<Property> myFields = getAllPropertiesIterator(pc); myFields.hasNext() ; ) {
			Property property = (Property) myFields.next();
			if(property.equals(pc.getIdentifierProperty())) {
				if(isAssignedIdentifier(pc, property)) {
					result.add(property);
				} else {
					continue;
				}
			} else if (property.equals(pc.getVersion())) {
				continue; // the version property should not be in the result.
			} else if( isRequiredInConstructor(property) ) {
				result.add(property);
			}
		}

		return result;
	}

	protected boolean isAssignedIdentifier(PersistentClass pc, Property property) {
		if(property.equals(pc.getIdentifierProperty())) {
			if(property.getValue().isSimpleValue()) {
				SimpleValue sv = (SimpleValue) property.getValue();
				if("assigned".equals(sv.getIdentifierGeneratorStrategy())) {
					return true;
				}
			}
		}
		return false;
	}

	public List<Property> getPropertyClosureForSuperclassMinimalConstructor() {
		return getPropertyClosureForSuperclassMinConstructor(clazz);
	}

	protected List<Property> getPropertyClosureForSuperclassMinConstructor(PersistentClass pc) {
		List<Property> result = new ArrayList<Property>();
		if ( pc.getSuperclass() != null ) {
			// The correct sequence is vital here, as the subclass should be
			// able to invoke the fullconstructor based on the sequence returned
			// by this method!
			result.addAll( getPropertyClosureForSuperclassMinConstructor( pc.getSuperclass() ) );
			result.addAll( getPropertiesForMinimalConstructor( pc.getSuperclass() ) );
		}

		return result;
	}

	public POJOClass getSuperClass(){
		if (!isSubclass())
			return null;
		return new EntityPOJOClass(clazz.getSuperclass(),c2j);
	}


	public String toString() {
		return "Entity: " + (clazz==null?"<none>":clazz.getEntityName());
	}

	public boolean hasVersionProperty() {
		return clazz.isVersioned() && clazz instanceof RootClass;
	}

	/*
	 * @see org.hibernate.tool.hbm2x.pojo.POJOClass#getVersionProperty()
	 */
	public Property getVersionProperty()
	{
		return clazz.getVersion();
	}

}
