package org.hibernate.tool.internal.export.pojo;

import java.util.*;
import java.util.List;
import java.util.Map;

import org.hibernate.boot.Metadata;
import org.hibernate.cfg.reveng.ReverseEngineeringStrategyUtil;
import org.hibernate.internal.util.StringHelper;
import org.hibernate.mapping.*;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.Set;
import org.hibernate.mapping.SimpleValue;
import org.hibernate.mapping.Value;
import org.hibernate.tool.internal.export.common.DefaultValueVisitor;
import org.hibernate.tool.internal.util.NameConverter;
import org.hibernate.tool.hbm2x.Cfg2JavaTool;
import org.hibernate.tool.hbm2x.MetaAttributeConstants;
import org.hibernate.tool.hbm2x.MetaAttributeHelper;
import org.hibernate.tool.hbm2x.visitor.DefaultValueVisitor;
import org.hibernate.tuple.GenerationTiming;
import org.hibernate.type.ForeignKeyDirection;

/**
 * Abstract implementation of POJOClass. To be extended by ComponentPOJO and EntityPOJO
 * @author max
 * @author <a href="mailto:abhayani@jboss.org">Amit Bhayani</a>
 *
 */
abstract public class BasicPOJOClass implements POJOClass, MetaAttributeConstants {

	protected ImportContext importContext;
	protected MetaAttributable meta;
	protected final Cfg2JavaTool c2j;
	
	public BasicPOJOClass(MetaAttributable ma, Cfg2JavaTool c2j) {
		this.meta = ma;
		this.c2j = c2j;		
		
		if(this.meta==null) {
			throw new IllegalArgumentException("class Argument must be not null");
		}
		if(this.c2j==null) throw new IllegalArgumentException("c2j must be not null");
	}
	
	// called by subclasses
	protected void init() {
		importContext = new ImportContextImpl(getPackageName());
		
		MetaAttribute metaAttribute = meta.getMetaAttribute("extra-import");
		if(metaAttribute!=null) {
			Iterator<?> values = metaAttribute.getValues().iterator();
			while ( values.hasNext() ) {
				String element = (String) values.next();
				importContext.importType(element);				
			}
		}	
	}
	
	protected String getPackageDeclaration(String pkgName) {
		if (pkgName!=null && pkgName.trim().length()!=0 ) {
			return "package " + pkgName + ";";
		} 
		else {        
			return "// default package";
		}
	}

	public String getPackageDeclaration() {
		String pkgName = getPackageName();
		return getPackageDeclaration(pkgName);			
	}

	/** Return package name. Note: Does not handle inner classes */ 
	public String getPackageName() {
		String generatedClass = getGeneratedClassName();
		return StringHelper.qualifier(generatedClass.trim());
	}
	
	public String getShortName() {
		return qualifyInnerClass(StringHelper.unqualify(getMappedClassName()));
	}
	
	public String getQualifiedDeclarationName() {
		String generatedName = qualifyInnerClass(getGeneratedClassName());
		String qualifier = StringHelper.qualifier( getMappedClassName() );
		if ( "".equals( qualifier ) ) {
			return qualifier + "." + generatedName;
		}
		else {
			return generatedName;
		}
	}
	
	/**
	 * @return unqualified classname for this class (can be changed by meta attribute "generated-class")
	 */
	public String getDeclarationName() {
		return qualifyInnerClass(StringHelper.unqualify( getGeneratedClassName() ));
	}
	
	protected String getGeneratedClassName()
	{
		String generatedClass = getMetaAsString(MetaAttributeConstants.GENERATED_CLASS).trim();
		if(StringHelper.isEmpty(generatedClass) ) {
			generatedClass = getMappedClassName();
		}
		if(generatedClass==null) return ""; // will occur for <dynamic-component>
		return generatedClass;
	}
	
	protected String qualifyInnerClass(String className)
	{
		return className.replace('$', '.');
	}
	
	protected abstract String getMappedClassName();

	public String getMetaAsString(String attribute) {
		MetaAttribute c = meta.getMetaAttribute( attribute );
		return MetaAttributeHelper.getMetaAsString( c );
	}

	public boolean hasMetaAttribute(String attribute) {
		return meta.getMetaAttribute( attribute ) != null;
	}

	public String getMetaAsString(String attribute, String seperator) {
		return MetaAttributeHelper.getMetaAsString( meta.getMetaAttribute( attribute ), seperator );
	}

	public boolean getMetaAsBool(String attribute) {
		return getMetaAsBool( attribute, false );
	}

	public boolean getMetaAsBool(String attribute, boolean defaultValue) {
		return MetaAttributeHelper.getMetaAsBool( meta.getMetaAttribute( attribute ), defaultValue );
	}

	public String getClassJavaDoc(String fallback, int indent) {
		MetaAttribute c = meta.getMetaAttribute( CLASS_DESCRIPTION );
		if ( c == null ) {
			return c2j.toJavaDoc( fallback, indent );
		}
		else {
			return c2j.toJavaDoc( getMetaAsString( CLASS_DESCRIPTION ), indent );
		}
	}
	
	public String getClassModifiers() {
		String classModifiers = null;

		// Get scope (backwards compatibility)
		if ( meta.getMetaAttribute( SCOPE_CLASS ) != null ) {
			classModifiers = getMetaAsString( SCOPE_CLASS ).trim();
		}

		// Get modifiers
		if ( meta.getMetaAttribute( CLASS_MODIFIER ) != null ) {
			classModifiers = getMetaAsString( CLASS_MODIFIER ).trim();
		}
		return classModifiers == null ? "public" : classModifiers;
	}

	public String getDeclarationType() {
		boolean isInterface = isInterface();
		if ( isInterface ) {
			return INTERFACE;
		}
		else {
			return "class";
		}
	}
	
	public boolean isInterface() {
		return getMetaAsBool( INTERFACE );
	}
	
	public String getExtendsDeclaration() {
		String extendz = getExtends();
		if ( extendz == null || extendz.trim().length() == 0 ) {
			return "";
		}
		else {
			return "extends " + extendz;
		}
	}

	public String getImplementsDeclaration() {
		String implementz = getImplements();
		if ( implementz == null || implementz.trim().length() == 0 ) {
			return "";
		}
		else {
			return "implements " + implementz;
		}
	}
	
