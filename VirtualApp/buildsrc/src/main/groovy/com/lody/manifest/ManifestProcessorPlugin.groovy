/*
 * Copyright (C) 2016 Baidu, Inc. All Rights Reserved.
 */
package com.lody.manifest

import org.apache.commons.io.FileUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException
import org.w3c.dom.*
import org.xml.sax.InputSource

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.Transformer
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import javax.xml.xpath.XPath
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathExpressionException

public class ManifestProcessorPlugin implements Plugin<Project> {

    private XPath mXPath;

    private ManifestExtension vaConfig;

    public static final String EXTENSION_NAME = "va_conf";

    public static final String NS_RESOURCES =
            "http://schemas.android.com/apk/res/android";

    public static final String APP_PREFIX = "app";
    public static final String ANDROID_URI =
            "http://schemas.android.com/apk/res/android";
    public static final String XMLNS_URI = "http://www.w3.org/2000/xmlns/";
    public static final String XMLNS = "xmlns";
    public static final String XMLNS_PREFIX = "xmlns:";
    public static final String ANDROID_NS_NAME = "android";
    public static final String ANDROID_NS_NAME_PREFIX = "android:";


    private static final String providerTemplate = "<provider\n" +
            "            android:name=\"com.lody.virtual.client.stub.StubContentProvider\$C%d\"\n" +
            "            android:authorities=\"virtual.client.stub.StubContentProvider%d\"\n" +
            "            android:exported=\"false\"\n" +
            "            android:process=\":p%d\">\n" +
            "            <meta-data\n" +
            "                android:name=\"X-Identity\"\n" +
            "                android:value=\"Stub-User\"/>\n" +
            "        </provider>"

    private static final String activityTemplate = "<activity\n" +
            "            android:name=\"com.lody.virtual.client.stub.StubActivity\$C%d\"\n" +
            "            android:configChanges=\"mcc|mnc|locale|touchscreen|keyboard|keyboardHidden|navigation|orientation|screenLayout|uiMode|screenSize|smallestScreenSize|fontScale\"\n" +
            "            android:process=\":p%d\"\n" +
            "            android:taskAffinity=\"com.lody.virtual.vt\"\n" +
            "            android:theme=\"@style/VATheme\">\n" +
            "            <meta-data\n" +
            "                android:name=\"X-Identity\"\n" +
            "                android:value=\"Stub-User\"/>\n" +
            "        </activity>";

    private static final String activityDialogThemeTemplate = "<activity\n" +
            "            android:name=\"com.lody.virtual.client.stub.StubActivity\$C%d_\"\n" +
            "            android:configChanges=\"mcc|mnc|locale|touchscreen|keyboard|keyboardHidden|navigation|orientation|screenLayout|uiMode|screenSize|smallestScreenSize|fontScale\"\n" +
            "            android:process=\":p%d\"\n" +
            "            android:taskAffinity=\"com.lody.virtual.vt\"\n" +
            "            android:theme=\"@android:style/Theme.Dialog\">\n" +
            "            <meta-data\n" +
            "                android:name=\"X-Identity\"\n" +
            "                android:value=\"Stub-User\"/>\n" +
            "        </activity>";

    @Override
    void apply(Project project) {


        if (!["com.android.application",
              "android",
              "com.android.module.application"].any { project.plugins.findPlugin(it) }) {
            throw new ProjectConfigurationException("Please apply 'com.android.application' " +
                    "or 'android' " +
                    "or 'com.android.module.application' plugin", null)
        }
        project.extensions.create(EXTENSION_NAME, ManifestExtension);
        applyTask(project);
    }

    void applyTask(Project project) {
        try {
            vaConfig = ManifestExtension.getConfig(project);
        } catch (Exception ex) {
            ex.printStackTrace()
            vaConfig = new ManifestExtension()
        }
        project.tasks.whenTaskAdded {
            task ->
                if (task.name.startsWith("process") && task.name.endsWith("Manifest")) {
                    task.doLast {
                        final def task1 = task;
                        parseDocument(task1.manifestOutputFile)
                    }
                }
        }
    }

