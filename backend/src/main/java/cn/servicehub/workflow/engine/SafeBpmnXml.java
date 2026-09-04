package cn.servicehub.workflow.engine;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

/** Strict, bounded metadata grammar. No expression, script, extension, URI loading or entity resolution. */
public final class SafeBpmnXml {
    public static final String BPMN = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    public static final String BPMNDI = "http://www.omg.org/spec/BPMN/20100524/DI";
    public static final String DC = "http://www.omg.org/spec/DD/20100524/DC";
    public static final String DI = "http://www.omg.org/spec/DD/20100524/DI";
    public static final int MAX_XML = 512 * 1024;
    private static final Set<String> NODES = Set.of("startEvent", "endEvent", "userTask", "task", "manualTask", "serviceTask", "sendTask", "receiveTask", "businessRuleTask", "exclusiveGateway", "parallelGateway", "inclusiveGateway", "eventBasedGateway", "subProcess", "intermediateCatchEvent", "intermediateThrowEvent", "boundaryEvent");
    private static final Set<String> MODEL = new HashSet<>(NODES);
    private static final Set<String> ATTRIBUTES = Set.of("id", "name", "targetNamespace", "exporter", "exporterVersion", "isExecutable", "sourceRef", "targetRef", "processRef", "default", "gatewayDirection", "attachedToRef", "cancelActivity", "triggeredByEvent", "bpmnElement", "isHorizontal", "isExpanded", "isMarkerVisible", "x", "y", "width", "height", "labelStyle", "isInterrupting");
    static { MODEL.addAll(Set.of("definitions", "process", "sequenceFlow", "incoming", "outgoing", "collaboration", "participant", "laneSet", "lane", "flowNodeRef", "association", "textAnnotation", "text")); }
    private SafeBpmnXml() { }