	public String generateEquals(String thisName, String otherName, boolean useGenerics) {
		Iterator<Property> allPropertiesIterator = getEqualsHashCodePropertiesIterator();
		return generateEquals( thisName, otherName, allPropertiesIterator, useGenerics );
	}
	
	/** returns the properties that would be visible on this entity as a pojo. This does not return *all* properties since hibernate has certain properties that are only relevant in context of persistence. */ 
	public abstract Iterator<Property> getAllPropertiesIterator();

	protected String generateEquals(String thisName, String otherName, Iterator<Property> allPropertiesIterator, boolean useGenerics) {
		StringBuffer buf = new StringBuffer();
		while ( allPropertiesIterator.hasNext() ) {
			Property property = (Property) allPropertiesIterator.next();
				if ( buf.length() > 0 ) buf.append( "\n && " );
				String javaTypeName = c2j.getJavaTypeName( property, useGenerics, this );
				buf.append(
						internalgenerateEquals(
								javaTypeName, thisName + "." + getGetterSignature( property ) + "()",
								otherName + "." + getGetterSignature( property ) + "()")
				);			
		}

		if ( buf.length() == 0 ) {
			return "false";
		}
		else {
			return buf.toString();
		}
	}

	private boolean usePropertyInEquals(Property property) {
		boolean hasEqualsMetaAttribute = c2j.hasMetaAttribute(property, "use-in-equals");		
		boolean useInEquals = c2j.getMetaAsBool( property, "use-in-equals" );
		
		if(property.isNaturalIdentifier()) {
			if(hasEqualsMetaAttribute && !useInEquals) {
				return false;
			} else {
				return true;
			}
		} 
		
		return useInEquals;
	}

	private boolean useCompareTo(String javaTypeName) {
		// Fix for HBX-400
		if ("java.math.BigDecimal".equals(javaTypeName)) {
			return true;
		} else {
			return false;
		}
	} 


	private String internalgenerateEquals(String typeName, String lh, String rh) {
		if ( c2j.isPrimitive( typeName ) ) {
			return "(" + lh + "==" + rh + ")";
		}
		else {
			if(useCompareTo( typeName )) {
				return "( (" + lh + "==" + rh + ") || ( " + lh + "!=null && " + rh + "!=null && " + lh + ".compareTo(" + rh + ")==0 ) )";
			} else {
				if(typeName.endsWith("[]")) {
					return "( (" + lh + "==" + rh + ") || ( " + lh + "!=null && " + rh + "!=null && " + importType("java.util.Arrays") + ".equals(" + lh + ", " + rh + ") ) )";
				} else {
					return "( (" + lh + "==" + rh + ") || ( " + lh + "!=null && " + rh + "!=null && " + lh + ".equals(" + rh + ") ) )";
				}
			}

		}
	}

	public String getExtraClassCode() {
		return getMetaAsString( "class-code", "\n" );
	}
	
	private boolean needsEqualsHashCode(Iterator<?> iter) {
		while ( iter.hasNext() ) {
			Property element = (Property) iter.next();
			if ( usePropertyInEquals( element ) ) {
				return true;
			}
		}
		return false;
	}

	public boolean needsEqualsHashCode() {
		Iterator<Property> iter = getAllPropertiesIterator();
		return needsEqualsHashCode( iter );
	}

	public abstract String getExtends();
	
	public abstract String getImplements();

	
	public String importType(String fqcn) {
		return importContext.importType(fqcn);
	}
	
	public String generateImports() {
		return importContext.generateImports();
	}

	public String staticImport(String fqcn, String member) {
		return importContext.staticImport(fqcn, member);
	}
	
	public String generateBasicAnnotation(Property property) {
		StringBuffer annotations = new StringBuffer( "    " );
		if(property.getValue() instanceof SimpleValue) {
			if (hasVersionProperty())
				if (property.equals(getVersionProperty()))
						buildVersionAnnotation(annotations);
			String typeName = ((SimpleValue)property.getValue()).getTypeName();
			if("date".equals(typeName) || "java.sql.Date".equals(typeName)) {
				buildTemporalAnnotation( annotations, "DATE" );
			} else if ("timestamp".equals(typeName) || "java.sql.Timestamp".equals(typeName)) {
				buildTemporalAnnotation( annotations, "TIMESTAMP" );
			} else if ("time".equals(typeName) || "java.sql.Time".equals(typeName)) {
				buildTemporalAnnotation(annotations, "TIME");
			} //TODO: calendar etc. ?

						
		}
			
		return annotations.toString();
	}

	private StringBuffer buildTemporalAnnotation(StringBuffer annotations, String temporalTypeValue) {
		String temporal = importType("javax.persistence.Temporal");
		String temporalType = importType("javax.persistence.TemporalType");
		
		return annotations.append( "@" + temporal +"(" + temporalType + "." + temporalTypeValue + ")");
	}
	
	private StringBuffer buildVersionAnnotation(StringBuffer annotations) {
		String version = importType("javax.persistence.Version");
		
		return annotations.append( "@" + version );
	}
	
	public String generateAnnColumnAnnotation(Property property) {
		StringBuffer annotations = new StringBuffer( "    " );
		boolean insertable = property.isInsertable();
		boolean updatable = property.isUpdateable();
		if ( property.isComposite() ) {
			annotations.append( "@" + importType("javax.persistence.AttributeOverrides") +"( {" );
			Component component = (Component) property.getValue();
			Iterator<?> subElements = component.getPropertyIterator();
			buildRecursiveAttributeOverride( subElements, null, property, annotations );
			annotations.setLength( annotations.length() - 2 );
			annotations.append( " } )" );
		}
		else {
			if ( property.getColumnSpan() == 1 ) {
				Selectable selectable = (Selectable) property.getColumnIterator().next();
				buildColumnAnnotation( selectable, annotations, insertable, updatable );				
			}
			else {
				Iterator<?> columns = property.getColumnIterator();
				annotations.append("@").append( importType("org.hibernate.annotations.Columns") ).append("( { " );
				while ( columns.hasNext() ) {
					Selectable selectable = (Selectable) columns.next();
	
					if ( selectable.isFormula() ) {
						//TODO formula in multicolumns not supported by annotations
						//annotations.append("/* TODO formula in multicolumns not supported by annotations */");
					}
					else {
						annotations.append( "\n        " );
						buildColumnAnnotation( selectable, annotations, insertable, updatable );
						annotations.append( ", " );
					}
				}
				annotations.setLength( annotations.length() - 2 );
				annotations.append( " } )" );
			}
		}
		return annotations.toString();
	}

