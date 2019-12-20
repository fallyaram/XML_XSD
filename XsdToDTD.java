package XsdToDtd;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import java.util.Enumeration;
import java.util.Iterator;
import java.util.Set;
import java.util.Stack;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 *
 * @author ANONYMOUS
 */
public class XsdToDTD extends org.xml.sax.helpers.DefaultHandler {
    protected static int MIN_ENUMERATION_INSTANCES = 10;
    protected static int MAX_ENUMERATION_VALUES    = 20;
    protected static int MIN_ENUMERATION_RATIO     = 3;
    protected static int MIN_FIXED                 = 5;
    protected static int MIN_ID_VALUES             = 10;
    protected static int MAX_ID_VALUES             = 100000;
    TreeMap              elementList;     // alphabetical list of element types appearing in the document;
    Stack                elementStack;    // stack of elements currently open; each entry is a StackEntry

    // object
    public XsdToDTD() {
        elementList  = new TreeMap();
        elementStack = new Stack();
    }

    public void characters(char ch[], int start, int length) throws SAXException {
        ElementDetails ed = ((StackEntry) elementStack.peek()).elementDetails;

        if (!ed.hasCharacterContent) {
            for (int i = start; i < start + length; i++) {
                if ((int) ch[i] > 0x20) {
                    ed.hasCharacterContent = true;

                    break;
                }
            }
        }
    }

    public void endElement(String uri, String localName, String name) throws SAXException {
        ElementDetails ed = (ElementDetails) elementList.get(name);

        if (ed.sequenced) {
            StackEntry se  = (StackEntry) elementStack.peek();
            int        seq = se.sequenceNumber;

            for (int i = seq + 1; i < ed.childseq.size(); i++) {
                ((ChildDetails) ed.childseq.elementAt(i)).optional = true;
            }
        }

        elementStack.pop();
    }

    private static String escape(String in) {
        char[] dest   = new char[in.length() * 8];
        int    newlen = escape(in.toCharArray(), 0, in.length(), dest);

        return new String(dest, 0, newlen);
    }

    private static int escape(char ch[], int start, int length, char[] out) {
        int o = 0;

        for (int i = start; i < start + length; i++) {
            if (ch[i] == '<') {
                ("&lt;").getChars(0, 4, out, o);
                o += 4;
            } else if (ch[i] == '>') {
                ("&gt;").getChars(0, 4, out, o);
                o += 4;
            } else if (ch[i] == '&') {
                ("&amp;").getChars(0, 5, out, o);
                o += 5;
            } else if (ch[i] == '\"') {
                ("&#34;").getChars(0, 5, out, o);
                o += 5;
            } else if (ch[i] == '\'') {
                ("&#39;").getChars(0, 5, out, o);
                o += 5;
            } else if (ch[i] <= 0x7f) {
                out[o++] = ch[i];
            } else {
                String dec = "&#" + Integer.toString((int) ch[i]) + ';';

                dec.getChars(0, dec.length(), out, o);
                o += dec.length();
            }
        }

        return o;
    }

