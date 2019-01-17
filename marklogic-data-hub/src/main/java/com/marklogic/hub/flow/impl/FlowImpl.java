/*
 * Copyright 2012-2019 MarkLogic Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.marklogic.hub.flow.impl;

import com.marklogic.client.MarkLogicIOException;
import com.marklogic.hub.collector.Collector;
import com.marklogic.hub.collector.impl.CollectorImpl;
import com.marklogic.hub.flow.*;
import com.marklogic.hub.main.MainPlugin;
import com.marklogic.hub.main.impl.MainPluginImpl;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.util.Properties;

public class FlowImpl implements Flow {

    private String entityName;
    private String name;
    private FlowType type;
    private DataFormat dataFormat;
    private CodeFormat codeFormat;
    private Collector collector;
    private MainPlugin main;
    private String mappingName;

    public FlowImpl() {}

    @Override
    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    @Override
    public String getEntityName() {
        return entityName;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setMappingName(String mappingName) {
        this.mappingName = mappingName;
    }

    @Override
    public String getMappingName() {
        return mappingName;
    }

    @Override
    public void setType(FlowType type) {
        this.type = type;
    }

    @Override
    public FlowType getType() {
        return type;
    }

    @Override
    public void setDataFormat(DataFormat dataFormat) {
        this.dataFormat = dataFormat;
    }

    @Override
    public DataFormat getDataFormat() {
        return dataFormat;
    }

    @Override
    public void setCodeFormat(CodeFormat codeFormat) {
        this.codeFormat = codeFormat;
    }

    @Override
    public CodeFormat getCodeFormat() {
        return codeFormat;
    }

    @Override
    public Collector getCollector() {
        return collector;
    }

    @Override
    public void setCollector(Collector collector) {
        this.collector = collector;
    }

    @Override
    public MainPlugin getMain() {
        return main;
    }

    @Override
    public void setMain(MainPlugin main) {
        this.main = main;
    }

    private XMLStreamWriter makeXMLSerializer(StringWriter writer) {
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        factory.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, true);
        try {
            return factory.createXMLStreamWriter(writer);
        } catch (Exception e) {
            throw new MarkLogicIOException(e);
        }
    }

    @Override
    public String serialize() {
        try {
            StringWriter writer = new StringWriter();
            XMLStreamWriter serializer = makeXMLSerializer(writer);
            serializer.writeStartDocument();
            serializer.writeComment("This file is autogenerated. Please don't edit.");
            serializer.setDefaultNamespace("http://marklogic.com/data-hub");
            serializer.writeStartElement("flow");

            serializer.writeStartElement("name");
            serializer.writeCharacters(this.name);
            serializer.writeEndElement();

            serializer.writeStartElement("entity");
            serializer.writeCharacters(this.entityName);
            serializer.writeEndElement();

            serializer.writeStartElement("type");
            serializer.writeCharacters(this.type.toString());
            serializer.writeEndElement();

            if(this.type == FlowType.HARMONIZE && this.mappingName != null) {
                serializer.writeStartElement("mapping");
                serializer.writeCharacters(this.mappingName);
                serializer.writeEndElement();
            }

            serializer.writeStartElement("data-format");
            serializer.writeCharacters(this.dataFormat.toString());
            serializer.writeEndElement();

            serializer.writeStartElement("code-format");
            serializer.writeCharacters(this.codeFormat.toString());
            serializer.writeEndElement();

            String flowDir = "/entities/" + getEntityName() + "/" + getType().toString() + "/" + getName() + "/";
            if (this.collector != null) {
                serializer.writeStartElement("collector");
                serializer.writeAttribute("code-format", collector.getCodeFormat().toString());
                serializer.writeAttribute("module", flowDir + collector.getModule());
                serializer.writeEndElement();
            }

            if (this.main != null) {
                serializer.writeStartElement("main");
                serializer.writeAttribute("code-format", main.getCodeFormat().toString());
                serializer.writeAttribute("module", flowDir + main.getModule());
                serializer.writeEndElement();
            }

            serializer.writeEndElement();
            serializer.writeEndDocument();
            serializer.flush();
            serializer.close();

            StringWriter finalWriter = new StringWriter();

            Transformer t = TransformerFactory.newInstance().newTransformer();
            t.setOutputProperty(OutputKeys.INDENT, "yes");
            t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            t.transform(new StreamSource(new StringReader(writer.toString())), new StreamResult(finalWriter));

            return finalWriter.toString().replaceFirst("<!--", "\n<!--").replaceFirst("-->", "-->\n");
        }
        catch (NullPointerException e) {
            throw new MarkLogicIOException("Invalid properties file", e);
        }
        catch (Exception e) {
            throw new MarkLogicIOException(e);
        }
    }

    @Override
    public Properties toProperties() {
        Properties flowProperties = new Properties();
        flowProperties.setProperty("dataFormat", dataFormat.toString());
        flowProperties.setProperty("codeFormat", codeFormat.toString());
        if(mappingName != null) {
            flowProperties.setProperty("mapping", mappingName);
        }
        if (this.collector != null) {
            flowProperties.setProperty("collectorCodeFormat", collector.getCodeFormat().toString());
            flowProperties.setProperty("collectorModule", collector.getModule());
        }

        if (this.main != null) {
            flowProperties.setProperty("mainCodeFormat", main.getCodeFormat().toString());
            flowProperties.setProperty("mainModule", main.getModule());
        }

        return flowProperties;
    }

    public static Flow loadFromFile(File file) {
        Flow flow = null;
        FileInputStream is = null;
        try {
            is = new FileInputStream(file);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(is);
            flow = FlowImpl.fromXml(doc.getDocumentElement());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        finally {
            if (is != null) {
                try {
                    is.close();
                }
                catch(IOException e) {}
            }

        }
        return flow;
    }


    public static Flow fromXml(Node xml) {
        FlowBuilder flowBuilder = FlowBuilder.newFlow();
        NodeList children = xml.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            String nodeName = node.getLocalName();
            switch(nodeName) {
                case "name":
                    flowBuilder.withName(node.getTextContent());
                    break;
                case "data-format":
                    flowBuilder.withDataFormat(DataFormat.getDataFormat(node.getTextContent()));
                    break;
                case "code-format":
                    flowBuilder.withCodeFormat(CodeFormat.getCodeFormat(node.getTextContent()));
                    break;
                case "type":
                    flowBuilder.withType(FlowType.getFlowType(node.getTextContent()));
                    break;
                case "entity":
                    flowBuilder.withEntityName(node.getTextContent());
                    break;
                case "collector":
                    Collector collector = new CollectorImpl(
                        node.getAttributes().getNamedItem("module").getNodeValue(),
                        CodeFormat.getCodeFormat(node.getAttributes().getNamedItem("code-format").getNodeValue())
                    );
                    flowBuilder.withCollector(collector);
                    break;
                case "main":
                    MainPlugin main = new MainPluginImpl(
                        node.getAttributes().getNamedItem("module").getNodeValue(),
                        CodeFormat.getCodeFormat(node.getAttributes().getNamedItem("code-format").getNodeValue())
                    );
                    flowBuilder.withMain(main);
                    break;
                case "mapping":
                    flowBuilder.withMapping(node.getTextContent());
                    break;
            }
        }
        return flowBuilder.build();
    }

    @Override
    public String getFlowDbPath() {
        return "/entities/" + getEntityName() + "/" + getType().toString() + "/" + getName() + "/" + getName() + ".xml";
    }

}
