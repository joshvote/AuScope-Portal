package org.auscope.portal.csw;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathExpressionException;

/**
 * User: Mathew Wyatt
 * Date: 11/02/2009
 * Time: 11:58:21 AM
 */
//TODO: refactor into data and service records
public class CSWRecord {

    private static final Log logger = LogFactory.getLog(CSWRecord.class);
    
    private String serviceName;
    private CSWOnlineResource[] onlineResources;
    private String contactOrganisation;
    private String fileIdentifier;
    private String recordInfoUrl;
    private CSWGeographicElement cswGeographicElement;
    private String[] descriptiveKeywords;


    private String dataIdentificationAbstract;


    public CSWRecord(Node node) throws XPathExpressionException {

        XPath xPath = XPathFactory.newInstance().newXPath();
        Node tempNode = null;
        NodeList tempNodeList1 = null;
        NodeList tempNodeList2 = null;
        xPath.setNamespaceContext(new CSWNamespaceContext());
        

        String serviceTitleExpression = "gmd:identificationInfo/gmd:MD_DataIdentification/gmd:citation/gmd:CI_Citation/gmd:title/gco:CharacterString";
        tempNode = (Node)xPath.evaluate(serviceTitleExpression, node, XPathConstants.NODE);
        serviceName = tempNode != null ? tempNode.getTextContent() : "";

        String dataIdentificationAbstractExpression = "gmd:identificationInfo/gmd:MD_DataIdentification/gmd:abstract/gco:CharacterString";
        tempNode = (Node)xPath.evaluate(dataIdentificationAbstractExpression, node, XPathConstants.NODE);
        dataIdentificationAbstract = tempNode != null ? tempNode.getTextContent() : "";

        String contactOrganisationExpression = "gmd:contact/gmd:CI_ResponsibleParty/gmd:organisationName/gco:CharacterString";
        tempNode = (Node)xPath.evaluate(contactOrganisationExpression, node, XPathConstants.NODE);
        contactOrganisation = tempNode != null ? tempNode.getTextContent() : "";

        String fileIdentifierExpression = "gmd:fileIdentifier/gco:CharacterString";
        tempNode = (Node)xPath.evaluate(fileIdentifierExpression, node, XPathConstants.NODE);
        fileIdentifier = tempNode != null ? tempNode.getTextContent() : "";
        
        //There can be multiple gmd:onLine elements (which contain a number of fields we want)
        String onlineTransfersExpression = "gmd:distributionInfo/gmd:MD_Distribution/gmd:transferOptions/gmd:MD_DigitalTransferOptions/gmd:onLine";
        tempNodeList1 = (NodeList)xPath.evaluate(onlineTransfersExpression, node, XPathConstants.NODESET);
        List<CSWOnlineResource> resources = new ArrayList<CSWOnlineResource>();
        for (int i = 0; i < tempNodeList1.getLength(); i++) {
        	try {
        	    Node onlineNode = tempNodeList1.item(i);
        	    resources.add(CSWOnlineResourceFactory.parseFromNode(onlineNode, xPath));
        	} catch (IllegalArgumentException ex) {
        	    logger.debug(String.format("Unable to parse online resource for serviceName='%1$s' %2$s",serviceName, ex));
        	}
        }
        onlineResources = resources.toArray(new CSWOnlineResource[resources.size()]);
        
        //Parse our bounding box (if it exists). If it's unparsable, don't worry and just continue
        String bboxExpression = "gmd:identificationInfo/gmd:MD_DataIdentification/gmd:extent/gmd:EX_Extent/gmd:geographicElement/gmd:EX_GeographicBoundingBox";
        tempNode = (Node)xPath.evaluate(bboxExpression, node, XPathConstants.NODE);
        if (tempNode != null) {
            try {
                cswGeographicElement = CSWGeographicBoundingBox.fromGeographicBoundingBoxNode(tempNode, xPath);
            } catch (Exception ex) { }
        }
        
        //Parse descriptive keywords
        String descriptiveKeywordsExpression = "gmd:identificationInfo/gmd:MD_DataIdentification/gmd:descriptiveKeywords";
        tempNodeList1 = (NodeList)xPath.evaluate(descriptiveKeywordsExpression, node, XPathConstants.NODESET);
        List<String> keywords = new ArrayList<String>();
        for(int i = 0; i < tempNodeList1.getLength(); i++) {
        	try {
        		Node keywordList = tempNodeList1.item(i);
        		
        		tempNodeList2 = (NodeList)xPath.evaluate("gmd:MD_Keywords/gmd:keyword", keywordList, XPathConstants.NODESET);

        		for(int j=0; j<tempNodeList2.getLength(); j++) {
        			
        			Node keyword = tempNodeList2.item(j);
        			
                    tempNode = (Node) xPath.evaluate("gco:CharacterString", keyword, XPathConstants.NODE);

                    if(tempNode != null) {
                    	keywords.add(tempNode.getTextContent());
                    }
        		}        	
        	} catch(IllegalArgumentException ex) {
        	    logger.debug(String.format("Unable to parse descriptive keywords for serviceName='%1$s' %2$s",serviceName, ex));
        	}
        }
        descriptiveKeywords = keywords.toArray(new String[keywords.size()]);
    }