    public String printDTD() {
        String   dtdResult = "";
        Iterator e         = elementList.keySet().iterator();

        while (e.hasNext()) {
            String         elementname = (String) e.next();
            ElementDetails ed          = (ElementDetails) elementList.get(elementname);
            TreeMap        children    = ed.children;
            Set            childKeys   = children.keySet();

            // EMPTY content
            if (childKeys.isEmpty() &&!ed.hasCharacterContent) {
                dtdResult = dtdResult.concat("<!ELEMENT " + elementname + " EMPTY >\n");
            }

            // CHARACTER content
            if (childKeys.isEmpty() && ed.hasCharacterContent) {
                dtdResult = dtdResult.concat("<!ELEMENT " + elementname + " ( #PCDATA ) >\n");
            }

            // ELEMENT content
            if ((childKeys.size() > 0) &&!ed.hasCharacterContent) {
                dtdResult = dtdResult.concat("<!ELEMENT " + elementname + " ( ");

                if (ed.sequenced) {

                    // all elements of this type have the same child elements
                    // in the same sequence, retained in the childseq vector
                    Enumeration c = ed.childseq.elements();

                    while (true) {
                        ChildDetails ch = (ChildDetails) c.nextElement();

                        dtdResult = dtdResult.concat(ch.name);

                        if (ch.repeatable &&!ch.optional) {
                            dtdResult = dtdResult.concat("+");
                        }

                        if (ch.repeatable && ch.optional) {
                            dtdResult = dtdResult.concat("*");
                        }

                        if (ch.optional &&!ch.repeatable) {
                            dtdResult = dtdResult.concat("?");
                        }

                        if (c.hasMoreElements()) {
                            dtdResult = dtdResult.concat(", ");
                        } else {
                            break;
                        }
                    }

                    dtdResult = dtdResult.concat(" ) >\n");
                } else {
                    Iterator c1 = childKeys.iterator();

                    while (c1.hasNext()) {
                        dtdResult = dtdResult.concat((String) c1.next());

                        if (c1.hasNext()) {
                            dtdResult = dtdResult.concat(" | ");
                        }
                    }

                    dtdResult = dtdResult.concat(" )* >\n");
                }
            }

            if ((childKeys.size() > 0) && ed.hasCharacterContent) {
                dtdResult = dtdResult.concat("<!ELEMENT " + elementname + " ( #PCDATA");

                Iterator c2 = childKeys.iterator();

                while (c2.hasNext()) {
                    dtdResult = dtdResult.concat(" | " + (String) c2.next());
                }

                dtdResult = dtdResult.concat(" )* >\n");
            }

            TreeMap  attlist = ed.attributes;
            boolean  doneID  = false;    // to ensure we have at most one ID attribute per element
            Iterator a       = attlist.keySet().iterator();

            while (a.hasNext()) {
                String           attname = (String) a.next();
                AttributeDetails ad      = (AttributeDetails) attlist.get(attname);

                // If the attribute is present on every instance of the element, treat it as required
                boolean required = (ad.occurrences == ed.occurrences);
                boolean isid     = ad.allNames &&    // ID values must be Names
                        (!doneID) &&                 // Only allowed one ID attribute per element type
                        (ad.unique) && (ad.occurrences >= MIN_ID_VALUES);
                boolean isfixed  = required && (ad.values.size() == 1) && (ad.occurrences >= MIN_FIXED);
                boolean isenum   = ad.allNMTOKENs
                                   &&                // Enumeration values must be NMTOKENs
                        (ad.occurrences >= MIN_ENUMERATION_INSTANCES)
                                   && (ad.values.size() <= ad.occurrences / MIN_ENUMERATION_RATIO)
                                   && (ad.values.size() <= MAX_ENUMERATION_VALUES);

                dtdResult = dtdResult.concat("<!ATTLIST " + elementname + " " + attname + " ");

                String tokentype = (ad.allNMTOKENs
                                    ? "NMTOKEN"
                                    : "CDATA");

                if (isid) {
                    dtdResult = dtdResult.concat("ID");
                    doneID    = true;
                } else if (isfixed) {
                    String val = (String) ad.values.first();

                    dtdResult = dtdResult.concat(tokentype + " #FIXED \"" + escape(val) + "\" >\n");
                } else if (isenum) {
                    dtdResult = dtdResult.concat("( ");

                    Iterator v = ad.values.iterator();

                    while (v.hasNext()) {
                        dtdResult = dtdResult.concat((String) v.next());

                        if (!v.hasNext()) {
                            break;
                        }

                        dtdResult = dtdResult.concat(" | ");
                    }

                    dtdResult = dtdResult.concat(" )");
                } else {
                    dtdResult = dtdResult.concat(tokentype);
                }

                if (!isfixed) {
                    if (required) {
                        dtdResult = dtdResult.concat(" #REQUIRED >\n");
                    } else {
                        dtdResult = dtdResult.concat(" #IMPLIED >\n");
                    }
                }
            }

            dtdResult = dtdResult.concat("\n");
            System.out.print("\n");
        }

        System.out.println(dtdResult);

        return dtdResult;
    }

    public void run(String filename) {
        try {
            File        file        = new File(filename);
            InputStream inputStream = new FileInputStream(file);
            Reader      reader      = new InputStreamReader(inputStream, "UTF-8");
            InputSource is          = new InputSource(reader);

            is.setEncoding("UTF-8");

            XMLReader parser = SAXParserFactory.newInstance().newSAXParser().getXMLReader();

            parser.setContentHandler(this);
            parser.parse(is);
        } catch (java.io.FileNotFoundException nf) {
            System.err.println("File " + filename + " not found");
        } catch (IOException | ParserConfigurationException | SAXException err) {
            System.err.println("Failed while parsing source file");
            System.err.println(err.getMessage());
            System.exit(2);
        }
    }