	private void buildRecursiveAttributeOverride(Iterator<?> subElements, String path, Property property, StringBuffer annotations) {
		while ( subElements.hasNext() ) {
			Property subProperty = (Property) subElements.next();
			if ( subProperty.isComposite() ) {
				if ( path != null ) {
					path = path + ".";
				}
				else {
					path = "";
				}
				path = path + subProperty.getName();
				Component component = (Component) subProperty.getValue();
				buildRecursiveAttributeOverride( component.getPropertyIterator(), path, subProperty, annotations );
			}
			else {
				Iterator<?> columns = subProperty.getColumnIterator();
				Selectable selectable = (Selectable) columns.next();
				if ( selectable.isFormula() ) {
					//TODO formula in multicolumns not supported by annotations
				}
				else {
					annotations.append( "\n        " ).append("@")
							.append( importType("javax.persistence.AttributeOverride") ).append("(name=\"" );
					if ( path != null ) {
						annotations.append( path ).append( "." );
					}
					annotations.append( subProperty.getName() ).append( "\"" )
							.append( ", column=" );
					buildColumnAnnotation(
							selectable, annotations, subProperty.isInsertable(), subProperty.isUpdateable()
					);
					annotations.append( " ), " );
				}
			}
		}
	}

	private void buildColumnAnnotation(Selectable selectable, StringBuffer annotations, boolean insertable, boolean updatable) {
		if ( selectable.isFormula() ) {
			annotations.append("@").append( importType("org.hibernate.annotations.Formula") )
					.append("(value=\"" ).append( selectable.getText() ).append( "\")" );
		}
		else {
			Column column = (Column) selectable;
			annotations.append( "@" + importType("javax.persistence.Column") + "(name=\"" ).append( column.getName() ).append( "\"" );
			
			appendCommonColumnInfo( annotations, column, insertable, updatable );
			
			if (column.getPrecision() != Column.DEFAULT_PRECISION) { // the default is actually 0 in spec
				annotations.append( ", precision=" ).append( column.getPrecision() );
			}
			if (column.getScale() != Column.DEFAULT_SCALE) { // default is actually 0 in spec
				annotations.append( ", scale=" ).append( column.getScale() );
			}
			else if (column.getLength() != 255){ 
				annotations.append( ", length=" ).append( column.getLength() );
			}
			
					
					
			
			//TODO support secondary table
			annotations.append( ")" );
		}
	}

	protected void appendCommonColumnInfo(StringBuffer annotations, Column column, boolean insertable, boolean updatable) {
		if(column.isUnique()) {
				annotations.append( ", unique=" ).append( column.isUnique() );
		}
		if(!column.isNullable()) {
				annotations.append( ", nullable=" ).append( column.isNullable() );
		}
		
		if(!insertable) {
				annotations.append( ", insertable=" ).append( insertable );
		}
		
		if(!updatable) {
				annotations.append( ", updatable=" ).append( updatable );
		}
		
		String sqlType = column.getSqlType();
		if ( StringHelper.isNotEmpty( sqlType ) ) {
			annotations.append( ", columnDefinition=\"" ).append( sqlType ).append( "\"" );
		}
				
	}


	public Iterator<Property> getToStringPropertiesIterator() {
		Iterator<Property> iter = getAllPropertiesIterator();
		return getToStringPropertiesIterator( iter );
	}

	private Iterator<Property> getToStringPropertiesIterator(Iterator<Property> iter) {
		List<Property> properties = new ArrayList<Property>();

		while ( iter.hasNext() ) {
			Property element = (Property) iter.next();
			if ( c2j.getMetaAsBool( element, "use-in-tostring" ) ) {
				properties.add( element );
			}
		}

		return properties.iterator();
	}

	public Iterator<Property> getEqualsHashCodePropertiesIterator() {
		Iterator<Property> iter = getAllPropertiesIterator();
		return getEqualsHashCodePropertiesIterator(iter);
	}

	private Iterator<Property> getEqualsHashCodePropertiesIterator(Iterator<Property> iter) {
		List<Property> properties = new ArrayList<Property>();

		while ( iter.hasNext() ) {
			Property element = (Property) iter.next();
			if ( usePropertyInEquals(element) ) {
				properties.add( element );
			}
		}

		return properties.iterator();
	}

	public boolean needsToString() {
		Iterator<Property> iter = getAllPropertiesIterator();
		return needsToString( iter );
	}
	
	private boolean needsToString(Iterator<Property> iter) {
		while ( iter.hasNext() ) {
			Property element = (Property) iter.next();
			if ( c2j.getMetaAsBool( element, "use-in-tostring" ) ) {
				return true;
			}
		}
		return false;
	}

	public boolean hasMetaAttribute(MetaAttributable pc, String attribute) {
		return pc.getMetaAttribute( attribute ) != null;
	}

	public boolean getMetaAttribAsBool(MetaAttributable pc, String attribute, boolean defaultValue) {
		return MetaAttributeHelper.getMetaAsBool( pc.getMetaAttribute( attribute ), defaultValue );
	}
	
	public boolean hasFieldJavaDoc(Property property) {
		return property.getMetaAttribute("field-description")!=null;
	}
	
	public String getFieldJavaDoc(Property property, int indent) {
		MetaAttribute c = property.getMetaAttribute( "field-description" );
		if ( c == null ) {
			return c2j.toJavaDoc( "", indent );
		}
		else {
			return c2j.toJavaDoc( c2j.getMetaAsString( property, "field-description" ), indent );
		}
	}
	
	public String getFieldDescription(Property property){
		MetaAttribute c = property.getMetaAttribute( "field-description" );
		if ( c == null ) {
			return "";
		}
		else {
			return c2j.getMetaAsString( property, "field-description" );
		}		
	}