    void parseDocument(final File xmlFile) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            Reader reader = getUtfReader(xmlFile);
            InputSource is = new InputSource(reader);
//            factory.setNamespaceAware(true);
            factory.setValidating(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(is);

            String prefix = lookupNamespacePrefix(doc, NS_RESOURCES);
            mXPath = AndroidXPathFactory.newXPath(prefix);

            def path = "/manifest/application/activity";

            int pos = path.lastIndexOf('/');
            assert pos > 1;
            String parentPath = path.substring(0, pos);
            Element parent = findFirstElement(doc, parentPath);

            assert parent != null;
            if (parent == null) {
                return;
            }

            int stubCount = vaConfig.stub_count;

            for (int i = 0; i < stubCount; i++) {
                Node fragmentNode = builder.parse(
                        new InputSource(new StringReader(String.format(providerTemplate, i, i, i))))
                        .getDocumentElement();
                fragmentNode = doc.importNode(fragmentNode, true);
                parent.appendChild(fragmentNode);

            }
            for (int i = 0; i < stubCount; i++) {
                Node fragmentNode = builder.parse(
                        new InputSource(new StringReader(String.format(activityTemplate, i, i))))
                        .getDocumentElement();
                fragmentNode = doc.importNode(fragmentNode, true);
                parent.appendChild(fragmentNode);

            }
            for (int i = 0; i < stubCount; i++) {
                Node fragmentNode = builder.parse(
                        new InputSource(new StringReader(String.format(activityDialogThemeTemplate, i, i))))
                        .getDocumentElement();
                fragmentNode = doc.importNode(fragmentNode, true);
                parent.appendChild(fragmentNode);

            }


            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(xmlFile);
            transformer.transform(source, result);

        } catch (FileNotFoundException e) {

        } catch (Exception e) {
            e.printStackTrace()
        }

    }

    private Element findFirstElement(
            Document doc,
            String path) {
        Node result;
        try {
            result = (Node) mXPath.evaluate(path, doc, XPathConstants.NODE);
            if (result instanceof Element) {
                return (Element) result;
            }

        } catch (XPathExpressionException e) {
        }
        return null;
    }


    public static Reader getUtfReader(File file) throws IOException {
        byte[] bytes = FileUtils.readFileToByteArray(file);
        int length = bytes.length;
        if (length == 0) {
            return new StringReader("");
        }

        return new InputStreamReader(new ByteArrayInputStream(bytes));
    }

    public static String lookupNamespacePrefix(Node node, String nsUri) {
        String defaultPrefix = ANDROID_URI.equals(nsUri) ? ANDROID_NS_NAME : APP_PREFIX;
        return lookupNamespacePrefix(node, nsUri, defaultPrefix, true /*create*/);
    }

    public static String lookupNamespacePrefix(
            Node node, String nsUri, String defaultPrefix,
            boolean create) {
        if (nsUri == null) {
            return null;
        }

        if (XMLNS_URI.equals(nsUri)) {
            return XMLNS;
        }

        HashSet<String> visited = new HashSet<String>();
        Document doc = node == null ? null : node.getOwnerDocument();

        String nsPrefix = null;
        try {
            nsPrefix = doc != null ? doc.lookupPrefix(nsUri) : null;
            if (nsPrefix != null) {
                return nsPrefix;
            }
        } catch (Throwable t) {
            // ignore
        }

        for (; node != null && node.getNodeType() == Node.ELEMENT_NODE;
               node = node.getParentNode()) {
            NamedNodeMap attrs = node.getAttributes();
            for (int n = attrs.getLength() - 1; n >= 0; --n) {
                Node attr = attrs.item(n);
                if (XMLNS.equals(attr.getPrefix())) {
                    String uri = attr.getNodeValue();
                    nsPrefix = attr.getLocalName();
                    if (nsUri.equals(uri)) {
                        return nsPrefix;
                    }
                    visited.add(nsPrefix);
                }
            }
        }

        if (defaultPrefix == null) {
            return null;
        }

        String prefix = defaultPrefix;
        String base = prefix;
        for (int i = 1; visited.contains(prefix); i++) {
            prefix = base + Integer.toString(i);
        }
        if (doc != null) {
            node = doc.getFirstChild();
            while (node != null && node.getNodeType() != Node.ELEMENT_NODE) {
                node = node.getNextSibling();
            }
            if (node != null && create) {
                Attr attr = doc.createAttributeNS(XMLNS_URI, XMLNS_PREFIX + prefix);
                attr.setValue(nsUri);
                node.getAttributes().setNamedItemNS(attr);
            }
        }

        return prefix;
    }


}

