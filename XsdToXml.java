package XsdToDtd;

import java.io.IOException;
import java.io.StringWriter;

import java.net.URL;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.namespace.QName;
import javax.xml.transform.stream.StreamResult;

import org.apache.xerces.xs.XSModel;
import org.apache.xmlbeans.SchemaGlobalElement;
import org.apache.xmlbeans.SchemaTypeSystem;
import org.apache.xmlbeans.XmlBeans;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;

import jlibs.xml.sax.XMLDocument;
import jlibs.xml.xsd.XSInstance;
import jlibs.xml.xsd.XSParser;

/**
 *
 * @author ANONYMOUS
 */
public class XsdToXml {
    URL fichier;

    public String generateXML(URL fileURL) throws Exception {
        fichier = fileURL;

        String xmlResult = "";

        try {
            List<QName> globalElements = getGlobalElements();
            String      path           = fichier.getFile();

            if (globalElements.isEmpty()) {
                throw new Exception("ERREUR.");
            }

            System.out.println("Size: " + globalElements.size());
            globalElements.forEach(
                (QName root) -> {
                    System.out.println("  " + root);
                });

            QName rootElement = globalElements.iterator().next();

            System.out.println("RootElement " + rootElement);

            XSModel    xsModel    = new XSParser().parse(path);
            XSInstance xsInstance = new XSInstance();

            xsInstance.generateAllChoices         = Boolean.TRUE;
            xsInstance.generateOptionalElements   = Boolean.TRUE;
            xsInstance.generateOptionalAttributes = Boolean.TRUE;
            xsInstance.generateFixedAttributes    = Boolean.TRUE;
            xsInstance.generateDefaultAttributes  = Boolean.TRUE;

            StringWriter outWriter = new StringWriter();
            XMLDocument  sampleXml = new XMLDocument(new StreamResult(outWriter), true, 4, null);

            xsInstance.generate(xsModel, rootElement, sampleXml);

            String xmlString = outWriter.getBuffer().toString();

            xmlResult = XmlObject.Factory.parse(xmlString).toString();
        } catch (XmlException | IOException ex) {
            Logger.getLogger(XsdToXml.class.getName()).log(Level.SEVERE, null, ex);
        }

        return xmlResult;
    }

    public List<QName> getGlobalElements() throws XmlException, IOException {
        SchemaTypeSystem sts = XmlBeans.compileXsd(new XmlObject[] { XmlObject.Factory.parse(fichier) },
                                                   XmlBeans.getBuiltinTypeSystem(),
                                                   null);
        List<QName> qnames = new ArrayList();

        for (SchemaGlobalElement el : sts.globalElements()) {
            qnames.add(el.getName());
        }

        return qnames;
    }
}


//~ Formatted by Jindent --- http://www.jindent.com