	/**
	 * Method getGetterSignature.
	 *
	 * @return String
	 */
	public String getGetterSignature(Property p) {
		String prefix = c2j.getJavaTypeName( p, false).equals( "boolean" ) ? "is" : "get";
		return prefix + beanCapitalize( p.getName() );
	}

	/**
	 * @param p
	 * @return foo -> Foo, FOo -> FOo
	 */
	public String getPropertyName(Property p) {
		return beanCapitalize( p.getName() );
	}


	// get the "opposite" collectionnae for a property. Currently a "hack" that just uses the same naming algorithm as in reveng, will fail on more general models!
	public String getCollectionNameFor(Property property) {
		String str = getPropertyName(property);
		return NameConverter.simplePluralize(str);
	}
	
	
	/**
	 * foo -> Foo
	 * FOo -> FOo
	 */
	static public String beanCapitalize(String fieldname) {
		if ( fieldname == null || fieldname.length() == 0 ) {
			return fieldname;
		}

		if ( fieldname.length() > 1 && Character.isUpperCase( fieldname.charAt( 1 ) ) ) {
			return fieldname;
		}
		char chars[] = fieldname.toCharArray();
		chars[0] = Character.toUpperCase( chars[0] );
		return new String( chars );
	}


	public boolean isComponent(Property property) {
		Value value = property.getValue();
		if ( value != null && value instanceof Component ) {
			return true;
		}
		else {
			return false;
		}
	}

	public String generateHashCode(Property property, String result, String thisName, boolean jdk5) {
		StringBuffer buf = new StringBuffer();
		if ( c2j.getMetaAsBool( property, "use-in-equals" ) ) {
			String javaTypeName = c2j.getJavaTypeName( property, jdk5, this );
			boolean isPrimitive = c2j.isPrimitive( javaTypeName );
			if ( isPrimitive ) {
				buf.append( result )
				.append( " = 37 * " )
				.append( result )
				.append( " + " );
				String thisValue = thisName + "." + getGetterSignature( property ) + "()";
				if("char".equals(javaTypeName)||"int".equals(javaTypeName)||"short".equals(javaTypeName)||"byte".equals(javaTypeName)) {
					buf.append( thisValue );
				} else if("boolean".equals(javaTypeName)) {
					buf.append("(" + thisValue + "?1:0)");
				} else {
					buf.append( "(int) ");
					buf.append( thisValue );
				}
				buf.append(";");
			}
			else {
				if(javaTypeName.endsWith("[]")) {
					if(jdk5) {
						buf.append( result )
						.append( " = 37 * " )
						.append( result )
						.append( " + " );
						buf.append( "( " )
						.append( getGetterSignature( property ) )
						.append( "() == null ? 0 : " + importType("java.util.Arrays") + ".hashCode(" )
						.append( thisName )
						.append( "." )
						.append( getGetterSignature( property ) )
						.append( "())" )
						.append( " )" )
						.append(";");						
					}
					else {
						buf.append(internalGenerateArrayHashcode(property, javaTypeName, result, thisName));
					}
				} else {
					buf.append( result )
					.append( " = 37 * " )
					.append( result )
					.append( " + " );
					buf.append( "( " )
					.append( getGetterSignature( property ) )
					.append( "() == null ? 0 : " )
					.append( thisName )
					.append( "." )
					.append( getGetterSignature( property ) )
					.append( "()" )
					.append( ".hashCode()" )
					.append( " )" )
					.append(";");
				}
			}
		}
		return buf.toString();
	}


	private String internalGenerateArrayHashcode(Property property, String javaTypeName, String result, String thisName)
	{
		StringBuffer buf = new StringBuffer();

		String propertyHashVarName = property.getName() + "Hashcode";
		String propertyArrayName = property.getName() + "Property";

//		int propertyHash = 0;
		buf.append( "int ")
		.append( propertyHashVarName )
		.append( " = 0;\n" );

//		type[] proterty = getProperty();
		buf.append( "         " )
		.append( javaTypeName )
		.append( " " )
		.append( propertyArrayName )
		.append( " = " )
		.append( thisName )
		.append( "." )
		.append( getGetterSignature( property ) )
		.append( "();\n");

//		if(property != null) {
		buf.append( "         if(" )
		.append( propertyArrayName )
		.append( " != null) {\n" );

//		propertyHash = 1;
		buf.append( "             " )
		.append( propertyHashVarName )
		.append( " = 1;\n" );

//		for (int i=0; i<property.length; i++)
		javaTypeName.replaceAll("\\[\\]", "");
		buf.append( "             for (int i=0; i<" )
		.append( propertyArrayName )
		.append( ".length; i++) {\n" );

		if(javaTypeName.startsWith("long")) {
//			int elementHash = (int)(propertyArray[i] ^ (propertyArray[i] >>> 32));
			buf.append( "                 int elementHash = (int)(" )
			.append( propertyArrayName )
			.append( "[i] ^ (" )
			.append( propertyArrayName )
			.append( "[i] >>> 32));\n" );

//			propertyHash = 37 * propertyHash + elementHash;
			buf.append( "                 " )
			.append( propertyHashVarName )
			.append( " = 37 * " )
			.append( propertyHashVarName )
			.append( " + elementHash;\n" );
		} else if(javaTypeName.startsWith("boolean")) {
//			propertyHash = 37 * propertyHash + (propertyArray[i] ? 1231 : 1237);
			buf.append( "                 " )
			.append( propertyHashVarName )
			.append( " = 37 * " )
			.append( propertyHashVarName )
			.append( " + (" )
			.append( propertyArrayName )
			.append( "[i] ? 1231 : 1237);\n" );
		} else if(javaTypeName.startsWith("float")) {
//			propertyHash = 37 * propertyHash + Float.floatToIntBits(propertyArray[i]);
			buf.append( "                 " )
			.append( propertyHashVarName )
			.append( " = 37 * " )
			.append( propertyHashVarName )
			.append( " + Float.floatToIntBits(" )
			.append( propertyArrayName )
			.append( "[i]);\n" );
		} else if(javaTypeName.startsWith("double")) {
//			long bits = Double.doubleToLongBits(propertyArray[i]);
			buf.append( "                 long bits = Double.doubleToLongBits(" )
			.append( propertyArrayName )
			.append( "[i]);\n" );

//			propertyHash = 37 * propertyHash + (int)(bits ^ (bits >>> 32));
			buf.append( "                 " )
			.append( propertyHashVarName )
			.append( " = 37 * " )
			.append( propertyHashVarName )
			.append( " + (int)(bits ^ (bits >>> 32));\n" );
		} else if(javaTypeName.startsWith("int")
				|| javaTypeName.startsWith("short")
				|| javaTypeName.startsWith("char")
				|| javaTypeName.startsWith("byte")) {
//			propertyHash = 37 * propertyHash + propertyArray[i];
			buf.append( "                 " )
			.append( propertyHashVarName )
			.append( " = 37 * " )
			.append( propertyHashVarName )
			.append( " + " )
			.append( propertyArrayName )
			.append( "[i];\n" );
		} else {// Object[]
//			propertyHash = 37 * propertyHash + propertyArray[i].hashCode();
			buf.append( "                 " )
			.append( propertyHashVarName )
			.append( " = 37 * " )
			.append( propertyHashVarName )
			.append( " + " )
			.append( propertyArrayName )
			.append( "[i].hashCode();\n" );
		}

		buf.append( "             }\n" );
		buf.append( "         }\n\n" );

//		result = 37 * result + arrayHashcode;
		buf.append( "         " )
		.append( result )
		.append( " = 37 * " )
		.append( result )
		.append( " + " )
		.append( propertyHashVarName )
		.append( ";\n" );

		return buf.toString();
	}