    public void startElement(String uri, String localName, String name, Attributes attributes) throws SAXException {
        StackEntry     se = new StackEntry();
        ElementDetails ed = (ElementDetails) elementList.get(name);

        if (ed == null) {
            ed = new ElementDetails(name);
            elementList.put(name, ed);
        }

        se.elementDetails = ed;
        se.sequenceNumber = -1;
        ed.occurrences++;

        for (int a = 0; a < attributes.getLength(); a++) {
            String           attName = attributes.getQName(a);
            String           val     = attributes.getValue(a);
            AttributeDetails ad      = (AttributeDetails) ed.attributes.get(attName);

            if (ad == null) {
                ad = new AttributeDetails(attName);
                ed.attributes.put(attName, ad);
            }

            if (!ad.values.contains(val)) {
                ad.values.add(val);

                if (ad.allNames &&!isValidName(val)) {
                    ad.allNames = false;
                }

                if (ad.allNMTOKENs &&!isValidNMTOKEN(val)) {
                    ad.allNMTOKENs = false;
                }

                if (ad.unique && ad.allNames && (ad.occurrences <= MAX_ID_VALUES)) {
                    ad.values.add(val);
                } else if (ad.values.size() <= MAX_ENUMERATION_VALUES) {
                    ad.values.add(val);
                }
            } else {

                // We've seen this attribute value before
                ad.unique = false;
            }

            ad.occurrences++;
        }

        // now keep track of the nesting and sequencing of child elements
        if (!elementStack.isEmpty()) {
            StackEntry     parent        = (StackEntry) elementStack.peek();
            ElementDetails parentDetails = parent.elementDetails;
            int            seq           = parent.sequenceNumber;

            // for sequencing, we're interested in consecutive groups of the same child element type
            boolean isFirstInGroup = ((parent.latestChild == null) || (!parent.latestChild.equals(name)));

            if (isFirstInGroup) {
                seq++;
                parent.sequenceNumber++;
            }

            parent.latestChild = name;

            // if we've seen this child of this parent before, get the details
            TreeMap      children = parentDetails.children;
            ChildDetails c        = (ChildDetails) children.get(name);

            if (c == null) {

                // this is the first time we've seen this child belonging to this parent
                c            = new ChildDetails();
                c.name       = name;
                c.position   = seq;
                c.repeatable = false;
                c.optional   = false;
                children.put(name, c);
                parentDetails.childseq.addElement(c);

                if (parentDetails.occurrences != 1) {
                    c.optional = true;
                }
            } else {
                if ((parentDetails.occurrences == 1) && isFirstInGroup) {
                    parentDetails.sequenced = false;
                }

                if ((parentDetails.childseq.size() <= seq)
                        ||!((ChildDetails) parentDetails.childseq.elementAt(seq)).name.equals(name)) {
                    parentDetails.sequenced = false;
                }
            }

            if (!isFirstInGroup) {
                c.repeatable = true;
            }
        }

        elementStack.push(se);
    }

    private boolean isValidNMTOKEN(String s) {
        if (s.length() == 0) {
            return false;
        }

        for (int i = 0; i < s.length(); i++) {
            int c = s.charAt(i);

            if (!(((c >= 0x41) && (c <= 0x5a))
                    || ((c >= 0x61) && (c <= 0x7a))
                    || ((c >= 0x30) && (c <= 0x39))
                    || (c == '.')
                    || (c == '_')
                    || (c == '-')
                    || (c == ':')
                    || (c > 128))) {
                return false;
            }
        }

        return true;
    }

    private boolean isValidName(String s) {
        if (!isValidNMTOKEN(s)) {
            return false;
        }

        int c = s.charAt(0);

        return !(((c >= 0x30) && (c <= 0x39)) || (c == '.') || (c == '-'));
    }

    /**
     * AttributeDetails is a data structure to keep information about attribute types
     */
    private class AttributeDetails {
        String  name;           // name of the attribute
        int     occurrences;    // number of occurrences of the attribute
        boolean unique;         // true if no duplicate values encountered
        TreeSet values;         // set of all distinct values encountered for this attribute
        boolean allNames;       // true if all the attribute values are valid names
        boolean allNMTOKENs;    // true if all the attribute values are valid NMTOKENs

        public AttributeDetails(String name) {
            this.name        = name;
            this.occurrences = 0;
            this.unique      = true;
            this.values      = new TreeSet();
            this.allNames    = true;
            this.allNMTOKENs = true;
        }
    }


    private class ChildDetails {
        String  name;
        int     position;
        boolean repeatable;
        boolean optional;
    }


    private class ElementDetails {
        String  name;
        int     occurrences;
        boolean hasCharacterContent;
        boolean sequenced;
        TreeMap children;
        Vector  childseq;
        TreeMap attributes;

        public ElementDetails(String name) {
            this.name                = name;
            this.occurrences         = 0;
            this.hasCharacterContent = false;
            this.sequenced           = true;
            this.children            = new TreeMap();
            this.childseq            = new Vector();
            this.attributes          = new TreeMap();
        }
    }


    private class StackEntry {
        ElementDetails elementDetails;
        int            sequenceNumber;
        String         latestChild;
    }
}


//~ Formatted by Jindent --- http://www.jindent.com
