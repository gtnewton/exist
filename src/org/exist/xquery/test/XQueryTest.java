package org.exist.xquery.test;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import org.custommonkey.xmlunit.DetailedDiff;
import org.custommonkey.xmlunit.XMLTestCase;
import org.exist.storage.DBBroker;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.EXistResource;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceIterator;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;

/** I propose that we put here in XQueryTest the tests involving all the 
 * others constructs of the XQuery language, besides XPath expressions.
 * And in {@link XPathQueryTest} we will put the tests involving only XPath expressions.
 * TODO maybe move the various eXist XQuery extensions in another class ... */
public class XQueryTest extends XMLTestCase {

	private static final String NUMBERS_XML = "numbers.xml";
	private static final String BOWLING_XML = "bowling.xml";
	private static final String MODULE1_NAME = "module1.xqm";
	private static final String MODULE2_NAME = "module2.xqm";
	private static final String MODULE3_NAME = "module3.xqm";
	private static final String MODULE4_NAME = "module4.xqm";
	private static final String FATHER_MODULE_NAME = "father.xqm";
	private static final String CHILD1_MODULE_NAME = "child1.xqm";
	private static final String CHILD2_MODULE_NAME = "child2.xqm";
	private static final String NAMESPACED_NAME = "namespaced.xml";
	private final static String URI = "xmldb:exist://" + DBBroker.ROOT_COLLECTION;

	private final static String numbers =
		"<test>"
			+ "<item id='1'><price>5.6</price><stock>22</stock></item>"
			+ "<item id='2'><price>7.4</price><stock>43</stock></item>"
			+ "<item id='3'><price>18.4</price><stock>5</stock></item>"
			+ "<item id='4'><price>65.54</price><stock>16</stock></item>"
			+ "</test>";

	private final static String module1 =
		"module namespace blah=\"blah\";\n"
		+ "declare variable $blah:param {\"value-1\"};";

	private final static String module2 =
		"module namespace foo=\"\";\n"
		+ "declare variable $foo:bar {\"bar\"};";

	private final static String module3 =
		"module namespace foo=\"foo\";\n"
		+ "declare variable $bar:bar {\"bar\"};";

	private final static String module4 =
		"module namespace foo=\"foo\";\n"
		//An external prefix in the statically known namespaces
		+ "declare variable $exist:bar external;\n"
		+ "declare function foo:bar() {\n"
	    + "$exist:bar\n"
	    + "};";
	
	private final static String fatherModule =
		"module namespace foo=\"foo\";\n"
		+ "import module namespace foo1=\"foo1\" at \"" + URI + "/test/" + CHILD1_MODULE_NAME + "\";\n"
		+ "import module namespace foo2=\"foo2\" at \"" + URI + "/test/" + CHILD2_MODULE_NAME + "\";\n"
		+ "declare variable $foo:bar { \"bar\" };\n "
	    + "declare variable $foo:bar1 { $foo1:bar };\n"
	    + "declare variable $foo:bar2 { $foo2:bar };\n";

	private final static String child1Module =
		"module namespace foo=\"foo1\";\n"
		+ "import module namespace blah=\"blah\" at \"" + URI + "/test/" + MODULE1_NAME + "\";\n"
		+ "declare variable $foo:bar {\"bar1\"};";	

	private final static String child2Module =
		"module namespace foo=\"foo2\";\n"
		+ "import module namespace blah=\"blah\" at \"" + URI + "/test/" + MODULE1_NAME + "\";\n"
		+ "declare variable $foo:bar {\"bar2\"};";	
	
	private final static String namespacedDocument =
		"<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\" \n" +	 
	         "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" \n" +	         
	         "xmlns:x=\"http://exist.sourceforge.net/dc-ext\"> \n" +	    
	         "<rdf:Description id=\"3\"> \n" +	        
	         	"<dc:title>title</dc:title> \n" +
	         	"<dc:creator>creator</dc:creator> \n" +	        
	         	"<x:place>place</x:place> \n"  +
	         	"<x:edition>place</x:edition> \n" +
	         "</rdf:Description> \n" +
	         "</rdf:RDF>";
	
	private final static String bowling = 
		"<series>" +
			"<game>" +
				"<frame/>" +
			"</game>" +
			"<game>" +
				"<frame/>" +
			"</game>" +
		"</series>";
	
	private Collection testCollection;
	private static String attributeXML;
	private static int stringSize;
	private static int nbElem;
	private String file_name = "detail_xml.xml";
	private String xml;
	private Database database;

	public XQueryTest(String arg0) {
		super(arg0);
	}

	protected void setUp() {
		try {
			// initialize driver
			Class cl = Class.forName("org.exist.xmldb.DatabaseImpl");
			database = (Database) cl.newInstance();
			database.setProperty("create-database", "true");
			DatabaseManager.registerDatabase(database);
			
			Collection root =
				DatabaseManager.getCollection("xmldb:exist://" + DBBroker.ROOT_COLLECTION, "admin",	null);
			CollectionManagementService service =
				(CollectionManagementService) root.getService("CollectionManagementService", "1.0");
			testCollection = service.createCollection("test");
			assertNotNull(testCollection);

		} catch (ClassNotFoundException e) {
		} catch (InstantiationException e) {
		} catch (IllegalAccessException e) {
		} catch (XMLDBException e) {
			e.printStackTrace();
		}
	}

	/*
	 * @see TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
		// testCollection.removeResource( testCollection .getResource(file_name));
		
		DatabaseManager.deregisterDatabase(database);
		DatabaseInstanceManager dim =
			(DatabaseInstanceManager) testCollection.getService(
				"DatabaseInstanceManager", "1.0");
		dim.shutdown();
        testCollection = null;
        database = null;
        
		System.out.println("tearDown PASSED");
	}
	
	public void testLet() {
		ResourceSet result;
		String query;
		XMLResource resu;
		try {
			XPathQueryService service = 
				storeXMLStringAndGetQueryService(NUMBERS_XML, numbers);

			//Non null context sequence
			System.out.println("testLet 1: ========" );
			query = "/test/item[let $id := ./@id return $id]";
			result = service.queryResource(NUMBERS_XML, query );
			printResult(result);
			assertEquals( "XQuery: " + query, 4, result.getSize() );

			System.out.println("testLet 2: ========" );
			query = "/test/item[let $id := ./@id return not(/test/set[@id=$id])]";
			result = service.queryResource(NUMBERS_XML, query );
			printResult(result);
			assertEquals( "XQuery: " + query, 4, result.getSize() );
			
		} catch (XMLDBException e) {
			System.out.println("testLet(): XMLDBException: "+e);
			fail(e.getMessage());
		}
	}

	public void testFor() {
		ResourceSet result;
		String query;
		XMLResource resu;
		try {
			XPathQueryService service = 
				storeXMLStringAndGetQueryService(NUMBERS_XML, numbers);

			System.out.println("testFor 1: ========" );
			query = "for $f in /*/item return $f";
			result = service.queryResource(NUMBERS_XML, query );
			printResult(result);
			assertEquals( "XQuery: " + query, 4, result.getSize() );

			System.out.println("testFor 2: ========" );
			query = "for $f in /*/item  order by $f ascending  return $f";
			result = service.queryResource(NUMBERS_XML, query );
			printResult(result);
			resu = (XMLResource) result.getResource(0);
			assertEquals( "XQuery: " + query, "3", ((Element)resu.getContentAsDOM()).getAttribute("id") );

			System.out.println("testFor 3: ========" );
			query = "for $f in /*/item  order by $f descending  return $f";
			result = service.queryResource(NUMBERS_XML, query );
			printResult(result);
			resu = (XMLResource) result.getResource(0);
			assertEquals( "XQuery: " + query, "2", ((Element)resu.getContentAsDOM()).getAttribute("id") );

			System.out.println("testFor 4: ========" );
			query = "for $f in /*/item  order by xs:double($f/price) descending  return $f";
			result = service.queryResource(NUMBERS_XML, query );
			printResult(result);
			resu = (XMLResource) result.getResource(0);
			assertEquals( "XQuery: " + query, "4", ((Element)resu.getContentAsDOM()).getAttribute("id") );
			
            System.out.println("testFor 5: ========" );
            query = "for $f in //item where $f/@id = '3' return $f";
            result = service.queryResource(NUMBERS_XML, query );
            printResult(result);
            resu = (XMLResource) result.getResource(0);
            assertEquals( "XQuery: " + query, "3", ((Element)resu.getContentAsDOM()).getAttribute("id") );            