	public String getFieldModifiers(Property property) {
		return getModifiers( property, "scope-field", "private" );
	}

	public String getPropertyGetModifiers(Property property) {
		return getModifiers( property, "scope-get", "public" );
	}

	public String getPropertySetModifiers(Property property) {
		return getModifiers( property, "scope-set", "public" );
	}

	//TODO defaultModifiers
	private String getModifiers(Property property, String modifiername, String defaultModifiers) {
		MetaAttribute override = property.getMetaAttribute( modifiername );
		if ( override != null ) {
			return MetaAttributeHelper.getMetaAsString( override );
		}
		else {
			return defaultModifiers;
		}
	}

	protected boolean isRequiredInConstructor(Property field) {
		if(hasMetaAttribute(field, "default-value")) {
			return false;
		}
		if(field.getValue()!=null) {			
			if (!field.isOptional() && (field.getValueGenerationStrategy() == null || field.getValueGenerationStrategy().getGenerationTiming().equals(GenerationTiming.NEVER))) {				
				return true;
			} else if (field.getValue() instanceof Component) {
				Component c = (Component) field.getValue();
				Iterator<?> it = c.getPropertyIterator();
				while ( it.hasNext() ) {
					Property prop = (Property) it.next();
					if(isRequiredInConstructor(prop)) {
						return true;
					}
				}
			}
		}
		
		return false;
	}

	public boolean needsMinimalConstructor() {
		List<Property> propClosure = getPropertyClosureForMinimalConstructor();
		if(propClosure.isEmpty()) return false; // minimal=default
		if(propClosure.equals(getPropertyClosureForFullConstructor())) return false; // minimal=full
		return true;
	}

	public boolean needsFullConstructor() {
		return !getPropertyClosureForFullConstructor().isEmpty();		
	}
	
	public String getJavaTypeName(Property p, boolean useGenerics) {
		return c2j.getJavaTypeName(p, useGenerics, this);
	}

	public String[] getCascadeTypes(Property property) {
		StringTokenizer st =  new StringTokenizer( property.getCascade(), ", ", false );
		List<String> types = new ArrayList<String>();
		while ( st.hasMoreElements() ) {
			String element = ( (String) st.nextElement() ).toLowerCase();
			if ( "persist".equals( element ) ) {
				types.add(importType( "javax.persistence.CascadeType" ) + ".PERSIST");
			}
			else if ( "merge".equals( element ) ) {
				types.add(importType( "javax.persistence.CascadeType") + ".MERGE");
			}
			else if ( "delete".equals( element ) ) {
				types.add(importType( "javax.persistence.CascadeType") + ".REMOVE");
			}
			else if ( "refresh".equals( element ) ) {
				types.add(importType( "javax.persistence.CascadeType") + ".REFRESH");
			}
			else if ( "all".equals( element ) ) {
				types.add(importType( "javax.persistence.CascadeType") + ".ALL");
			}
		}
		return (String[]) types.toArray( new String[types.size()] );
	}

	public String getOneToOneMappedBy(Metadata md, OneToOne oneToOne) {
		String mappedBy;
		Iterator<Selectable> joinColumnsIt = oneToOne.getColumnIterator();
		java.util.Set<Selectable> joinColumns = new HashSet<Selectable>();
		while ( joinColumnsIt.hasNext() ) {
			joinColumns.add( joinColumnsIt.next() );
		}
		PersistentClass pc = md.getEntityBinding(oneToOne.getReferencedEntityName());
		String referencedPropertyName = oneToOne.getReferencedPropertyName();
		if ( referencedPropertyName != null )
			return referencedPropertyName;

		Iterator<?> properties = pc.getPropertyClosureIterator();
		//TODO we should check the table too
		boolean isOtherSide = false;
		mappedBy = "unresolved";


		while ( ! isOtherSide && properties.hasNext() ) {
			Property oneProperty = (Property) properties.next();
			Value manyValue = oneProperty.getValue();
			if ( manyValue != null && ( manyValue instanceof OneToOne || manyValue instanceof ManyToOne ) ) {
				if ( joinColumns.size() == manyValue.getColumnSpan() ) {
					isOtherSide = true;
					Iterator<?> it = manyValue.getColumnIterator();
					while ( it.hasNext() ) {
						Object column = it.next();
						if (! joinColumns.contains( column ) ) {
							isOtherSide = false;
							break;
						}
					}
					if (isOtherSide) {
						mappedBy = oneProperty.getName();
					}
				}

			}
		}
		return mappedBy;
	}

	public String getFetchType(Property property) {
		Value value = property.getValue();
		String fetchType = importType( "javax.persistence.FetchType");
		boolean lazy = false;
		if ( value instanceof ToOne ) {
			lazy = ( (ToOne) value ).isLazy();
		}
		else if ( value instanceof Collection ) {
			lazy = ( (Collection) value ).isLazy();
		}
		else {
			//we're not collection neither *toone so we are looking for property fetching
			lazy = property.isLazy();
		}
		if ( lazy ) {
			return fetchType + "." + "LAZY";
		}
		else {
			return fetchType + "." + "EAGER";
		}
	}

