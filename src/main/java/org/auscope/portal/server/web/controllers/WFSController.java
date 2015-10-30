package org.auscope.portal.server.web.controllers;

import java.io.IOException;
import java.net.URLDecoder;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.httpclient.HttpStatus;
import org.auscope.portal.core.server.GeoServerType;
import org.auscope.portal.core.server.controllers.BasePortalController;
import org.auscope.portal.core.services.methodmakers.filter.FilterBoundingBox;
import org.auscope.portal.core.services.methodmakers.filter.SimpleBBoxFilter;
import org.auscope.portal.core.services.methodmakers.filter.SimplePropertyFilter;
import org.auscope.portal.core.services.responses.wfs.WFSCountResponse;
import org.auscope.portal.core.services.responses.wfs.WFSGetCapabilitiesResponse;
import org.auscope.portal.core.services.responses.wfs.WFSTransformedResponse;
import org.auscope.portal.server.web.service.WFSService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

/**
 * Acts as a proxy to WFS's
 *
 * User: Mathew Wyatt
 * 
 * @version $Id$
 */

@Controller
public class WFSController extends BasePortalController {

    private WFSService wfsService;

    @Autowired
    public WFSController(WFSService wfsService) {
        this.wfsService = wfsService;
    }

    /**
     * Given a service Url and a feature type this will query for all of the features, then convert them into KML, to be displayed, assuming that the response
     * will be complex feature GeoSciML
     *
     * @param serviceUrl
     * @param featureType
     * @param request
     * @return
     * @throws Exception
     */
    @RequestMapping("/getAllFeatures.do")
    public ModelAndView requestAllFeatures(@RequestParam("serviceUrl") final String serviceUrl,
            @RequestParam("typeName") final String featureType,
            @RequestParam(required = false, value = "bbox") final String bboxJSONString,
            @RequestParam(required = false, value = "maxFeatures", defaultValue = "0") int maxFeatures)
            throws Exception {

        GeoServerType geoServerType = GeoServerType.parseUrl(serviceUrl);
        FilterBoundingBox bbox = FilterBoundingBox.attemptParseFromJSON(bboxJSONString, geoServerType);

        SimpleBBoxFilter filter = new SimpleBBoxFilter();
        String filterString = null;
        String srs = null;
        if (bbox == null) {
            filterString = filter.getFilterStringAllRecords();
        } else {
            filterString = filter.getFilterStringBoundingBox(bbox);
        }

        WFSTransformedResponse response = null;
        try {
            response = wfsService.getWfsResponseAsKml(serviceUrl, featureType, filterString, maxFeatures, srs);
        } catch (Exception ex) {
            log.warn(String.format("Exception getting '%2$s' from '%1$s': %3$s", serviceUrl, featureType, ex));
            log.debug("Exception: ", ex);
            return generateExceptionResponse(ex, serviceUrl);
        }

        return generateJSONResponseMAV(true, response.getGml(), response.getTransformed(), response.getMethod());
    }

    /**
     * Given a service Url, a feature type and a specific feature ID, this function will fetch the specific feature and then convert it into KML to be
     * displayed, assuming that the response will be complex feature GeoSciML
     * 
     * @param serviceUrl
     * @param featureType
     * @param featureId
     * @param request
     * @return
     */
    @RequestMapping("/requestFeature.do")
    public ModelAndView requestFeature(@RequestParam("serviceUrl") final String serviceUrl,
            @RequestParam("typeName") final String featureType,
            @RequestParam("featureId") final String featureId) throws Exception {
        WFSTransformedResponse response = null;
        try {
            response = wfsService.getWfsResponseAsKml(serviceUrl, featureType, featureId);
        } catch (Exception ex) {
            log.warn(String.format("Exception getting '%2$s' with id '%4$s' from '%1$s': %3$s", serviceUrl,
                    featureType, ex, featureId));
            log.debug("Exception: ", ex);
            return generateExceptionResponse(ex, serviceUrl);
        }

        return generateJSONResponseMAV(true, response.getGml(), response.getTransformed(), response.getMethod());
    }

    /**
     * Given a service Url, a feature type and a property + value to filter by, this function will fetch the specific feature and then convert it into KML to be
     * displayed, assuming that the response will be complex feature GeoSciML
     *
     * This function exists to workaround some WFS instances whose featureId lookups do not work as required (such as Geoserver simple feature WFS sourced from
     * data with no primary keys)
     * 
     * @param serviceUrl
     * @param featureType
     * @param featureId
     * @param request
     * @return
     */
    @RequestMapping("/requestFeatureByProperty.do")
    public ModelAndView requestFeatureByProperty(@RequestParam("serviceUrl") final String serviceUrl,
            @RequestParam("typeName") final String featureType,
            @RequestParam("property") final String property,
            @RequestParam("value") final String value) throws Exception {
        SimplePropertyFilter filter = new SimplePropertyFilter(property, value);
        WFSTransformedResponse response = null;
        try {
            response = wfsService.getWfsResponseAsKml(serviceUrl, featureType, filter.getFilterStringAllRecords(),
                    null, null);
        } catch (Exception ex) {
            log.warn(String.format("Exception getting '%2$s' with property '%4$s' equal to '%5$s' '%1$s': %3$s",
                    serviceUrl, featureType, ex, property, value));
            log.debug("Exception: ", ex);
            return generateExceptionResponse(ex, serviceUrl);
        }

        return generateJSONResponseMAV(true, response.getGml(), response.getTransformed(), response.getMethod());
    }