            //Non null context sequence
            System.out.println("testFor 6: ========" );
            query = "/test/item[for $id in ./@id return $id]";
            result = service.queryResource(NUMBERS_XML, query );
            printResult(result);
            resu = (XMLResource) result.getResource(0);
			assertEquals( "XQuery: " + query, 4, result.getSize() );

            //Ordered value sequence
            System.out.println("testFor 7: ========" );
            query = "let $doc := <doc><value>Z</value><value>Y</value><value>X</value></doc> " +
				"return " +
				"let $ordered_values := " +
				"	for $value in $doc/value order by $value ascending " + 
				"	return $value " +
				"for $value in $doc/value " +
				"	return $value[. = $ordered_values[position() = 1]]";			

			result = service.queryResource(NUMBERS_XML, query );
	        printResult(result);
	        resu = (XMLResource) result.getResource(0);
			assertEquals( "XQuery: " + query, "<value>X</value>", resu.getContent() );

				
		} catch (XMLDBException e) {
			System.out.println("testFor(): XMLDBException: "+e);
			fail(e.getMessage());
		}
	}
	
    public void testRecursion() {
        try {
            String q1 =
                "declare function local:append($head, $i) {\n" +
                "   if ($i < 5000) then\n" +
                "       local:append(($head, $i), $i + 1)\n" +
                "   else\n" +
                "       $head\n" +
                "};\n" +
                "local:append((), 0)";
            XPathQueryService service =
                (XPathQueryService) testCollection.getService(
                    "XPathQueryService",
                    "1.0");
            ResourceSet result = service.query(q1);
            assertEquals(result.getSize(), 5000);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
    
	public void testCombiningNodeSequences() {
		ResourceSet result;
		String query;
		
		try {
			XPathQueryService service =
				(XPathQueryService) testCollection.getService(
					"XPathQueryService",
					"1.0");
			
			System.out.println("testCombiningNodeSequences 1: ========" );
			query = "let $a := <a/> \n" +
			"let $aa := ($a, $a) \n" +
			"for $b in ($aa intersect $aa \n)" +
			"return $b";
			result = service.query(query);
			printResult(result);
			assertEquals( "XQuery: " + query, 1, result.getSize() );
			assertEquals( "XQuery: " + query, "<a/>", ((XMLResource)result.getResource(0)).getContent());

			System.out.println("testCombiningNodeSequences 2: ========" );
			query = "let $a := <a/> \n" +
			"let $aa := ($a, $a) \n" +
			"for $b in ($aa union $aa \n)" +
			"return $b";
			result = service.query(query);
			printResult(result);
			assertEquals( "XQuery: " + query, 1, result.getSize() );
			assertEquals( "XQuery: " + query, "<a/>", ((XMLResource)result.getResource(0)).getContent());
			
			System.out.println("testCombiningNodeSequences 3: ========" );
			query = "let $a := <a/> \n" +
			"let $aa := ($a, $a) \n" +
			"for $b in ($aa except $aa \n)" +
			"return $b";
			result = service.query(query);
			printResult(result);
			assertEquals( "XQuery: " + query, 0, result.getSize() );

			
		} catch (XMLDBException e) {
			System.out.println("testCombiningNodeSequences(): XMLDBException: "+e);
			fail(e.getMessage());
		}
	}	
	
	public void testVariable() {
		ResourceSet result;
		String query;
		XMLResource resu;
		boolean exceptionThrown;
		String message;				
		try {
			XPathQueryService service =
				(XPathQueryService) testCollection.getService(
					"XPathQueryService",
					"1.0");

            System.out.println("testVariable 1: ========" );
            query = "xquery version \"1.0\";\n"                 
                + "declare namespace param=\"param\";\n"
                + "declare variable $param:a {\"a\"};\n"
                + "declare function param:a() {$param:a};\n"
                + "let $param:a := \"b\" \n"
                + "return ($param:a, $param:a)";            
            result = service.query(query);
            printResult(result);
            assertEquals( "XQuery: " + query, 2, result.getSize() );
            assertEquals( "XQuery: " + query, "b", ((XMLResource)result.getResource(0)).getContent());
            assertEquals( "XQuery: " + query, "b", ((XMLResource)result.getResource(1)).getContent());
            
            System.out.println("testVariable 2: ========" );
			query = "xquery version \"1.0\";\n" 				
				+ "declare namespace param=\"param\";\n"
				+ "declare variable $param:a {\"a\"};\n"
				+ "declare function param:a() {$param:a};\n"
				+ "let $param:a := \"b\" \n"
				+ "return param:a(), param:a()";				
			result = service.query(query);
			printResult(result);
			assertEquals( "XQuery: " + query, 2, result.getSize() );
			assertEquals( "XQuery: " + query, "a", ((XMLResource)result.getResource(0)).getContent());
			assertEquals( "XQuery: " + query, "a", ((XMLResource)result.getResource(1)).getContent());
			
            System.out.println("testVariable 3: ========" );
			query = "declare variable $foo {\"foo1\"};\n"				
				+ "let $foo := \"foo2\" \n"
				+ "for $bar in (1 to 1) \n"
				+ "  let $foo := \"foo3\" \n"
				+ "  return $foo";				
			result = service.query(query);
			printResult(result);
            assertEquals( "XQuery: " + query, 1, result.getSize() );
			assertEquals( "XQuery: " + query, "foo3", ((XMLResource)result.getResource(0)).getContent());
			
			try {
				message = "";
				System.out.println("testVariable 4 ========" );
				query = "xquery version \"1.0\";\n" 				
					+ "declare variable $a {\"1st instance\"};\n"
					+ "declare variable $a {\"2nd instance\"};\n"
					+ "$a";
				result = service.query(query);
            } catch (XMLDBException e) {
                message = e.getMessage();
            }
            assertTrue(message.indexOf("XQST0049") > -1);
						
			System.out.println("testVariable 5: ========" );
			query = "xquery version \"1.0\";\n" 				
				+ "declare namespace param=\"param\";\n"				
				+ "declare function param:f() { $param:a };\n"
				+ "declare variable $param:a {\"a\"};\n"
				+ "param:f()";				
			result = service.query(query);
			printResult(result);
			assertEquals( "XQuery: " + query, 1, result.getSize() );
			assertEquals( "XQuery: " + query, "a", ((XMLResource)result.getResource(0)).getContent());
            
            System.out.println("testVariable 6: ========" );            
            query = "let $a := <root> " +
            "<b name='1'>"+
            "  <c name='x'> " +
            "    <bar name='2'/> " + 
            "    <bar name='3'> " +
            "      <bar name='4'/> " +
            "    </bar> " +
            "  </c> " +
            "</b> " +
            "</root> " +
            "let $b := for $bar in $a/b/c/bar " +
            "where ($bar/../@name = 'x') " +
            "return $bar " +
            "return $b";
            result = service.queryResource(NUMBERS_XML, query );
            assertEquals( "XQuery: " + query, 2, result.getSize() );
            printResult(result);
            resu = (XMLResource) result.getResource(0);
            assertEquals( "XQuery: " + query, "2", ((Element)resu.getContentAsDOM()).getAttribute("name") );    
            resu = (XMLResource) result.getResource(1);
            assertEquals( "XQuery: " + query, "3", ((Element)resu.getContentAsDOM()).getAttribute("name") ); 
			
        } catch (XMLDBException e) {
            System.out.println("testVariable : XMLDBException: "+e);
            fail(e.getMessage());
        }
	}			
	
	public void testTypedVariables() {
		ResourceSet result;
		String query;
		boolean exceptionThrown;
		String message;		
		try {
			XPathQueryService service = 
				storeXMLStringAndGetQueryService(NUMBERS_XML, numbers);

			System.out.println("testTypedVariables 1: ========" );
			query = "let $v as element()* := ( <assign/> , <assign/> )\n" 
				+ "let $w := <r>{ $v }</r>\n"
				+ "let $x as element()* := $w/assign\n"
				+ "return $x";
			result = service.query(query);				
			assertEquals( "XQuery: " + query, 2, result.getSize() );
			assertEquals( "XQuery: " + query, Node.ELEMENT_NODE, ((XMLResource)result.getResource(0)).getContentAsDOM().getNodeType());
			assertEquals( "XQuery: " + query, "assign", ((XMLResource)result.getResource(0)).getContentAsDOM().getNodeName());

			System.out.println("testTypedVariables 2: ========" );
			query = "let $v as node()* := ()\n" 
			+ "return $v";
			result = service.query(query);		
			assertEquals( "XQuery: " + query, 0, result.getSize() );
			
			System.out.println("testTypedVariables 3: ========" );
			query = "let $v as item()* := ()\n" 
			+ "return $v";
			result = service.query(query);		
			assertEquals( "XQuery: " + query, 0, result.getSize() );			

			System.out.println("testTypedVariables 4: ========" );
			query = "let $v as empty() := ()\n" 
			+ "return $v";
			result = service.query(query);		
			assertEquals( "XQuery: " + query, 0, result.getSize() );			
			
			System.out.println("testTypedVariables 5: ========" );
			query = "let $v as item() := ()\n" 
			+ "return $v";			
			try {
				exceptionThrown = false;
				result = service.query(query);						
			} catch (XMLDBException e) {
				exceptionThrown = true;
				message = e.getMessage();
			}
			assertTrue("XQuery: " + query, exceptionThrown);
			
			System.out.println("testTypedVariables 6: ========" );
			query = "let $v as item()* := ( <a/> , 1 )\n" 
				+ "return $v";
			result = service.query(query);		
			assertEquals( "XQuery: " + query, 2, result.getSize() );	
			assertEquals( "XQuery: " + query, Node.ELEMENT_NODE, ((XMLResource)result.getResource(0)).getContentAsDOM().getNodeType());
			assertEquals( "XQuery: " + query, "a", ((XMLResource)result.getResource(0)).getContentAsDOM().getNodeName());			
			assertEquals( "XQuery: " + query, "1", ((XMLResource)result.getResource(1)).getContent());		
			
			System.out.println("testTypedVariables 7: ========" );
			query = "let $v as node()* := ( <a/> , 1 )\n" 
				+ "return $v";			
			try {
				exceptionThrown = false;
				result = service.query(query);		
			} catch (XMLDBException e) {
				exceptionThrown = true;
				message = e.getMessage();
			}
			assertTrue(exceptionThrown);	
			
			System.out.println("testTypedVariables 8: ========" );
			query = "let $v as item()* := ( <a/> , 1 )\n" 
				+ "let $w as element()* := $v\n"
				+ "return $w";		
			try {
				exceptionThrown = false;
				result = service.query(query);		
				result = service.query(query);		
			} catch (XMLDBException e) {
				exceptionThrown = true;
				message = e.getMessage();
			}
			assertTrue(exceptionThrown);	
			
			System.out.println("testTypedVariables 9: ========" );
			query = "declare variable $v as element()* {( <assign/> , <assign/> ) };\n" 
				+ "declare variable $w { <r>{ $v }</r> };\n"
				+ "declare variable $x as element()* { $w/assign };\n"
				+ "$x";
			result = service.query(query);				
			assertEquals( "XQuery: " + query, 2, result.getSize() );
			assertEquals( "XQuery: " + query, Node.ELEMENT_NODE, ((XMLResource)result.getResource(0)).getContentAsDOM().getNodeType());
			assertEquals( "XQuery: " + query, "assign", ((XMLResource)result.getResource(0)).getContentAsDOM().getNodeName());
			
			System.out.println("testTypedVariables 10: ========" );
			query = "declare variable $v as node()* { () };\n" 
			+ "$v";
			result = service.query(query);		
			assertEquals( "XQuery: " + query, 0, result.getSize() );
			
			System.out.println("testTypedVariables 11: ========" );
			query = "declare variable $v as item()* { () };\n" 
			+ "$v";
			result = service.query(query);		
			assertEquals( "XQuery: " + query, 0, result.getSize() );			

			System.out.println("testTypedVariables 12: ========" );
			query = "declare variable $v as empty() { () };\n" 
			+ "$v";
			result = service.query(query);		
			assertEquals( "XQuery: " + query, 0, result.getSize() );			
			
			System.out.println("testTypedVariables 13: ========" );
			query = "declare variable $v as item() { () };\n" 
			+ "$v";			
			try {
				exceptionThrown = false;
				result = service.query(query);						
			} catch (XMLDBException e) {
				exceptionThrown = true;
				message = e.getMessage();
			}
			assertTrue("XQuery: " + query, exceptionThrown);
			
			System.out.println("testTypedVariables 14: ========" );
			query = "declare variable $v as item()* { ( <a/> , 1 ) }; \n" 
				+ "$v";
			result = service.query(query);		
			assertEquals( "XQuery: " + query, 2, result.getSize() );	
			assertEquals( "XQuery: " + query, Node.ELEMENT_NODE, ((XMLResource)result.getResource(0)).getContentAsDOM().getNodeType());
			assertEquals( "XQuery: " + query, "a", ((XMLResource)result.getResource(0)).getContentAsDOM().getNodeName());			
			assertEquals( "XQuery: " + query, "1", ((XMLResource)result.getResource(1)).getContent());		
			
			System.out.println("testTypedVariables 15: ========" );
			query = "declare variable $v as node()* { ( <a/> , 1 ) };\n" 
				+ "$v";			
			try {
				exceptionThrown = false;
				result = service.query(query);		
			} catch (XMLDBException e) {
				exceptionThrown = true;
				message = e.getMessage();
			}
			assertTrue(exceptionThrown);	
			
			System.out.println("testTypedVariables 16: ========" );
			query = "declare variable $v as item()* { ( <a/> , 1 ) };\n" 
				+ "declare variable $w as element()* { $v };\n"
				+ "$w";		
			try {
				exceptionThrown = false;
				result = service.query(query);						
			} catch (XMLDBException e) {
				exceptionThrown = true;
				message = e.getMessage();
			}
			assertTrue(exceptionThrown);
			
			System.out.println("testTypedVariables 15: ========" );
			query = "let $v as document-node() :=  doc('" + DBBroker.ROOT_COLLECTION + "/test/" + NUMBERS_XML + "') \n" 
				+ "return $v";
			result = service.query(query);		
			assertEquals( "XQuery: " + query, 1, result.getSize() );	
			//TODO : no way to test the node type ?
			//assertEquals( "XQuery: " + query, Node.DOCUMENT_NODE, ((XMLResource)result.getResource(0)));
			assertEquals( "XQuery: " + query, "test", ((XMLResource)result.getResource(0)).getContentAsDOM().getNodeName());			
				
		} catch (XMLDBException e) {
			System.out.println("testTypedVariables : XMLDBException: "+e);
			fail(e.getMessage());
		}
	}	
	
	public void testPrecedence() {
		ResourceSet result;
		String query;
		boolean exceptionThrown;
		String message;		
		try {
			XPathQueryService service = 
				storeXMLStringAndGetQueryService(NUMBERS_XML, numbers);
	
			System.out.println("testPrecedence 1: ========" );
			query = "xquery version \"1.0\";\n" 
				+ "declare namespace blah=\"blah\";\n"
				+ "declare variable $blah:param  {\"value-1\"};\n"
				+ "let $blah:param := \"value-2\"\n"
				+ "(:: FLWOR expressions have a higher precedence than the comma operator ::)\n"
				+ "return $blah:param, $blah:param ";
			result = service.query(query);				
			assertEquals( "XQuery: " + query, 2, result.getSize() );
			assertEquals( "XQuery: " + query, "value-2", ((XMLResource)result.getResource(0)).getContent());
			assertEquals( "XQuery: " + query, "value-1", ((XMLResource)result.getResource(1)).getContent());
		
		} catch (XMLDBException e) {
			System.out.println("testTypedVariables : XMLDBException: "+e);
			fail(e.getMessage());
		}
	}
	
	public void testImprobableAxesAndNodeTestsCombinations() {
		ResourceSet result;
		String query;
		boolean exceptionThrown;
		String message;		
		try {
			XPathQueryService service = 
				storeXMLStringAndGetQueryService(NUMBERS_XML, numbers);
	
            System.out.println("testImprobableAxesAndNodeTestsCombinations 1: ========" );
            query = "let $a := <x>a<!--b-->c</x>/self::comment() return <z>{$a}</z>";
            result = service.query(query);              
            assertEquals( "XQuery: " + query, 1, result.getSize() );
            assertEquals( "XQuery: " + query, "<z/>", ((XMLResource)result.getResource(0)).getContent());

			System.out.println("testImprobableAxesAndNodeTestsCombinations 2: ========" );
			query = "let $a := <x>a<!--b-->c</x>/parent::comment() return <z>{$a}</z>";
			result = service.query(query);				
			assertEquals( "XQuery: " + query, 1, result.getSize() );
			assertEquals( "XQuery: " + query, "<z/>", ((XMLResource)result.getResource(0)).getContent());	
			
			System.out.println("testImprobableAxesAndNodeTestsCombinations 3: ========" );
			query = "let $a := <x>a<!--b-->c</x>/ancestor::comment() return <z>{$a}</z>";
			result = service.query(query);				
			assertEquals( "XQuery: " + query, 1, result.getSize() );
			assertEquals( "XQuery: " + query, "<z/>", ((XMLResource)result.getResource(0)).getContent());
			
			System.out.println("testImprobableAxesAndNodeTestsCombinations 4: ========" );
			query = "let $a := <x>a<!--b-->c</x>/ancestor-or-self::comment() return <z>{$a}</z>";
			result = service.query(query);				
			assertEquals( "XQuery: " + query, 1, result.getSize() );
			assertEquals( "XQuery: " + query, "<z/>", ((XMLResource)result.getResource(0)).getContent());			

//			This one is intercepted by the parser
			System.out.println("testImprobableAxesAndNodeTestsCombinations 5: ========" );
			query = "let $a := <x>a<!--b-->c</x>/attribute::comment() return <z>{$a}</z>";
			try {
				exceptionThrown = false;
				result = service.query(query);						
			} catch (XMLDBException e) {
				exceptionThrown = true;
				message = e.getMessage();
			}
			assertTrue(exceptionThrown);
			
//			This one is intercepted by the parser
			System.out.println("testImprobableAxesAndNodeTestsCombinations 6: ========" );
			query = "let $a := <x>a<!--b-->c</x>/namespace::comment() return <z>{$a}</z>";
			try {
				exceptionThrown = false;
				result = service.query(query);						
			} catch (XMLDBException e) {
				exceptionThrown = true;
				message = e.getMessage();
			}
			assertTrue(exceptionThrown);			
				
			System.out.println("testImprobableAxesAndNodeTestsCombinations 7: ========" );
			query = "let $a := <x>a<!--b-->c</x>/self::attribute() return <z>{$a}</z>";
			result = service.query(query);				
			assertEquals( "XQuery: " + query, 1, result.getSize() );
			assertEquals( "XQuery: " + query, "<z/>", ((XMLResource)result.getResource(0)).getContent());

			System.out.println("testImprobableAxesAndNodeTestsCombinations 8: ========" );
			query = "let $a := <x>a<!--b-->c</x>/parent::attribute() return <z>{$a}</z>";
			result = service.query(query);				
			assertEquals( "XQuery: " + query, 1, result.getSize() );
			assertEquals( "XQuery: " + query, "<z/>", ((XMLResource)result.getResource(0)).getContent());	
			
			System.out.println("testImprobableAxesAndNodeTestsCombinations 9: ========" );
			query = "let $a := <x>a<!--b-->c</x>/ancestor::attribute() return <z>{$a}</z>";
			result = service.query(query);				
			assertEquals( "XQuery: " + query, 1, result.getSize() );
			assertEquals( "XQuery: " + query, "<z/>", ((XMLResource)result.getResource(0)).getContent());
			
			System.out.println("testImprobableAxesAndNodeTestsCombinations 10: ========" );
			query = "let $a := <x>a<!--b-->c</x>/ancestor-or-self::attribute() return <z>{$a}</z>";
			result = service.query(query);				
			assertEquals( "XQuery: " + query, 1, result.getSize() );
			assertEquals( "XQuery: " + query, "<z/>", ((XMLResource)result.getResource(0)).getContent());	

			System.out.println("testImprobableAxesAndNodeTestsCombinations 11: ========" );
			query = "let $a := <x>a<!--b-->c</x>/child::attribute() return <z>{$a}</z>";
			result = service.query(query);				
			assertEquals( "XQuery: " + query, 1, result.getSize() );
			assertEquals( "XQuery: " + query, "<z/>", ((XMLResource)result.getResource(0)).getContent());
			
			System.out.println("testImprobableAxesAndNodeTestsCombinations 12: ========" );
			query = "let $a := <x>a<!--b-->c</x>/descendant::attribute() return <z>{$a}</z>";
			result = service.query(query);				
			assertEquals( "XQuery: " + query, 1, result.getSize() );
			assertEquals( "XQuery: " + query, "<z/>", ((XMLResource)result.getResource(0)).getContent());	
			
			System.out.println("testImprobableAxesAndNodeTestsCombinations 13: ========" );
			query = "let $a := <x>a<!--b-->c</x>/descendant-or-self::attribute() return <z>{$a}</z>";
			result = service.query(query);				
			assertEquals( "XQuery: " + query, 1, result.getSize() );
			assertEquals( "XQuery: " + query, "<z/>", ((XMLResource)result.getResource(0)).getContent());
			
			System.out.println("testImprobableAxesAndNodeTestsCombinations 14: ========" );
			query = "let $a := <x>a<!--b-->c</x>/preceding::attribute() return <z>{$a}</z>";
			result = service.query(query);				
			assertEquals( "XQuery: " + query, 1, result.getSize() );
			assertEquals( "XQuery: " + query, "<z/>", ((XMLResource)result.getResource(0)).getContent());	
			
			System.out.println("testImprobableAxesAndNodeTestsCombinations 15: ========" );
			query = "let $a := <x>a<!--b-->c</x>/preceding-sibling::attribute() return <z>{$a}</z>";
			result = service.query(query);				
			assertEquals( "XQuery: " + query, 1, result.getSize() );
			assertEquals( "XQuery: " + query, "<z/>", ((XMLResource)result.getResource(0)).getContent());	
			
			System.out.println("testImprobableAxesAndNodeTestsCombinations 16: ========" );
			query = "let $a := <x>a<!--b-->c</x>/following::attribute() return <z>{$a}</z>";
			result = service.query(query);				
			assertEquals( "XQuery: " + query, 1, result.getSize() );
			assertEquals( "XQuery: " + query, "<z/>", ((XMLResource)result.getResource(0)).getContent());	
			
			System.out.println("testImprobableAxesAndNodeTestsCombinations 17: ========" );
			query = "let $a := <x>a<!--b-->c</x>/following-sibling::attribute() return <z>{$a}</z>";
			result = service.query(query);				
			assertEquals( "XQuery: " + query, 1, result.getSize() );
			assertEquals( "XQuery: " + query, "<z/>", ((XMLResource)result.getResource(0)).getContent());
			
//			This one is intercepted by the parser
			System.out.println("testImprobableAxesAndNodeTestsCombinations 18: ========" );
			query = "let $a := <x>a<!--b-->c</x>/namespace::attribute() return <z>{$a}</z>";
			try {
				exceptionThrown = false;
				result = service.query(query);						
			} catch (XMLDBException e) {
				exceptionThrown = true;
				message = e.getMessage();
			}
			assertTrue(exceptionThrown);			
			
			//TODO : uncomment when PI are OK
			
			/*
			System.out.println("testImprobableAxesAndNodeTestsCombinations 19: ========" );
			query = "let $a := <x>a<?foo ?>c</x>/self::processing-instruction('foo') return <z>{$a}</z>";
			result = service.query(query);				
			assertEquals( "XQuery: " + query, 1, result.getSize() );
			assertEquals( "XQuery: " + query, "<z/>", ((XMLResource)result.getResource(0)).getContent());

			System.out.println("testImprobableAxesAndNodeTestsCombinations 20: ========" );
			query = "let $a := <x>a<?foo ?>c</x>/parent::processing-instruction('foo') return <z>{$a}</z>";
			result = service.query(query);				
			assertEquals( "XQuery: " + query, 1, result.getSize() );
			assertEquals( "XQuery: " + query, "<z/>", ((XMLResource)result.getResource(0)).getContent());	
			
			System.out.println("testImprobableAxesAndNodeTestsCombinations 21: ========" );
			query = "let $a := <x>a<?foo ?>c</x>/ancestor::processing-instruction('foo') return <z>{$a}</z>";
			result = service.query(query);				
			assertEquals( "XQuery: " + query, 1, result.getSize() );
			assertEquals( "XQuery: " + query, "<z/>", ((XMLResource)result.getResource(0)).getContent());
			
			System.out.println("testImprobableAxesAndNodeTestsCombinations 22: ========" );
			query = "let $a := <x>a<?foo ?>c</x>/ancestor-or-self::processing-instruction('foo') return <z>{$a}</z>";
			result = service.query(query);				
			assertEquals( "XQuery: " + query, 1, result.getSize() );
			assertEquals( "XQuery: " + query, "<z/>", ((XMLResource)result.getResource(0)).getContent());	
			*/		

//			This one is intercepted by the parser
			System.out.println("testImprobableAxesAndNodeTestsCombinations 23: ========" );
			query = "let $a := <x>a<?foo ?>c</x>/attribute::processing-instruction('foo') return <z>{$a}</z>";
			try {
				exceptionThrown = false;
				result = service.query(query);						
			} catch (XMLDBException e) {
				exceptionThrown = true;
				message = e.getMessage();
			}
			assertTrue(exceptionThrown);
			
//			This one is intercepted by the parser
			System.out.println("testImprobableAxesAndNodeTestsCombinations 24: ========" );
			query = "let $a := <x>a<?foo ?>c</x>/namespace::processing-instruction('foo') return <z>{$a}</z>";
			try {
				exceptionThrown = false;
				result = service.query(query);						
			} catch (XMLDBException e) {
				exceptionThrown = true;
				message = e.getMessage();
			}
			assertTrue(exceptionThrown);	
			
		} catch (XMLDBException e) {
 			System.out.println("testTypedVariables : XMLDBException: "+e);
			fail(e.getMessage());
		}		
		
	}
	
	public void testNamespace() {
		Resource doc;
		ResourceSet result;
		String query;
		XMLResource resu;
		boolean exceptionThrown;
		String message;				
		try {
			doc = testCollection.createResource(MODULE1_NAME, "BinaryResource");
			doc.setContent(module1);	
			((EXistResource) doc).setMimeType("application/xquery");
			testCollection.storeResource(doc);

			doc = testCollection.createResource(MODULE2_NAME, "BinaryResource");
			doc.setContent(module2);	
			((EXistResource) doc).setMimeType("application/xquery");
			testCollection.storeResource(doc);		
			
			doc = testCollection.createResource(NAMESPACED_NAME, "XMLResource");
			doc.setContent(namespacedDocument);	
			((EXistResource) doc).setMimeType("text/xml");
			testCollection.storeResource(doc);					
			
			XPathQueryService service =
				(XPathQueryService) testCollection.getService(
					"XPathQueryService",
					"1.0");
			
			System.out.println("testNamespace 1: ========" );
			query = "xquery version \"1.0\";\n" 
				+ "import module namespace blah=\"blah\" at \"" + URI + "/test/" + MODULE1_NAME + "\";\n"
				+ "(:: redefine existing prefix ::)\n"
				+ "declare namespace blah=\"bla\";\n"		
				+ "$blah:param";
			try {
				message = "";			
				result = service.query(query);
			} catch (XMLDBException e) {
				message = e.getMessage();
			}
			assertTrue(message.indexOf("XQST0033") > -1);

			System.out.println("testNamespace 2: ========" );
			query = "xquery version \"1.0\";\n" 
				+ "import module namespace blah=\"blah\" at \"" + URI + "/test/" + MODULE1_NAME + "\";\n"
				+ "(:: redefine existing prefix with same URI ::)\n"
				+ "declare namespace blah=\"blah\";\n"
				+ "declare variable $blah:param  {\"value-2\"};\n"			
				+ "$blah:param";
			try {
				message = "";			
				result = service.query(query);
			} catch (XMLDBException e) {
				message = e.getMessage();
			}
			assertTrue(message.indexOf("XQST0033") >  -1);

			System.out.println("testNamespace 3: ========" );
			query = "xquery version \"1.0\";\n" 
				+ "import module namespace foo=\"\" at \"" + URI + "/test/" + MODULE1_NAME + "\";\n"
				+ "$foo:bar";			
			try {
				message = "";	
				result = service.query(query);	
			} catch (XMLDBException e) {
				message = e.getMessage();
			}
			assertTrue(message.indexOf("does not match namespace URI") > -1);

			System.out.println("testNamespace 4: ========" );
			query = "xquery version \"1.0\";\n" 
				+ "import module namespace foo=\"\" at \"" + URI + "/test/" + MODULE2_NAME + "\";\n"
				+ "$bar";			
			try {
				message = "";	
				result = service.query(query);					
			} catch (XMLDBException e) {
				message = e.getMessage();
			}	
			assertTrue(message.indexOf("No namespace defined for prefix") > -1);
			
			System.out.println("testNamespace 5: ========" );
			query = "xquery version \"1.0\";\n" 
				+ "import module namespace foo=\"blah\" at \"" + URI + "/test/" + MODULE2_NAME + "\";\n"
				+ "$bar";			
			try {
				message = "";
				result = service.query(query);					
			} catch (XMLDBException e) {
				message = e.getMessage();
			}	
			assertTrue(message.indexOf("No namespace defined for prefix") > -1);	
			
			System.out.println("testNamespace 6: ========" );
			query = "declare namespace x = \"http://www.foo.com\"; \n" +
				"let $a := doc('" + DBBroker.ROOT_COLLECTION + "/test/" + NAMESPACED_NAME + "') \n" +
                "return $a//x:edition";				
			result = service.query(query);				
			assertEquals( "XQuery: " + query, 0, result.getSize() );
			
			System.out.println("testNamespace 7: ========" );
			query = "declare namespace x = \"http://www.foo.com\"; \n" +
			    "declare namespace y = \"http://exist.sourceforge.net/dc-ext\"; \n" +
				"let $a := doc('" + DBBroker.ROOT_COLLECTION + "/test/" + NAMESPACED_NAME + "') \n" +
                "return $a//y:edition";				
			result = service.query(query);				
			assertEquals( "XQuery: " + query, 1, result.getSize() );			
			assertEquals( "XQuery: " + query, "<x:edition xmlns:x=\"http://exist.sourceforge.net/dc-ext\">place</x:edition>",
				((XMLResource)result.getResource(0)).getContent());			
			
			System.out.println("testNamespace 8: ========" );
			query = "<result xmlns:rdf='http://www.w3.org/1999/02/22-rdf-syntax-ns#'>{//rdf:Description}</result>";
			result = service.query(query);
                        assertEquals("XQuery: "+query,
                                "<result xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n"+
                                "    <rdf:Description id=\"3\">\n" +
                                "        <dc:title xmlns:dc=\"http://purl.org/dc/elements/1.1/\">title</dc:title>\n" +
                                "        <dc:creator xmlns:dc=\"http://purl.org/dc/elements/1.1/\">creator</dc:creator>\n" +
                                "        <x:place xmlns:x=\"http://exist.sourceforge.net/dc-ext\">place</x:place>\n"  +
                                "        <x:edition xmlns:x=\"http://exist.sourceforge.net/dc-ext\">place</x:edition>\n" +
                                "    </rdf:Description>\n" +
                                "</result>",
                                ((XMLResource)result.getResource(0)).getContent());
                        
			System.out.println("testNamespace 9: ========" );
			query = "<result xmlns='http://www.w3.org/1999/02/22-rdf-syntax-ns#'>{//Description}</result>";
			result = service.query(query);
                        assertEquals("XQuery: "+query,
                                "<result xmlns=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n"+
                                "    <rdf:Description xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\" id=\"3\">\n" +
                                "        <dc:title xmlns:dc=\"http://purl.org/dc/elements/1.1/\">title</dc:title>\n" +
                                "        <dc:creator xmlns:dc=\"http://purl.org/dc/elements/1.1/\">creator</dc:creator>\n" +
                                "        <x:place xmlns:x=\"http://exist.sourceforge.net/dc-ext\">place</x:place>\n"  +
                                "        <x:edition xmlns:x=\"http://exist.sourceforge.net/dc-ext\">place</x:edition>\n" +
                                "    </rdf:Description>\n" +
                                "</result>",
                                ((XMLResource)result.getResource(0)).getContent());
                        
			//Interesting one : let's see with XQuery gurus :-)
			//declare namespace fn="";
			//fn:current-time()
			/*
			 If the URILiteral part of a namespace declaration is a zero-length string, 
			 any existing namespace binding for the given prefix is removed from 
			 the statically known namespaces. This feature provides a way 
			 to remove predeclared namespace prefixes such as local.
			 */
			
		} catch (XMLDBException e) {
			System.out.println("testNamespace : XMLDBException: "+e);
			fail(e.getMessage());
		}			
	}

	public void testModule() {
		Resource doc;
		ResourceSet result;
		String query;
		String message;				
		try {
			doc = testCollection.createResource(MODULE1_NAME, "BinaryResource");
			doc.setContent(module1);	
			((EXistResource) doc).setMimeType("application/xquery");
			testCollection.storeResource(doc);
			
			doc = testCollection.createResource(MODULE3_NAME, "BinaryResource");
			doc.setContent(module3);	
			((EXistResource) doc).setMimeType("application/xquery");
			testCollection.storeResource(doc);		

			doc = testCollection.createResource(MODULE4_NAME, "BinaryResource");
			doc.setContent(module4);	
			((EXistResource) doc).setMimeType("application/xquery");
			testCollection.storeResource(doc);		
			
			doc = testCollection.createResource(FATHER_MODULE_NAME, "BinaryResource");
			doc.setContent(fatherModule);	
			((EXistResource) doc).setMimeType("application/xquery");
			testCollection.storeResource(doc);

			doc = testCollection.createResource(CHILD1_MODULE_NAME, "BinaryResource");
			doc.setContent(child1Module);	
			((EXistResource) doc).setMimeType("application/xquery");
			testCollection.storeResource(doc);	

			doc = testCollection.createResource(CHILD2_MODULE_NAME, "BinaryResource");
			doc.setContent(child2Module);	
			((EXistResource) doc).setMimeType("application/xquery");
			testCollection.storeResource(doc);			
			
			XPathQueryService service =
				(XPathQueryService) testCollection.getService(
					"XPathQueryService",
					"1.0");
			
			System.out.println("testModule 1: ========" );
			query = "xquery version \"1.0\";\n" 
				+ "import module namespace blah=\"blah\" at \"" + URI + "/test/" + MODULE1_NAME + "\";\n"
				+ "$blah:param";			
			result = service.query(query);	
			printResult(result);	
			assertEquals( "XQuery: " + query, 1, result.getSize() );
			assertEquals( "XQuery: " + query, "value-1", ((XMLResource)result.getResource(0)).getContent());
			
			System.out.println("testModule 2: ========" );
			query = "xquery version \"1.0\";\n" 
				+ "import module namespace blah=\"blah\" at \"" + URI + "/test/" + MODULE1_NAME + "\";\n"
				+ "(:: redefine variable ::)\n"
				+ "declare variable $blah:param  {\"value-2\"};\n"			
				+ "$blah:param";
            try {
                message = "";   
                result = service.query(query);  
            } catch (XMLDBException e) {
                message = e.getMessage();               
            }           
            assertTrue(message.indexOf("XQST0049") > -1);
			
			System.out.println("testModule 3: ========" );
			query = "xquery version \"1.0\";\n" 
				+ "import module namespace blah=\"blah\" at \"" + URI + "/test/" + MODULE1_NAME + "\";\n"
				+ "declare namespace blah2=\"blah\";\n"		
				+ "$blah2:param";
			result = service.query(query);
			printResult(result);
			assertEquals( "XQuery: " + query, 1, result.getSize() );
			assertEquals( "XQuery: " + query, "value-1", ((XMLResource)result.getResource(0)).getContent());	
			
			System.out.println("testModule 4: ========" );
			query = "xquery version \"1.0\";\n" 
				+ "import module namespace blah=\"bla\" at \"" + URI + "/test/" + MODULE1_NAME + "\";\n"					
				+ "$blah:param";
			try {
				message = "";				
				result = service.query(query);	
			} catch (XMLDBException e) {
				message = e.getMessage();				
			}			
			assertTrue(message.indexOf("does not match namespace URI") > -1);
			
			System.out.println("testModule 5: ========" );
			query = "xquery version \"1.0\";\n" 
				+ "import module namespace foo=\"foo\" at \"" + URI + "/test/" + FATHER_MODULE_NAME + "\";\n"					
				+ "$foo:bar, $foo:bar1, $foo:bar2";					
			result = service.query(query);	
			printResult(result);	
			assertEquals( "XQuery: " + query, 3, result.getSize() );
			assertEquals( "XQuery: " + query, "bar", ((XMLResource)result.getResource(0)).getContent());		
			assertEquals( "XQuery: " + query, "bar1", ((XMLResource)result.getResource(1)).getContent());		
			assertEquals( "XQuery: " + query, "bar2", ((XMLResource)result.getResource(2)).getContent());		
			
//			Non-heritance check
			System.out.println("testModule 6: ========" );
			query = "xquery version \"1.0\";\n" 
				+ "import module namespace foo=\"foo\" at \"" + URI + "/test/" + FATHER_MODULE_NAME + "\";\n"
				+ "declare namespace foo1=\"foo1\"; \n"
				+ "$foo1:bar";
			try {
				message = "";				
				result = service.query(query);		
			} catch (XMLDBException e) {
				message = e.getMessage();				
			}			
			assertTrue(message.indexOf("is not bound") > -1);
			
//			Non-heritance check
			System.out.println("testModule 7: ========" );
			query = "xquery version \"1.0\";\n" 
				+ "import module namespace foo=\"foo\" at \"" + URI + "/test/" + FATHER_MODULE_NAME + "\";\n"	
				+ "declare namespace foo2=\"foo2\"; \n"
				+ "$foo2:bar";
			try {
				message = "";			
				result = service.query(query);	
			} catch (XMLDBException e) {
				message = e.getMessage();				
			}			
			assertTrue(message.indexOf("is not bound") > -1);
			
			System.out.println("testModule 8: ========" );
			query = "xquery version \"1.0\";\n" 
				+ "import module namespace foo1=\"foo\" at \"" + URI + "/test/" + CHILD1_MODULE_NAME + "\";\n"	
				+ "import module namespace foo2=\"foo\" at \"" + URI + "/test/" + CHILD1_MODULE_NAME + "\";\n"	
				+ "$foo1:bar";
			try {
				message = "";			
				result = service.query(query);						
			} catch (XMLDBException e) {				
				message = e.getMessage();				
			}	
//			Should be a XQST0047 error
			assertTrue(message.indexOf("does not match namespace URI") > -1);

			System.out.println("testModule 9: ========" );
			query = "xquery version \"1.0\";\n" 
				+ "import module namespace foo=\"foo\" at \"" + URI + "/test/" + MODULE3_NAME + "\";\n"		
				+ "$bar:bar";
			try {
				message = "";			
				result = service.query(query);						
			} catch (XMLDBException e) {				
				message = e.getMessage();				
			}
			assertTrue(message.indexOf("No namespace defined for prefix") > -1);
			
			System.out.println("testModule 10: ========" );
			query = "xquery version \"1.0\";\n" 
				+ "import module namespace foo=\"foo\" at \"" + URI + "/test/" + MODULE4_NAME + "\";\n"		
				+ "foo:bar()";
			try {
				message = "";			
				result = service.query(query);
				//WARNING !
				//This result is false ! The external vairable has not been resolved
				//Furthermore it is not in the module's namespace !
				printResult(result);	
				assertEquals( "XQuery: " + query, 0, result.getSize() );
			} catch (XMLDBException e) {				
				message = e.getMessage();				
			}
			//This is the good result !
			//assertTrue(message.indexOf("XQST0048") > -1);
			
		} catch (XMLDBException e) {
			System.out.println("testModule : XMLDBException: "+e);
			fail(e.getMessage());
		}
	}	
	
	public void testFunctionDoc() {
		ResourceSet result;
		String query;
		boolean exceptionThrown;
		String message;		
		try {
			XPathQueryService service = 
				storeXMLStringAndGetQueryService(NUMBERS_XML, numbers);

			System.out.println("testFunctionDoc 1: ========" );				
			query ="doc('" + DBBroker.ROOT_COLLECTION + "/test/" + NUMBERS_XML +  "')";	
			result = service.query(query);
			assertEquals( "XQuery: " + query, 1, result.getSize() );	
			try {				
				Node n = ((XMLResource)result.getResource(0)).getContentAsDOM();	
				DetailedDiff d = new DetailedDiff(compareXML(numbers, n.toString()));
                assertEquals(0, d.getAllDifferences().size());
				//ignore eXist namespace's attributes				
				//assertEquals(1, d.getAllDifferences().size());
			} catch (Exception e) {
				System.out.println("testFunctionDoc : XMLDBException: "+e);
				fail(e.getMessage());
			}			
			
			System.out.println("testFunctionDoc 2: ========" );				
			query = "let $v := ()\n" 
				+ "return doc($v)";	
			result = service.query(query);
			assertEquals( "XQuery: " + query, 0, result.getSize() );			
			
			System.out.println("testFunctionDoc 3: ========" );		
			query ="doc('" + DBBroker.ROOT_COLLECTION + "/test/dummy" + NUMBERS_XML +  "')";	
			try {
				exceptionThrown = false;
				result = service.query(query);		
			} catch (XMLDBException e) {
				exceptionThrown = true;
				message = e.getMessage();
			}
			//TODO : to be decided !
			//assertTrue(exceptionThrown);
			assertEquals(0, result.getSize());						
			
			System.out.println("testFunctionDoc 4: ========" );				
			query ="doc-available('" + DBBroker.ROOT_COLLECTION + "/test/" + NUMBERS_XML +  "')";	
			result = service.query(query);
			assertEquals( "XQuery: " + query, 1, result.getSize() );
			assertEquals( "XQuery: " + query, "true", result.getResource(0).getContent());			
			
			System.out.println("testFunctionDoc 5: ========" );				
			query = "let $v := ()\n" 
				+ "return doc-available($v)";	
			result = service.query(query); 
			assertEquals( "XQuery: " + query, 1, result.getSize() );
			assertEquals( "XQuery: " + query, "false", result.getResource(0).getContent());
			
			System.out.println("testFunctionDoc 6: ========" );		
			query ="doc-available('" + DBBroker.ROOT_COLLECTION + "/test/dummy" + NUMBERS_XML +  "')";	
			assertEquals( "XQuery: " + query, 1, result.getSize() );
			assertEquals( "XQuery: " + query, "false", result.getResource(0).getContent());				
			
		} catch (XMLDBException e) {
			System.out.println("testFunctionDoc : XMLDBException: "+e);
			fail(e.getMessage());
		}
	}	
	
	//This test only works if there is an Internet access
	public void testFunctionDocExternal() {
		boolean hasInternetAccess = false;
		ResourceSet result;
		String query;
		boolean exceptionThrown;
		String message;		
		
		//Checking that we have an Internet Aceess
		try {
			URL url = new URL("http://www.w3.org/");
			URLConnection con = url.openConnection();
			if (con instanceof HttpURLConnection) {
				HttpURLConnection httpConnection = (HttpURLConnection)con;
				hasInternetAccess = (httpConnection.getResponseCode() == HttpURLConnection.HTTP_OK);
			}
		} catch (MalformedURLException e) {
			fail("Stupid error... " + e.getMessage());					
		} catch (IOException e) {	
			//Ignore
		}
		
		if (!hasInternetAccess) {
			System.out.println("No Internet access: skipping 'testFunctionDocExternal' tests");
			return;
		}
		
		try {
			XPathQueryService service = 
				storeXMLStringAndGetQueryService(NUMBERS_XML, numbers);
			
			System.out.println("testFunctionDocExternal 1: ========" );				
			query ="doc(\"http://www.w3.org/RDF/\")";	
			result = service.query(query);
			assertEquals( "XQuery: " + query, 1, result.getSize() );			
			
			System.out.println("testFunctionDocExternal 2: ========" );		
			query ="doc(\"http://www.w3.org/RDF/dummy\")";	
			try {
				exceptionThrown = false;
				result = service.query(query);		
			} catch (XMLDBException e) {
				exceptionThrown = true;
				message = e.getMessage();
			}
			assertTrue(exceptionThrown);		
			
			System.out.println("testFunctionDocExternal 3: ========" );				
			query ="doc-available(\"http://www.w3.org/RDF/\")";	
			result = service.query(query);
			assertEquals( "XQuery: " + query, 1, result.getSize() );
			assertEquals( "XQuery: " + query, "true", result.getResource(0).getContent());			
						
			System.out.println("testFunctionDocExternal 4: ========" );		
			query ="doc-available(\"http://www.404brain.net/true404\")";	
			result = service.query(query);
			assertEquals( "XQuery: " + query, 1, result.getSize() );
			assertEquals( "XQuery: " + query, "false", result.getResource(0).getContent());			

			System.out.println("testFunctionDocExternal 5: ========" );
			//A redirected 404
			query ="doc-available(\"http://java.sun.com/404\")";	
			assertEquals( "XQuery: " + query, 1, result.getSize() );
			assertEquals( "XQuery: " + query, "false", result.getResource(0).getContent());		
			
		} catch (XMLDBException e) {
			System.out.println("testFunctionDoc : XMLDBException: "+e);
			fail(e.getMessage());
		}
	}			
	
	private String makeString(int n){
		StringBuffer b = new StringBuffer();
		char c = 'a';
		for ( int i=0; i<n; i++ ) {
			b.append(c);
		}
		return b.toString();
	}
    
    public void testAttributeAxis() {
        ResourceSet result;
        String query;
        XMLResource resu;
        try {
            System.out.println("testAttributeAxis 1: ========" );
            String large = createXMLContentWithLargeString();
            XPathQueryService service = 
                storeXMLStringAndGetQueryService(file_name, xml);
            
            query = "let $node := (<c id=\"OK\">b</c>)/descendant-or-self::*/attribute::id "+
            "return <a>{$node}</a>";
            result = service.query(query );
            printResult(result);
            resu = (XMLResource) result.getResource(0);
            assertEquals( "XQuery: " + query, "OK", ((Element)resu.getContentAsDOM()).getAttribute("id"));    
        } catch (XMLDBException e) {
            System.out.println("testAttributeAxis(): XMLDBException: "+e);
            fail(e.getMessage());
        }
    }    

    public void testLargeAttributeSimple() {
        ResourceSet result;
        String query;
        XMLResource resu;
        try {
            System.out.println("testLargeAttributeSimple 1: ========" );
            String large = createXMLContentWithLargeString();
            XPathQueryService service = 
                storeXMLStringAndGetQueryService(file_name, xml);
            
            query = "doc('"+ file_name+"') / details/metadata[@docid= '" + large + "' ]";
            result = service.queryResource(file_name, query );
            printResult(result);
            assertEquals( "XQuery: " + query, nbElem, result.getSize() );
        } catch (XMLDBException e) {
            System.out.println("testLargeAttributeSimple(): XMLDBException: "+e);
            fail(e.getMessage());
        }
    }

	public void testLargeAttributeContains() {
		ResourceSet result;
		String query;
		XMLResource resu;
		try {
			System.out.println("testLargeAttributeSimple 1: ========" );
			String large = createXMLContentWithLargeString();
			XPathQueryService service = 
				storeXMLStringAndGetQueryService(file_name, xml);

			query = "doc('"+ file_name+"') / details/metadata[ contains(@docid, 'aa') ]";
			result = service.queryResource(file_name, query );
			assertEquals( "XQuery: " + query, nbElem, result.getSize() );
		} catch (XMLDBException e) {
			System.out.println("testLargeAttributeSimple(): XMLDBException: "+e);
			fail(e.getMessage());
		}
	}

	public void testLargeAttributeKeywordOperator() {
		ResourceSet result;
		String query;
		XMLResource resu;
		try {
			System.out.println("testLargeAttributeSimple 1: ========" );
			String large = createXMLContentWithLargeString();
			XPathQueryService service = 
				storeXMLStringAndGetQueryService(file_name, xml);

			query = "doc('"+ file_name+"') / details/metadata[ @docid &= '" + large + "' ]";
			result = service.queryResource(file_name, query );
			assertEquals( "XQuery: " + query, nbElem, result.getSize() );
		} catch (XMLDBException e) {
			System.out.println("testLargeAttributeSimple(): XMLDBException: "+e);
			fail(e.getMessage());
		}
	}

    public void testNameConflicts() {
        String query = "let $a := <name name=\"Test\"/> return <wrap>{$a//@name}</wrap>";
        try {
            XPathQueryService service = (XPathQueryService) testCollection.getService(
                    "XPathQueryService", "1.0");
            ResourceSet result = service.query(query);
            assertEquals(1, result.getSize());
            assertEquals("<wrap name=\"Test\"/>", result.getResource(0).getContent().toString());
        } catch (XMLDBException e) {
            fail(e.getMessage());
        }
    }
    
    public void testSerialization() {
        ResourceSet result;
        String query;
        boolean exceptionThrown;
        String message;         
        
        try {
            XPathQueryService service = 
                storeXMLStringAndGetQueryService(NUMBERS_XML, numbers);    
            
            query = "let $a := <test><foo name='bar'/><foo name='bar'/></test>" +
            "return <attribute>{$a/foo/@name}</attribute>";
            try {
                message = "";   
                result = service.query(query);  
            } catch (XMLDBException e) {
                message = e.getMessage();               
            }             
            assertTrue(message.indexOf("XQDY0025") > -1); 
            
            query = "let $a := <foo name='bar'/> return $a/@name";            
            try {
                message = "";   
                result = service.query(query);
            } catch (XMLDBException e) {
                message = e.getMessage();               
            }  
            //TODO : how toserialize this resultand get the error ? -pb
            //assertTrue(message.indexOf("XQDY0025") > -1); 
        
        } catch (XMLDBException e) {
            System.out.println("testVariable : XMLDBException: "+e);
            fail(e.getMessage());
        }
    }       
        
	
	/** CAUTION side effect on field xml
	 * @return the large string contained in the atrbute(s)
	 */
	private String createXMLContentWithLargeString() {
		String large = makeString(stringSize);
		String head = "<details format='xml'>";
		String elem = "<metadata docid='" + large + "'></metadata>";
		String tail = "</details>";
		xml = head;
		for ( int i=0; i< nbElem; i++ )
			xml += elem;
		xml += tail;
		System.out.println("XML:\n" + xml);
		return large;
	}

	public void testRetrieveLargeAttribute() throws XMLDBException{
		System.out.println("testRetrieveLargeAttribute 1: ========" );
		XMLResource res = (XMLResource) testCollection.getResource(file_name);
		System.out.println("res.getContent(): " + res.getContent() );
	}
	
	/** This test is obsolete because testLargeAttributeSimple() reproduces the problem without a file,
	 * but I keep it to show how one can test with an XML file. */
	public void obsoleteTestLargeAttributeRealFile() {
		ResourceSet result;
		String query;
		XMLResource resu;
		try {
			System.out.println("testLargeAttributeRealFile 1: ========" );
			String large;
			large = "challengesininformationretrievalandlanguagemodelingreportofaworkshopheldatthecenterforintelligentinformationretrievaluniversityofmassachusettsamherstseptember2002-extdocid-howardturtlemarksandersonnorbertfuhralansmeatonjayaslamdragomirradevwesselkraaijellenvoorheesamitsinghaldonnaharmanjaypontejamiecallannicholasbelkinjohnlaffertylizliddyronirosenfeldvictorlavrenkodavidjharperrichschwartzjohnpragerchengxiangzhaijinxixusalimroukosstephenrobertsonandrewmccallumbrucecroftrmanmathasuedumaisdjoerdhiemstraeduardhovyralphweischedelthomashofmannjamesallanchrisbuckleyphilipresnikdavidlewis2003";
			if (attributeXML != null)
				large = attributeXML;
			String xml = "<details format='xml'><metadata docid='" + large +
				"'></metadata></details>";
			final String FILE_NAME = "detail_xml.xml";
			XPathQueryService service = 
				storeXMLStringAndGetQueryService(FILE_NAME);

			query = "doc('"+ FILE_NAME+"') / details/metadata[@docid= '" + large + "' ]"; // fails !!!
			// query = "doc('"+ FILE_NAME+"') / details/metadata[ docid= '" + large + "' ]"; // test passes!
			result = service.queryResource(FILE_NAME, query );
			printResult(result);
			assertEquals( "XQuery: " + query, 2, result.getSize() );
		} catch (XMLDBException e) {
			System.out.println("testLargeAttributeRealFile(): XMLDBException: "+e);
			fail(e.getMessage());
		}
	}
	
	public void bugtestXUpdateWithAdvancentTextNodes() {
		ResourceSet result;
		String query;	
		
        query = 
		"let $coll := xmldb:collection('/db', 'guest', 'guest')" +
		"let $name := xmldb:store($coll , 'xupdateTest.xml', <test>aaa</test>)" +
		"let $xu :=" +
		"<xu:modifications xmlns:xu='http://www.xmldb.org/xupdate' version='1.0'>" +
		  "<xu:append select='/test'>" +
		    "<xu:text>yyy</xu:text>" +
		  "</xu:append>" +
		"</xu:modifications>" +
		"let $count := xmldb:update($coll , $xu)" +
		"for $textNode in document('/db/xupdateTest.xml')/test/text()" +
		"	return <text id='{util:node-id($textNode)}'>{$textNode}</text>";
		
		try {
			XPathQueryService service = 
				storeXMLStringAndGetQueryService(NUMBERS_XML, numbers);
	
            System.out.println("testXUpdateWithAdvancentTextNodes 1: ========" );
            result = service.query(query);              
            assertEquals( "XQuery: " + query, 1, result.getSize() );
		} catch (XMLDBException e) {
			System.out.println("testXUpdateWithAdvancentTextNodes(): XMLDBException: "+ e);
			fail(e.getMessage());
		}
	}

	//TODO : understand this test and make sure that the expected result is correct
	//expected:<3> but was:<2>
	public void bugtestXUpdateAttributesAndElements() {
		ResourceSet result;
		String query;	
		
        query = 
		"declare function local:update-game($game) {\n" +
		"local:update-frames($game),\n" +
		"update insert\n" +
		"<stats>\n" +
		"<strikes>4</strikes>\n" +
		"<spares>\n" +
		"<attempted>4</attempted>\n" +
		"</spares>\n" +
		"</stats>\n" +
		"into $game\n" +
		"};\n" +
		"declare function local:update-frames($game) {\n" +
		// Uncomment this, and it works:
		//"for $frame in $game/frame return update insert <processed/> into $frame,\n" +
		"for $frame in $game/frame\n" +
		"return update insert attribute points {4} into $frame\n" +
		"};\n" +
		"let $series := document('bowling.xml')/series\n" +
		"let $nul1 := for $game in $series/game return local:update-game($game)\n" +
		"return $series/game/stats\n";
		
		try {
			XPathQueryService service = 
				storeXMLStringAndGetQueryService(BOWLING_XML, bowling);
	
            System.out.println("testXUpdateAttributesAndElements 1: ========" );
            result = service.query(query);              
            assertEquals( "XQuery: " + query, 3, result.getSize() );
		} catch (XMLDBException e) {
			System.out.println("testXUpdateAttributesAndElements(): XMLDBException: "+ e);
			fail(e.getMessage());
		}
	}
	/**
	 * @return
	 * @throws XMLDBException
	 */
	private XPathQueryService storeXMLStringAndGetQueryService(String documentName,
			String content) throws XMLDBException {
		XMLResource doc =
			(XMLResource) testCollection.createResource(
					documentName, "XMLResource" );
		doc.setContent(content);
		testCollection.storeResource(doc);
		XPathQueryService service =
			(XPathQueryService) testCollection.getService(
				"XPathQueryService",
				"1.0");
		return service;
	}

	/**
	 * @return
	 * @throws XMLDBException
	 */
	private XPathQueryService storeXMLStringAndGetQueryService(String documentName
			) throws XMLDBException {
		XMLResource doc =
			(XMLResource) testCollection.createResource(
					documentName, "XMLResource" );
		doc.setContent(new File(documentName));
		testCollection.storeResource(doc);
		XPathQueryService service =
			(XPathQueryService) testCollection.getService(
				"XPathQueryService",
				"1.0");
		return service;
	}

	/**
	 * @param result
	 * @throws XMLDBException
	 */
	private void printResult(ResourceSet result) throws XMLDBException {
		for (ResourceIterator i = result.getIterator();
			i.hasMoreResources(); ) {
			Resource r = i.nextResource();
			System.out.println(r.getContent());
		}
	}

	public static void main(String[] args) {
		if ( args.length > 0 ) {
			attributeXML = args[0];
		}
		stringSize = 513;
		if ( args.length > 1 ) {
			stringSize = Integer.parseInt( args[1] );
		}
		nbElem = 2;
		if ( args.length > 2 ) {
			nbElem = Integer.parseInt( args[2] );
		}

		junit.textui.TestRunner.run(XQueryTest.class);
	}
}