	public String getHibernateCascadeTypeAnnotation(Property property) {
		StringTokenizer st =  new StringTokenizer( property.getCascade(), ", ", false );
		String cascadeType = null;
		StringBuffer cascade = new StringBuffer();
		while ( st.hasMoreElements() ) {
			String element = ( (String) st.nextElement() ).toLowerCase();
			if ( "all-delete-orphan".equals( element ) ) {
				if (cascadeType == null) cascadeType = importType( "org.hibernate.annotations.CascadeType");
				cascade.append( cascadeType ).append(".ALL").append(", ")
						.append( cascadeType ).append(".DELETE_ORPHAN").append(", ");
			}
			else if ( "delete-orphan".equals( element ) ) {
				if (cascadeType == null) cascadeType = importType( "org.hibernate.annotations.CascadeType");
				cascade.append( cascadeType ).append(".DELETE_ORPHAN").append(", ");
			}
			else if ( "save-update".equals( element ) ) {
				if (cascadeType == null) cascadeType = importType( "org.hibernate.annotations.CascadeType");
				cascade.append( cascadeType ).append(".SAVE_UPDATE").append(", ");
			}
			else if ( "replicate".equals( element ) ) {
				if (cascadeType == null) cascadeType = importType( "org.hibernate.annotations.CascadeType");
				cascade.append( cascadeType ).append(".REPLICATE").append(", ");
			}
			else if ( "lock".equals( element ) ) {
				if (cascadeType == null) cascadeType = importType( "org.hibernate.annotations.CascadeType");
				cascade.append( cascadeType ).append(".LOCK").append(", ");
			}
			else if ( "evict".equals( element ) ) {
				if (cascadeType == null) cascadeType = importType( "org.hibernate.annotations.CascadeType");
				cascade.append( cascadeType ).append(".EVICT").append(", ");
			}
		}
		if ( cascade.length() >= 2 ) {
			String hibernateCascade = importType("org.hibernate.annotations.Cascade");
			cascade.insert(0, "@" + hibernateCascade + "( {");
			cascade.setLength( cascade.length() - 2 );
			cascade.append("} )");
		}
		return cascade.toString();
	}

	public String generateManyToOneAnnotation(Property property) {
		StringBuffer buffer = new StringBuffer(AnnotationBuilder.createAnnotation( importType("javax.persistence.ManyToOne") )
				.addAttribute( "cascade", getCascadeTypes(property))
				.addAttribute( "fetch", getFetchType(property))
				.getResult());
		buffer.append(getHibernateCascadeTypeAnnotation(property));
		return buffer.toString();
	}

	protected void buildJoinColumnAnnotation(
			Selectable selectable, Selectable referencedColumn, StringBuffer annotations, boolean insertable, boolean updatable
	) {
		if ( selectable.isFormula() ) {
			//TODO not supported by HA
		}
		else {
			Column column = (Column) selectable;
			annotations.append("@").append( importType("javax.persistence.JoinColumn") )
					.append("(name=\"" ).append( column.getName() ).append( "\"" );
					//TODO handle referenced column name, this is a hard one
			        if(referencedColumn!=null) {
			         annotations.append(", referencedColumnName=\"" ).append( referencedColumn.getText() ).append( "\"" );
			        }

					appendCommonColumnInfo(annotations, column, insertable, updatable);
			//TODO support secondary table
			annotations.append( ")" );
		}
	}

	protected void buildArrayOfJoinColumnAnnotation(
			Iterator<Selectable> columns, Iterator<Selectable> referencedColumnsIterator, StringBuffer annotations, boolean insertable,
			boolean updatable
	) {
		while ( columns.hasNext() ) {
			Selectable selectable = columns.next();
            Selectable referencedColumn = null;
            if(referencedColumnsIterator!=null) {
            	referencedColumn = referencedColumnsIterator.next();
            }

			if ( selectable.isFormula() ) {
				//TODO formula in multicolumns not supported by annotations
				//annotations.append("/* TODO formula in multicolumns not supported by annotations */");
			}
			else {
				annotations.append( "\n        " );
				buildJoinColumnAnnotation( selectable, referencedColumn, annotations, insertable, updatable );
				annotations.append( ", " );
			}
		}
		annotations.setLength( annotations.length() - 2 );
	}

	public boolean isSharedPkBasedOneToOne(OneToOne oneToOne){
		Iterator<Selectable> joinColumnsIt = oneToOne.getColumnIterator();
		java.util.Set<Selectable> joinColumns = new HashSet<Selectable>();
		while ( joinColumnsIt.hasNext() ) {
			joinColumns.add( joinColumnsIt.next() );
		}

		if ( joinColumns.size() == 0 )
			return false;

		Iterator<?> idColumnsIt = getIdentifierProperty().getColumnIterator();
		while ( idColumnsIt.hasNext() ) {
			if (!joinColumns.contains(idColumnsIt.next()) )
				return false;
		}

		return true;
	}

	public String generateOneToOneAnnotation(Property property, Metadata md) {
		OneToOne oneToOne = (OneToOne)property.getValue();

		boolean pkIsAlsoFk = isSharedPkBasedOneToOne(oneToOne);

		AnnotationBuilder ab = AnnotationBuilder.createAnnotation( importType("javax.persistence.OneToOne") )
			.addAttribute( "cascade", getCascadeTypes(property))
			.addAttribute( "fetch", getFetchType(property));

		if ( oneToOne.getForeignKeyType().equals(ForeignKeyDirection.TO_PARENT) ){
			ab.addQuotedAttribute("mappedBy", getOneToOneMappedBy(md, oneToOne));
		}

		StringBuffer buffer = new StringBuffer(ab.getResult());
		buffer.append(getHibernateCascadeTypeAnnotation(property));

		if ( pkIsAlsoFk && oneToOne.getForeignKeyType().equals(ForeignKeyDirection.FROM_PARENT) ){
			AnnotationBuilder ab1 = AnnotationBuilder.createAnnotation( importType("javax.persistence.PrimaryKeyJoinColumn") );
			buffer.append(ab1.getResult());
		}

		return buffer.toString();
	}