    public static Document parse(String xml) {
        if (xml == null || xml.isBlank() || xml.length() > MAX_XML || xml.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_XML || xml.toUpperCase(Locale.ROOT).contains("<!DOCTYPE") || xml.toUpperCase(Locale.ROOT).contains("<!ENTITY")) throw invalid();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true); factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
                @Override public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
                @Override public void error(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
            });
            Document doc = builder.parse(new InputSource(new StringReader(xml)));
            if (!BPMN.equals(doc.getDocumentElement().getNamespaceURI()) || !"definitions".equals(doc.getDocumentElement().getLocalName())) throw invalid();
            return doc;
        } catch (IllegalArgumentException e) { throw e; } catch (Exception e) { throw invalid(); }
    }

    public static Set<String> validateDraft(String xml) {
        Document doc = parse(xml); Set<String> ids = new HashSet<>(), nodes = new HashSet<>();
        rejectInstructions(doc);
        NodeList elements = doc.getElementsByTagName("*");
        if (elements.getLength() > 4000 || doc.getElementsByTagNameNS(BPMN, "process").getLength() != 1) throw invalid();
        for (int i = 0; i < elements.getLength(); i++) {
            Element e = (Element) elements.item(i);
            if (!allowedElement(e)) throw invalid();
            int depth = 0; for (Node p = e; p != null; p = p.getParentNode()) if (++depth > 32) throw invalid();
            NamedNodeMap attributes = e.getAttributes();
            for (int a = 0; a < attributes.getLength(); a++) {
                Attr attr = (Attr) attributes.item(a);
                if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attr.getNamespaceURI())) continue;
                if (attr.getNamespaceURI() != null || !ATTRIBUTES.contains(attr.getName()) || attr.getValue().length() > 500 || attr.getValue().contains("${") || attr.getValue().contains("#{") || attr.getValue().contains("<") || attr.getValue().contains(">")) throw invalid();
                if (attr.getName().equals("isExecutable") && !Set.of("false", "0").contains(attr.getValue())) throw invalid();
                if (Set.of("x", "y", "width", "height").contains(attr.getName())) {
                    try { double n = Double.parseDouble(attr.getValue()); if (!Double.isFinite(n) || Math.abs(n) > 1_000_000 || ((attr.getName().equals("width") || attr.getName().equals("height")) && n <= 0)) throw invalid(); } catch (NumberFormatException bad) { throw invalid(); }
                }
            }
            String id = e.getAttribute("id");
            if (!id.isEmpty() && (!id.matches("^[A-Za-z_][A-Za-z0-9_.-]{0,127}$") || !ids.add(id))) throw invalid();
            if (BPMN.equals(e.getNamespaceURI()) && NODES.contains(e.getLocalName())) { if (id.isBlank()) throw invalid(); nodes.add(id); }
            if (e.getTextContent().length() > MAX_XML) throw invalid();
        }
        for (int i = 0; i < elements.getLength(); i++) {
            Element e = (Element) elements.item(i);
            for (String reference : List.of("sourceRef", "targetRef", "processRef", "attachedToRef", "default", "bpmnElement")) if (e.hasAttribute(reference) && !ids.contains(e.getAttribute(reference))) throw invalid();
            if (BPMN.equals(e.getNamespaceURI()) && Set.of("incoming", "outgoing", "flowNodeRef").contains(e.getLocalName()) && !ids.contains(e.getTextContent().trim())) throw invalid();
        }
        return Set.copyOf(nodes);
    }
    private static void rejectInstructions(Node root) {
        var queue=new ArrayDeque<Node>();queue.add(root);
        while(!queue.isEmpty()){Node n=queue.removeFirst();if(n.getNodeType()==Node.PROCESSING_INSTRUCTION_NODE)throw invalid();for(Node c=n.getFirstChild();c!=null;c=c.getNextSibling())queue.addLast(c);}
    }

    /** Preserve only diagram metadata from an already deployed definition. Never include implementation attributes. */
    public static Projection project(String xml, String processKey) {
        Document doc = parse(xml);
        scrub(doc.getDocumentElement());
        NodeList processes = doc.getElementsByTagNameNS(BPMN, "process");
        Element process = null;
        for (int i = processes.getLength() - 1; i >= 0; i--) {
            Element p = (Element) processes.item(i);
            if (processKey.equals(p.getAttribute("id"))) process = p; else p.getParentNode().removeChild(p);
        }
        if (process == null) throw invalid();
        process.setAttribute("isExecutable", "false");
        boolean authored = doc.getElementsByTagNameNS(BPMNDI, "BPMNShape").getLength() > 0;
        if (!authored) addLayout(doc, process);
        return new Projection(serialize(doc), authored ? "AUTHORED" : "GENERATED");
    }
    private static void scrub(Element e) {
        NamedNodeMap attrs = e.getAttributes();
        for (int i = attrs.getLength() - 1; i >= 0; i--) {
            Attr a = (Attr) attrs.item(i);
            if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(a.getNamespaceURI())) {
                if (!Set.of(BPMN, BPMNDI, DC, DI).contains(a.getValue())) e.removeAttributeNode(a);
            } else if (a.getNamespaceURI() != null || !ATTRIBUTES.contains(a.getName())) e.removeAttributeNode(a);
        }
        for (Node child = e.getFirstChild(); child != null;) {
            Node next = child.getNextSibling();
            if (child instanceof Element ce) { if (allowedElement(ce)) scrub(ce); else e.removeChild(ce); }
            else if (child.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE || child.getNodeType() == Node.COMMENT_NODE) e.removeChild(child);
            child = next;
        }
    }
    private static boolean allowedElement(Element e) {
        return BPMN.equals(e.getNamespaceURI()) ? MODEL.contains(e.getLocalName())
            : BPMNDI.equals(e.getNamespaceURI()) ? Set.of("BPMNDiagram", "BPMNPlane", "BPMNShape", "BPMNEdge", "BPMNLabel").contains(e.getLocalName())
            : DC.equals(e.getNamespaceURI()) ? "Bounds".equals(e.getLocalName())
            : DI.equals(e.getNamespaceURI()) && "waypoint".equals(e.getLocalName());
    }
    private static void addLayout(Document doc, Element process) {
        Element root = doc.getDocumentElement();
        root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:bpmndi", BPMNDI);
        root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:dc", DC);
        root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:di", DI);
        Element diagram = element(doc, BPMNDI, "bpmndi:BPMNDiagram", "id", "ServiceHubDisplayDiagram"); root.appendChild(diagram);
        Element plane = element(doc, BPMNDI, "bpmndi:BPMNPlane", "id", "ServiceHubDisplayPlane", "bpmnElement", process.getAttribute("id")); diagram.appendChild(plane);
        Map<String, double[]> positions = new LinkedHashMap<>();
        NodeList all = process.getElementsByTagNameNS(BPMN, "*"); int index = 0;
        for (int i = 0; i < all.getLength(); i++) {
            Element node = (Element) all.item(i); if (!NODES.contains(node.getLocalName())) continue;
            boolean round = node.getLocalName().endsWith("Event"); boolean gateway = node.getLocalName().endsWith("Gateway");
            double width = round ? 36 : gateway ? 50 : 120, height = round ? 36 : gateway ? 50 : 80;
            double x = 70 + index++ * 175, y = 150 - height / 2; positions.put(node.getAttribute("id"), new double[]{x,y,width,height});
            Element shape = element(doc, BPMNDI, "bpmndi:BPMNShape", "id", node.getAttribute("id") + "_display", "bpmnElement", node.getAttribute("id"));
            shape.appendChild(element(doc, DC, "dc:Bounds", "x", ""+x, "y", ""+y, "width", ""+width, "height", ""+height)); plane.appendChild(shape);
        }
        NodeList flows = process.getElementsByTagNameNS(BPMN, "sequenceFlow");
        for (int i = 0; i < flows.getLength(); i++) {
            Element flow = (Element) flows.item(i); double[] s = positions.get(flow.getAttribute("sourceRef")), t = positions.get(flow.getAttribute("targetRef"));
            if (s == null || t == null) continue;
            Element edge = element(doc, BPMNDI, "bpmndi:BPMNEdge", "id", flow.getAttribute("id") + "_display", "bpmnElement", flow.getAttribute("id"));
            edge.appendChild(element(doc, DI, "di:waypoint", "x", ""+(s[0]+s[2]), "y", ""+(s[1]+s[3]/2)));
            edge.appendChild(element(doc, DI, "di:waypoint", "x", ""+t[0], "y", ""+(t[1]+t[3]/2))); plane.appendChild(edge);
        }
    }
    private static Element element(Document doc, String ns, String name, String... attrs) { Element e = doc.createElementNS(ns, name); for (int i=0;i<attrs.length;i+=2) e.setAttribute(attrs[i],attrs[i+1]); return e; }
    private static String serialize(Document doc) { try { TransformerFactory f = TransformerFactory.newInstance(); f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); f.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, ""); StringWriter out=new StringWriter(); f.newTransformer().transform(new DOMSource(doc),new StreamResult(out)); return out.toString(); } catch(Exception e) { throw invalid(); } }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("BPMN metadata is invalid or unsupported"); }
    public record Projection(String xml, String layoutSource) { }
}