    /**
     * Given a WFS service Url and a feature type this will query for the count of all of the features that optionally lie within a bounding box
     *
     * @param serviceUrl
     *            The WFS endpoint
     * @param featureType
     *            The feature type name to query
     * @param boundingBox
     *            [Optional] A JSON encoding of a FilterBoundingBox instance
     * @param maxFeatures
     *            [Optional] The maximum number of features to query
     */
    @RequestMapping("/getFeatureCount.do")
    public ModelAndView requestFeatureCount(@RequestParam("serviceUrl") final String serviceUrl,
            @RequestParam("typeName") final String featureType,
            @RequestParam(required = false, value = "bbox") final String bboxJSONString,
            @RequestParam(required = false, value = "maxFeatures", defaultValue = "0") int maxFeatures)
            throws Exception {

        GeoServerType geoServerType = GeoServerType.parseUrl(serviceUrl);
        FilterBoundingBox bbox = FilterBoundingBox.attemptParseFromJSON(bboxJSONString,geoServerType);
        SimpleBBoxFilter filter = new SimpleBBoxFilter();
        String filterString = null;
        if (bbox == null) {
            filterString = filter.getFilterStringAllRecords();
        } else {
            filterString = filter.getFilterStringBoundingBox(bbox);
        }

        WFSCountResponse response = null;
        try {
            response = wfsService.getWfsFeatureCount(serviceUrl, featureType, filterString, maxFeatures, null);
        } catch (Exception ex) {
            log.warn(String.format("Exception getting '%2$s' from '%1$s': %3$s", serviceUrl, featureType, ex));
            log.debug("Exception: ", ex);
            return generateExceptionResponse(ex, serviceUrl);
        }

        return generateJSONResponseMAV(true, new Integer(response.getNumberOfFeatures()), "");
    }

    /**
     * This method can be utilised by specifying a WFS url, typeName and featureId (in which a WFS request will be generated) OR just by specifying a URL which
     * will be resolved (such as in the case of a resolvable URN which maps to a WFS request at a remote server).
     * 
     * @param serviceUrl
     *            Can either be a WFS endpoint OR a URL that when resolved returns a WFS response
     * @param typeName
     *            [Optional] If specified a WFS request will be generated
     * @param featureId
     *            [Optional] If specified a WFS request will be generated
     * @throws IOException
     */
    @RequestMapping("wfsFeaturePopup.do")
    public void wfsFeaturePopup(HttpServletResponse response,
            @RequestParam("url") String serviceUrl,
            @RequestParam(required = false, value = "typeName") String typeName,
            @RequestParam(required = false, value = "featureId") String featureId) throws IOException {

        response.setContentType("text/html; charset=utf-8");
        ServletOutputStream outputStream = response.getOutputStream();

        //Make our request, transform and then return it.
        WFSTransformedResponse htmlResponse = null;
        try {
            if (typeName == null) {
                htmlResponse = wfsService.getWfsResponseAsHtml(serviceUrl);
            } else {
                htmlResponse = wfsService.getWfsResponseAsHtml(serviceUrl, typeName, featureId);
            }

            outputStream.write(htmlResponse.getTransformed().getBytes());
        } catch (Exception ex) {
            log.warn(String.format("Internal error requesting/writing popup for '%1$s' from '%2$s': %3$s", typeName,
                    serviceUrl, ex));
            log.debug("Exception: ", ex);
            response.sendError(HttpStatus.SC_INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * Gets the Abstract  from the getCapabilities record if it is defined there.    
     * The version is currently hardcoded in the WFSGetFeaturemethodMaker class.
     * 
     * @param serviceUrl The WMS URL to query
     * @param version the version of the WFS to request
     * @param name the name of the feature
     */
    @RequestMapping("/getWFSFeatureAbstract.do")
    public ModelAndView getWFSFeatureAbstract(
            @RequestParam("serviceUrl") String serviceUrl,
            @RequestParam("version") String version,
            @RequestParam("name") String name) throws Exception {

        try {
            String decodedServiceURL = URLDecoder.decode(serviceUrl, "UTF-8");
            
            WFSGetCapabilitiesResponse getCapabilitiesRecord = 
                    wfsService.getCapabilitiesResponse(decodedServiceURL);           
            
            String featureAbstract = getCapabilitiesRecord.getFeatureAbstracts().get(name);
            
            return generateJSONResponseMAV(true, featureAbstract, "");

        } catch (Exception e) {
            log.warn(String.format("Unable to download WFS Feature Abstract for '%1$s'", serviceUrl));
            log.debug(e);
            return generateJSONResponseMAV(false, "", null);
        }
    }   
    
    /**
     * Gets the MetadataURL  from the getCapabilities record if it is defined there.    
     * The version is currently hardcoded in the WFSGetFeaturemethodMaker class.
     * 
     * @param serviceUrl The WMS URL to query
     * @param version the version of the WFS to request
     * @param name the name of the feature
     */
    @RequestMapping("/getWFSFeatureMetadataURL.do")
    public ModelAndView getWFSFeatureMetadataURL(
            @RequestParam("serviceUrl") String serviceUrl,
            @RequestParam("version") String version,
            @RequestParam("name") String name) throws Exception {

        try {
            /* 
             * It might be preferable to create a nicer way of getting the data for the specific layer
             * This implementation just loops through the whole capabilities document looking for the layer.
             */
            String decodedServiceURL = URLDecoder.decode(serviceUrl, "UTF-8");
            
            WFSGetCapabilitiesResponse getCapabilitiesRecord = 
                    wfsService.getCapabilitiesResponse(decodedServiceURL);           
            
            String featureMetadataURL = getCapabilitiesRecord.getMetadataURLs().get(name);
            
            return generateJSONResponseMAV(true, featureMetadataURL, "");

        } catch (Exception e) {
            log.warn(String.format("Unable to download WFS Feature metadataURL for '%1$s'", serviceUrl));
            log.debug(e);
            return generateJSONResponseMAV(false, "", null);
        }
    }   
}