	@SuppressWarnings("unchecked")
	public String generateJoinColumnsAnnotation(Property property, Metadata md) {
		boolean insertable = property.isInsertable();
		boolean updatable = property.isUpdateable();
		Value value = property.getValue();
		int span;
		Iterator<Selectable> columnIterator;
		Iterator<Selectable> referencedColumnsIterator = null;
		if (value != null && value instanceof Collection) {
			Collection collection = (Collection) value;
			span = collection.getKey().getColumnSpan();
			columnIterator = collection.getKey().getColumnIterator();
		}
		else {
			span = property.getColumnSpan();
			columnIterator = property.getColumnIterator();
		}

		if(property.getValue() instanceof ToOne) {
			String referencedEntityName = ((ToOne)property.getValue()).getReferencedEntityName();
			PersistentClass target = md.getEntityBinding(referencedEntityName);
			if(target!=null) {
				referencedColumnsIterator = target.getKey().getColumnIterator();
			}
		}

		StringBuffer annotations = new StringBuffer( "    " );
		if ( span == 1 ) {
				Selectable selectable = columnIterator.next();
				buildJoinColumnAnnotation( selectable, null, annotations, insertable, updatable );
		}
		else {
			Iterator<Selectable> columns = columnIterator;
			annotations.append("@").append( importType("javax.persistence.JoinColumns") ).append("( { " );
			buildArrayOfJoinColumnAnnotation( columns, referencedColumnsIterator, annotations, insertable, updatable );
			annotations.append( " } )" );
		}
		return annotations.toString();
	}

	protected String generateAnnTableUniqueConstraint(Table table) {
		Iterator<UniqueKey> uniqueKeys = table.getUniqueKeyIterator();
		List<String> cons = new ArrayList<String>();
		while ( uniqueKeys.hasNext() ) {
			UniqueKey key = (UniqueKey) uniqueKeys.next();
			if (table.hasPrimaryKey() && table.getPrimaryKey().getColumns().equals(key.getColumns())) {
				continue;
			}
			AnnotationBuilder constraint = AnnotationBuilder.createAnnotation( importType("javax.persistence.UniqueConstraint") );
			constraint.addQuotedAttributes( "columnNames", new IteratorTransformer<Column>(key.getColumnIterator()) {
				public String transform(Column column) {
					return column.getName();
				}
			});
			cons.add( constraint.getResult() );
		}

		AnnotationBuilder builder = AnnotationBuilder.createAnnotation( "dummyAnnotation" );
		builder.addAttributes( "dummyAttribute", cons.iterator() );
		String attributeAsString = builder.getAttributeAsString( "dummyAttribute" );
		return attributeAsString==null?"":attributeAsString;
	}

	public String generateCollectionAnnotation(Property property, Metadata md) {
		StringBuffer annotation = new StringBuffer();
		Value value = property.getValue();
		if ( value != null && value instanceof Collection) {
			Collection collection = (Collection) value;
			if ( collection.isOneToMany() ) {
				String mappedBy = null;
				AnnotationBuilder ab = AnnotationBuilder.createAnnotation( importType( "javax.persistence.OneToMany") );
				ab.addAttribute( "cascade", getCascadeTypes( property ) );
				ab.addAttribute( "fetch", getFetchType (property) );
				if ( collection.isInverse() ) {
					mappedBy = getOneToManyMappedBy( md, collection );
					ab.addQuotedAttribute( "mappedBy", mappedBy );
				}
				annotation.append( ab.getResult() );

				if (mappedBy == null) annotation.append("\n").append( generateJoinColumnsAnnotation(property, md) );
			}
			else {
				//TODO do the @OneToMany @JoinTable
				//TODO composite element
				String mappedBy = null;
				AnnotationBuilder ab = AnnotationBuilder.createAnnotation( importType( "javax.persistence.ManyToMany") );
				ab.addAttribute( "cascade", getCascadeTypes( property ) );
				ab.addAttribute( "fetch", getFetchType (property) );

				if ( collection.isInverse() ) {
					mappedBy = getManyToManyMappedBy( md, collection );
					ab.addQuotedAttribute( "mappedBy", mappedBy );
				}
				annotation.append(ab.getResult());
				if (mappedBy == null) {
					annotation.append("\n    @");
					annotation.append( importType( "javax.persistence.JoinTable") ).append( "(name=\"" );
					Table table = collection.getCollectionTable();

					annotation.append( table.getName() );
					annotation.append( "\"" );
					if ( StringHelper.isNotEmpty( table.getSchema() ) ) {
						annotation.append(", schema=\"").append( table.getSchema() ).append("\"");
					}
					if ( StringHelper.isNotEmpty( table.getCatalog() ) ) {
						annotation.append(", catalog=\"").append( table.getCatalog() ).append("\"");
					}
					String uniqueConstraint = generateAnnTableUniqueConstraint(table);
					if ( uniqueConstraint.length() > 0 ) {
						annotation.append(", uniqueConstraints=").append(uniqueConstraint);
					}
					annotation.append( ", joinColumns = { ");
					buildArrayOfJoinColumnAnnotation(
							collection.getKey().getColumnIterator(),
							null,
							annotation,
							property.isInsertable(),
							property.isUpdateable()
					);
					annotation.append( " }");
					annotation.append( ", inverseJoinColumns = { ");
					buildArrayOfJoinColumnAnnotation(
							collection.getElement().getColumnIterator(),
							null,
							annotation,
							property.isInsertable(),
							property.isUpdateable()
					);
					annotation.append( " }");
					annotation.append(")");
				}

			}
			String hibernateCascade = getHibernateCascadeTypeAnnotation( property );
			if (hibernateCascade.length() > 0) annotation.append("\n    ").append(hibernateCascade);
		}
		return annotation.toString();
	}