    public void setRecordInfoUrl(String recordInfoUrl) {
        this.recordInfoUrl = recordInfoUrl;
    }
    
    public String getRecordInfoUrl() {
        return recordInfoUrl;
    }

    public String getFileIdentifier() {
        return fileIdentifier;
    }

    public String getServiceName() {
        return serviceName;
    }
    
    public CSWOnlineResource[] getOnlineResources() {
        return onlineResources;
    }
    
    public String getContactOrganisation() {
        return contactOrganisation;
    }

    public String getDataIdentificationAbstract() {
        return dataIdentificationAbstract;
    }

    /**
     * Set the CSWGeographicElement that bounds this record
     * @param cswGeographicElement (can be null)
     */
    public void setCSWGeographicElement(CSWGeographicElement cswGeographicElement) {
        this.cswGeographicElement = cswGeographicElement;
    }

    /**
     * gets the  CSWGeographicElement that bounds this record (or null if it DNE)
     * @return
     */
    public CSWGeographicElement getCSWGeographicElement() {
        return cswGeographicElement;
    }
    
    /**
     * Returns the descriptive keywords for this record
     * @return descriptive keywords
     */
    public String[] getDescriptiveKeywords() {
    	return descriptiveKeywords;
    }
    
    @Override
    public String toString() {
        return "CSWRecord [contactOrganisation=" + contactOrganisation
                + ", cswGeographicElement=" + cswGeographicElement
                + ", dataIdentificationAbstract=" + dataIdentificationAbstract
                + ", fileIdentifier=" + fileIdentifier + ", onlineResources="
                + Arrays.toString(onlineResources) + ", recordInfoUrl="
                + recordInfoUrl + ", serviceName=" + serviceName + "]";
    }

    /**
     * Returns a filtered list of online resource protocols that match at least one of the specified types
     * 
     * @param types The list of types you want to filter by
     * @return
     */
    public CSWOnlineResource[] getOnlineResourcesByType(CSWOnlineResource.OnlineResourceType... types) {
        List <CSWOnlineResource> result = new ArrayList<CSWOnlineResource>();
        
        for (CSWOnlineResource r : onlineResources) {
            boolean matching = false;
            CSWOnlineResource.OnlineResourceType typeToMatch = r.getType();
            for (CSWOnlineResource.OnlineResourceType type : types) {
                if (typeToMatch == type) {
                    matching = true;
                    break;
                }
            }
            
            if (matching) {
                result.add(r);
            }
        }
        
        return result.toArray(new CSWOnlineResource[result.size()]);
    }
    
    /**
     * Returns true if this CSW Record contains at least 1 onlineResource with ANY of the specified types
     * @param types
     * @return
     */
    public boolean containsAnyOnlineResource(CSWOnlineResource.OnlineResourceType... types) {
        for (CSWOnlineResource r : onlineResources) {
            CSWOnlineResource.OnlineResourceType typeToMatch = r.getType();
            for (CSWOnlineResource.OnlineResourceType type : types) {
                if (typeToMatch == type) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Returns true if this record contains the given descriptive keyword, false otherwise.
     * 
     * @param str
     * @return true if this record contains the given descriptive keyword, false otherwise.
     */
    public boolean containsKeyword(String str) {
		for(String keyword : descriptiveKeywords) {
			if(keyword.equals(str)) {
				return true;
			}
		}
    	return false;
    }
}