	private String getManyToManyMappedBy(Metadata md, Collection collection) {
		String mappedBy;
		Iterator<Selectable> joinColumnsIt = collection.getKey().getColumnIterator();
		java.util.Set<Selectable> joinColumns = new HashSet<Selectable>();
		while ( joinColumnsIt.hasNext() ) {
			joinColumns.add( joinColumnsIt.next() );
		}
		ManyToOne manyToOne = (ManyToOne) collection.getElement();
		PersistentClass pc = md.getEntityBinding(manyToOne.getReferencedEntityName());
		Iterator<?> properties = pc.getPropertyClosureIterator();
		//TODO we should check the table too
		boolean isOtherSide = false;
		mappedBy = "unresolved";
		while ( ! isOtherSide && properties.hasNext() ) {
			Property collectionProperty = (Property) properties.next();
			Value collectionValue = collectionProperty.getValue();
			if ( collectionValue != null && collectionValue instanceof Collection ) {
				Collection realCollectionValue = (Collection) collectionValue;
				if ( ! realCollectionValue.isOneToMany() ) {
					if ( joinColumns.size() == realCollectionValue.getElement().getColumnSpan() ) {
						isOtherSide = true;
						Iterator<?> it = realCollectionValue.getElement().getColumnIterator();
						while ( it.hasNext() ) {
							Object column = it.next();
							if (! joinColumns.contains( column ) ) {
								isOtherSide = false;
								break;
							}
						}
						if (isOtherSide) {
							mappedBy = collectionProperty.getName();
						}
					}
				}
			}
		}
		return mappedBy;
	}

	private String getOneToManyMappedBy(Metadata md, Collection collection) {
		String mappedBy;
		Iterator<Selectable> joinColumnsIt = collection.getKey().getColumnIterator();
		java.util.Set<Selectable> joinColumns = new HashSet<Selectable>();
		while ( joinColumnsIt.hasNext() ) {
			joinColumns.add( joinColumnsIt.next() );
		}
		OneToMany oneToMany = (OneToMany) collection.getElement();
		PersistentClass pc = md.getEntityBinding(oneToMany.getReferencedEntityName());
		Iterator<?> properties = pc.getPropertyClosureIterator();
		//TODO we should check the table too
		boolean isOtherSide = false;
		mappedBy = "unresolved";
		while ( ! isOtherSide && properties.hasNext() ) {
			Property manyProperty = (Property) properties.next();
			Value manyValue = manyProperty.getValue();
			if ( manyValue != null && manyValue instanceof ManyToOne ) {
				if ( joinColumns.size() == manyValue.getColumnSpan() ) {
					isOtherSide = true;
					Iterator<?> it = manyValue.getColumnIterator();
					while ( it.hasNext() ) {
						Object column = it.next();
						if (! joinColumns.contains( column ) ) {
							isOtherSide = false;
							break;
						}
					}
					if (isOtherSide) {
						mappedBy = manyProperty.getName();
					}
				}

			}
		}
		return mappedBy;
	}

	static private class DefaultInitializor {
		
		private final String type;
		private final boolean initToZero;
		
		public DefaultInitializor(String type, boolean initToZero) {
			this.type = type;
			this.initToZero = initToZero;					
		}
		
		public String getDefaultValue(String comparator, String genericDeclaration, ImportContext importContext) {
			StringBuffer val = new StringBuffer("new " + importContext.importType(type));
			if(genericDeclaration!=null) {
				val.append(genericDeclaration);
			}
			
			val.append("(");
			if(comparator!=null) {
				val.append("new ");
				val.append(importContext.importType(comparator));
				val.append("()");
				if(initToZero) val.append(",");
			}
			if(initToZero) {
				val.append("0");
			}
			val.append(")");
			return val.toString();
		}
		
	}
	
	static Map<String, DefaultInitializor> defaultInitializors = new HashMap<String, DefaultInitializor>();
	static {
		defaultInitializors.put("java.util.List", new DefaultInitializor("java.util.ArrayList", true));
		defaultInitializors.put("java.util.Map", new DefaultInitializor("java.util.HashMap", true));
		defaultInitializors.put("java.util.Set", new DefaultInitializor("java.util.HashSet",true));		
		defaultInitializors.put("java.util.SortedSet", new DefaultInitializor("java.util.TreeSet", false));
		defaultInitializors.put("java.util.SortedMap", new DefaultInitializor("java.util.TreeMap", false));
	}
	
	public boolean hasFieldInitializor(Property p, boolean useGenerics) {
		return getFieldInitialization(p, useGenerics)!=null;
	}
	
	public String getFieldInitialization(Property p, boolean useGenerics) {
		if(hasMetaAttribute(p, "default-value")) {
			return MetaAttributeHelper.getMetaAsString( p.getMetaAttribute( "default-value" ) );
		}
		if(c2j.getJavaTypeName(p, false)==null) {
			throw new IllegalArgumentException();
		} else if (p.getValue() instanceof Collection) {
			Collection col = (Collection) p.getValue();
			
			DefaultInitializor initialization = (DefaultInitializor) col.accept(new DefaultValueVisitor(true) {
			 
				public Object accept(Bag o) {
					return new DefaultInitializor("java.util.ArrayList", true);
				}
				
				public Object accept(org.hibernate.mapping.List o) {
					return new DefaultInitializor("java.util.ArrayList", true);
				}
				
				public Object accept(org.hibernate.mapping.Map o) {
					if(o.isSorted()) {
						return new DefaultInitializor("java.util.TreeMap", false);
					} else {
						return new DefaultInitializor("java.util.HashMap", true);
					}
				}
				
				public Object accept(IdentifierBag o) {
					return new DefaultInitializor("java.util.ArrayList", true);
				}
				
				public Object accept(Set o) {
					if(o.isSorted()) {
						return new DefaultInitializor("java.util.TreeSet", false);
					} else {
						return new DefaultInitializor("java.util.HashSet", true);
					}
				}
				
				
				public Object accept(PrimitiveArray o) {
					return null; // TODO: default init for arrays ?
				}
				
				public Object accept(Array o) {
					return null;// TODO: default init for arrays ?
				}
				
			});
						 
			if(initialization!=null) {
				String comparator = null;
				String decl = null;

				if(col.isSorted()) {
					comparator = col.getComparatorClassName();
				}

				if(useGenerics) {
					decl = c2j.getGenericCollectionDeclaration((Collection) p.getValue(), true, importContext);
				}
				return initialization.getDefaultValue(comparator, decl, this);
			} else {
				return null;
			}
		} else {
			return null;
		}
	}

	public MetaAttributable getMeta() {
		return meta;
	}
}
 
