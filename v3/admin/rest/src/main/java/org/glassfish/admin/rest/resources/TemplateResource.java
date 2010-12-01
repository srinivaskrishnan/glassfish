/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2010 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package org.glassfish.admin.rest.resources;

import com.sun.enterprise.util.LocalStringManagerImpl;
import com.sun.enterprise.v3.common.ActionReporter;
import com.sun.jersey.api.core.ResourceContext;
import com.sun.jersey.multipart.FormDataBodyPart;
import com.sun.jersey.multipart.FormDataMultiPart;
import org.glassfish.admin.rest.ResourceUtil;
import org.glassfish.admin.rest.RestService;
import org.glassfish.admin.rest.Util;
import org.glassfish.admin.rest.provider.MethodMetaData;
import org.glassfish.admin.rest.results.ActionReportResult;
import org.glassfish.admin.rest.results.OptionsResult;
import org.glassfish.admin.rest.utils.xml.RestActionReporter;
import org.glassfish.api.ActionReport;
import org.glassfish.api.admin.ParameterMap;
import org.glassfish.api.admin.RestRedirect;
import org.jvnet.hk2.component.Habitat;
import org.jvnet.hk2.config.*;

import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.glassfish.admin.rest.Util.eleminateHypen;
import static org.glassfish.admin.rest.Util.upperCaseFirstLetter;

/**
 * @author Ludovic Champenois ludo@dev.java.net
 * @author Rajeshwar Patil
 */
@Produces({"text/html;qs=2", MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.APPLICATION_FORM_URLENCODED})
@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, MediaType.APPLICATION_FORM_URLENCODED})
public class TemplateResource {
    @Context
    protected HttpHeaders requestHeaders;

    @Context
    protected UriInfo uriInfo;

    @Context
    protected ResourceContext resourceContext;

    @Context
    protected Habitat habitat;
    protected Dom entity;  //may be null when not created yet...
    protected Dom parent;
    protected String tagName;
    protected boolean entityNeedsToBeCreated = false;
    protected ConfigModel childModel; //good model even if the child entity is null
    public final static LocalStringManagerImpl localStrings = new LocalStringManagerImpl(TemplateResource.class);
    final private static List<String> attributesToSkip = new ArrayList<String>() {
        {
            add("parent");
            add("name");
            add("children");
            add("submit");
        }
    };

    /**
     * Creates a new instance of xxxResource
     */
    public TemplateResource() {
    }

    @GET
    public ActionReportResult getEntity(@QueryParam("expandLevel") @DefaultValue("1") int expandLevel) {
        if (childModel == null) {//wrong entity name at this point
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }

        return buildActionReportResult(true);
    }

    // TODO: This is wrong. Updates are done via PUT
    @POST
    //update
    public Response createEntity(HashMap<String, String> data) {
        try {
            //data.remove("submit");
            removeAttributesToBeSkipped(data);
            if (data.containsKey("error")) {
                String errorMessage = localStrings.getLocalString("rest.request.parsing.error",
                        "Unable to parse the input entity. Please check the syntax.");
                return Response.status(400).entity(ResourceUtil.getActionReportResult(400, errorMessage, requestHeaders, uriInfo)).build();
            }

            ResourceUtil.purgeEmptyEntries(data);

            //hack-1 : support delete method for html
            //Currently, browsers do not support delete method. For html media,
            //delete operations can be supported through POST. Redirect html
            //client POST request for delete operation to DELETE method.
            if ("__deleteoperation".equals(data.get("operation"))) {
                data.remove("operation");
                return delete(data);
            }
            if (entityNeedsToBeCreated) {//create if in the tree
                Class<? extends ConfigBeanProxy> proxy = TemplateListOfResource.getElementTypeByName(parent, tagName);
                ConfigBean theParent = (ConfigBean) parent;
                //need to change from camel case to xml !!!createAndSet works on xml names
                HashMap<String, String> xmldata = new HashMap<String, String>();
                Set<String> keys = data.keySet();
                Iterator<String> iterator = keys.iterator();
                String key;
                while (iterator.hasNext()) {
                    key = iterator.next();
                    xmldata.put(theParent.model.camelCaseToXML(key), data.get(key));
                }
                ConfigSupport.createAndSet(theParent, proxy, xmldata);
                entity = parent.nodeElement(tagName);
                entityNeedsToBeCreated = false;
            } else { //just update it.
                data = ResourceUtil.translateCamelCasedNamesToXMLNames(data);
                ActionReporter ar = applyChanges(data);
                if(ar.getActionExitCode() != ActionReport.ExitCode.SUCCESS) {
                    //TODO better error handling.
                    return Response.status(400).entity(ResourceUtil.getActionReportResult(400, "Could not apply changes" + ar.getMessage(), requestHeaders, uriInfo)).build();
                }

            }
            String successMessage = localStrings.getLocalString("rest.resource.update.message",
                    "\"{0}\" updated successfully.", new Object[]{uriInfo.getAbsolutePath()});
            return Response.ok(ResourceUtil.getActionReportResult(200, successMessage, requestHeaders, uriInfo)).build();
        } catch (Exception ex) {
            if (ex.getCause() instanceof ValidationException) {
                return Response.status(400).entity(ResourceUtil.getActionReportResult(400, ex.getMessage(), requestHeaders, uriInfo)).build();
            } else {
                throw new WebApplicationException(ex, Response.Status.INTERNAL_SERVER_ERROR);
            }
        }
    }

    private ActionReporter applyChanges(HashMap<String, String> data) {
        List<PathSegment> pathSegments = uriInfo.getPathSegments();
        List<PathSegment> pathSegmentsDiscardingFirstSegment = pathSegments.subList(1, pathSegments.size());

        StringBuilder setBasePath = new StringBuilder();
        for(PathSegment pathSegment :  pathSegmentsDiscardingFirstSegment) {
            setBasePath.append(pathSegment.getPath());
            setBasePath.append('.');
        }
        ParameterMap parameters = new ParameterMap();
        for (Map.Entry<String, String> entry : data.entrySet()) {
            StringBuilder setExpression = new StringBuilder(setBasePath);
            parameters.add("DEFAULT", setExpression.append(entry.getKey()).append('=').append(entry.getValue()).toString());
        }
        return ResourceUtil.runCommand("set", parameters, habitat, ""); //TODO The last parameter is resultType and is not used. Refactor the called method to remove it
    }


    @DELETE
    public Response delete(HashMap<String, String> data) {
        if (entity == null) {//wrong resource
            String errorMessage = localStrings.getLocalString("rest.resource.erromessage.noentity",
                    "Resource not found.");
            return Response.status(404).entity(ResourceUtil.getActionReportResult(404, errorMessage, requestHeaders, uriInfo)).build();
        }

        if (getDeleteCommand() == null) {
            String message = localStrings.getLocalString("rest.resource.delete.forbidden",
                    "DELETE on \"{0}\" is forbidden.", new Object[]{uriInfo.getAbsolutePath()});
            return Response.status(403).entity(ResourceUtil.getActionReportResult(403, message, requestHeaders, uriInfo)).build(); //403 - forbidden
        }
        if (getDeleteCommand().equals("GENERIC-DELETE")) {
            try {
                ConfigBean p = (ConfigBean) parent;
                if (parent == null) {
                    p = (ConfigBean) entity.parent();
                }
                ConfigSupport.deleteChild(p, (ConfigBean) entity);
                String successMessage = localStrings.getLocalString("rest.resource.delete.message",
                        "\"{0}\" deleted successfully.", new Object[]{uriInfo.getAbsolutePath()});
                return Response.ok(ResourceUtil.getActionReportResult(200, successMessage, requestHeaders, uriInfo)).build(); //200 - ok
//                return ResourceUtil.getDeleteResponse(200, successMessage, requestHeaders, uriInfo); //200 - ok
            } catch (TransactionFailure ex) {
                throw new WebApplicationException(ex,
                        Response.Status.INTERNAL_SERVER_ERROR);
            }
        }
        //do the delete via the command:
        try {
            if (data.containsKey("error")) {
                String errorMessage = localStrings.getLocalString("rest.request.parsing.error",
                        "Unable to parse the input entity. Please check the syntax.");
                return Response.status(400).entity(ResourceUtil.getActionReportResult(400, errorMessage, requestHeaders, uriInfo)).build();
            }

            ResourceUtil.addQueryString(uriInfo.getQueryParameters(), data);
            ResourceUtil.purgeEmptyEntries(data);
            ResourceUtil.adjustParameters(data);

            if (data.get("DEFAULT") == null) {
                addDefaultParameter(data);
            } else {
                String resourceName = getResourceName(uriInfo.getAbsolutePath().getPath(), "/");
                if (!data.get("DEFAULT").equals(resourceName)) {
                    String errorMessage = localStrings.getLocalString("rest.resource.not.deleted",
                            "Resource not deleted. Value of \"name\" should be the name of this resource.");
                    return Response.status(403).entity(ResourceUtil.getActionReportResult(403, errorMessage, requestHeaders, uriInfo)).build();
                }
            }

            ActionReport actionReport = runCommand(getDeleteCommand(), data);

            if (actionReport != null) {
                ActionReport.ExitCode exitCode = actionReport.getActionExitCode();
                if (exitCode != ActionReport.ExitCode.FAILURE) {
                    String successMessage = localStrings.getLocalString("rest.resource.delete.message",
                            "\"{0}\" deleted successfully.", new Object[]{uriInfo.getAbsolutePath()});
                    return Response.ok(ResourceUtil.getActionReportResult(200, successMessage, requestHeaders, uriInfo)).build(); //200 - ok
                }

                String errorMessage = actionReport.getMessage();

                return Response.status(400).entity(ResourceUtil.getActionReportResult(400, errorMessage, requestHeaders, uriInfo)).build(); //400 - bad request
            }

            String message = localStrings.getLocalString("rest.resource.delete.forbidden",
                    "DELETE on \"{0}\" is forbidden.", new Object[]{uriInfo.getAbsolutePath()});
            return Response.status(400).entity(ResourceUtil.getActionReportResult(403, message, requestHeaders, uriInfo)).build(); //403 - forbidden
        } catch (Exception e) {
            throw new WebApplicationException(e, Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @OPTIONS
    public Response options() {
        return Response.ok(buildActionReportResult(false)).build();
    }

    /**
     * allows for remote files to be put in a tmp area and we pass the
     * local location of this file to the corresponding command instead of the content of the file
     * * Yu need to add  enctype="multipart/form-data" in the form
     * for ex:  <form action="http://localhost:4848/management/domain/applications/application" method="post" enctype="multipart/form-data">
     * then any param of type="file" will be uploaded, stored locally and the param will use the local location
     * on the server side (ie. just the path)
     */
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response post(FormDataMultiPart formData) {
        HashMap<String, String> data = createDataBasedOnForm(formData);
        return createEntity(data); //execute the deploy command with a copy of the file locally
    }

    public void setEntity(Dom p) {
        entity = p;
        childModel = p.model;
    }

    public Dom getEntity() {
        return entity;
    }

    public void setParentAndTagName(Dom parent, String tagName) {
        
        if (parent==null){ //prevent https://glassfish.dev.java.net/issues/show_bug.cgi?id=14125
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        this.parent = parent;
        this.tagName = tagName;
        entity = parent.nodeElement(tagName);
        if (entity == null) {
            try {
                Class<? extends ConfigBeanProxy> proxy = TemplateListOfResource.getElementTypeByName(parent, tagName);
                ConfigBean theParent = (ConfigBean) parent;
                // we quickly crate one just to get it smodel.
                // but the real one might be created later, if the user wants it!
                entityNeedsToBeCreated = true;
                entity = theParent.allocate(proxy);
                childModel = entity.model;
                entity = null;

            } catch (Exception e) {
                throw new WebApplicationException(e, Response.Status.INTERNAL_SERVER_ERROR);
            }

        } else {
            childModel = entity.model;

        }
    }

    /**
     * allows for remote files to be put in a tmp area and we pass the
     * local location of this file to the corresponding command instead of the content of the file
     * * Yu need to add  enctype="multipart/form-data" in the form
     * for ex:  <form action="http://localhost:4848/management/domain/applications/application" method="post" enctype="multipart/form-data">
     * then any param of type="file" will be uploaded, stored locally and the param will use the local location
     * on the server side (ie. just the path)
     */
    public static HashMap<String, String> createDataBasedOnForm(FormDataMultiPart formData) {
        HashMap<String, String> data = new HashMap<String, String>();
        try {
            /* data passed to the generic command running
             *
             * */

            Map<String, List<FormDataBodyPart>> m1 = formData.getFields();

            Set<String> ss = m1.keySet();
            for (String fieldName : ss) {
                FormDataBodyPart n = formData.getField(fieldName);
                Logger.getLogger(TemplateResource.class.getName()).log(Level.INFO, "fieldName=" + fieldName);


                if (n.getContentDisposition().getFileName() != null) {//we have a file
                    //save it and mark it as delete on exit.
                    InputStream fileStream = n.getValueAs(InputStream.class);
                    String mimeType = n.getMediaType().toString();

                    //Use just the filename without complete path. File creation
                    //in case of remote deployment failing because fo this.
                    String fileName = n.getContentDisposition().getFileName();
                    if (fileName.contains("/")) {
                        fileName = Util.getName(fileName, '/');
                    } else {
                        if (fileName.contains("\\")) {
                            fileName = Util.getName(fileName, '\\');
                        }
                    }

                    File f = saveFile(fileName, mimeType, fileStream);
                    f.deleteOnExit();
                    //put only the local path of the file in the same field.
                    data.put(fieldName, f.getAbsolutePath());

                } else {
                    try {
                        Logger.getLogger(TemplateResource.class.getName()).log(Level.INFO, "Values=" + fieldName + " === " + n.getValue());
                        data.put(fieldName, n.getValue());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception ex) {
            Logger.getLogger(TemplateResource.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            formData.cleanup();
        }
        return data;

    }

    public void setBeanByKey(List<Dom> parentList, String id) {
        if (parentList != null) { // Believe it or not, this can happen
            for (Dom c : parentList) {
                String keyAttributeName = null;
                ConfigModel model = c.model;
                if (model.key == null) {
                    try {
                        for (String s : model.getAttributeNames()) {//no key, by default use the name attr
                            if (s.equals("name")) {
                                keyAttributeName = s;
                            }
                        }
                        if (keyAttributeName == null) {//nothing, so pick the first one
                            keyAttributeName = model.getAttributeNames().iterator().next();
                        }
                    } catch (Exception e) {
                        keyAttributeName = "ThisIsAModelBug:NoKeyAttr"; //no attr choice fo a key!!! Error!!!
                    } //firstone
                } else {
                    keyAttributeName = model.key.substring(1, model.key.length());
                }

                String keyvalue = c.attribute(keyAttributeName.toLowerCase());
                if (keyvalue.equals(id)) {
                    setEntity((ConfigBean) c);
                }
            }
        }
    }

    protected ActionReportResult buildActionReportResult(boolean showEntityValues) {
        RestActionReporter ar = new RestActionReporter();
        ar.setExtraProperties(new Properties());
        ConfigBean entity = (ConfigBean) getEntity();
        if (childModel != null) {
            ar.setActionDescription(upperCaseFirstLetter(childModel.getTagName()));
        }
        if (showEntityValues) {
            if (entity != null) {
                ar.getExtraProperties().put("entity", getAttributes(entity));
            }
        }
        OptionsResult optionsResult = new OptionsResult(Util.getResourceName(uriInfo));
        Map<String, MethodMetaData> mmd = getMethodMetaData();
        optionsResult.putMethodMetaData("GET", mmd.get("GET"));
        optionsResult.putMethodMetaData("POST", mmd.get("POST"));
        optionsResult.putMethodMetaData("DELETE", mmd.get("DELETE"));

        ResourceUtil.addMethodMetaData(ar, mmd);
        if (entity != null) {
            ar.getExtraProperties().put("childResources", ResourceUtil.getResourceLinks(entity, uriInfo, 
                    ResourceUtil.canShowDeprecatedItems(habitat)));
        }
        ar.getExtraProperties().put("commands", ResourceUtil.getCommandLinks(getCommandResourcesPaths()));

        return new ActionReportResult(ar, entity, optionsResult);
    }

    protected void removeAttributesToBeSkipped(Map<String, String> data) {
        for (String item : attributesToSkip) {
            data.remove(item);
        }
    }

    protected String[][] getCommandResourcesPaths() {
        return new String[][]{};
    }

    protected String getDeleteCommand() {
        if (entity == null) {
            return null;
        }
        return ResourceUtil.getCommand(RestRedirect.OpType.DELETE, getEntity().model);
    }

    private static File saveFile(String fileName, String mimeType, InputStream fileStream) {
        BufferedOutputStream out = null;
        File f = null;
        try {
            if (fileName.contains(".")) {
                String prefix = fileName.substring(0, fileName.indexOf("."));
                String suffix = fileName.substring(fileName.indexOf("."), fileName.length());
                if (prefix.length() < 3) {
                    prefix = "glassfish" + prefix;
                }
                f = File.createTempFile(prefix, suffix);
            }


            out = new BufferedOutputStream(new FileOutputStream(f));
            byte[] buffer = new byte[32 * 1024];
            int bytesRead = 0;
            while ((bytesRead = fileStream.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            return f;
        } catch (IOException ex) {
            Logger.getLogger(TemplateResource.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException ex) {
                Logger.getLogger(TemplateResource.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return null;
    }

    /*
     * see if we can understand the ConfigBeans annotations like:
     * @RestRedirects(
    {
    @RestRedirect(opType= RestRedirect.OpType.DELETE, commandName="undeploy"),
    @RestRedirect(opType= RestRedirect.OpType.POST, commandName = "redeploy")
    }
     **/

    /**
     * Returns the list of command resource paths [command, http method, url/path]
     *
     * @return
     */
    private ActionReport runCommand(String commandName, HashMap<String, String> data) {

        if (commandName != null) {
            String typeOfResult = ResourceUtil.getResultType(requestHeaders);

            return ResourceUtil.runCommand(commandName, data, habitat, typeOfResult);//processed
        }

        return null;//not processed
    }

    // This has to be smarter, since we are encoding / in resource names now

    private void addDefaultParameter(HashMap<String, String> data) {
        int index = uriInfo.getAbsolutePath().getPath().lastIndexOf('/');
        String defaultParameterValue =
                //uriInfo.getAbsolutePath().getPath().substring(index + 1);
                getEntity().getKey();
        data.put("DEFAULT", defaultParameterValue);
    }

    private String getResourceName(String absoluteName, String delimiter) {
        if (null == absoluteName) {
            return absoluteName;
        }
        int index = absoluteName.lastIndexOf(delimiter);
        if (index != -1) {
            index = index + delimiter.length();
            return absoluteName.substring(index);
        } else {
            return absoluteName;
        }
    }

    //******************************************************************************************************************

    private Map<String, String> getAttributes(Dom entity) {
        Map<String, String> result = new TreeMap<String, String>();
        Set<String> attributeNames = entity.model.getAttributeNames();
        for (String attributeName : attributeNames) {
            result.put(eleminateHypen(attributeName), entity.attribute(attributeName));
        }

        return result;
    }

    private Map<String, MethodMetaData> getMethodMetaData() {
        Map<String, MethodMetaData> map = new TreeMap<String, MethodMetaData>();
        //GET meta data
        map.put("GET", new MethodMetaData());

        /////optionsResult.putMethodMetaData("POST", new MethodMetaData());
        MethodMetaData postMethodMetaData = ResourceUtil.getMethodMetaData(childModel);
        map.put("POST", postMethodMetaData);


        //DELETE meta data
        String command = getDeleteCommand();
        if (command != null) {
            MethodMetaData deleteMethodMetaData;
            if (command.equals("GENERIC-DELETE")) {
                deleteMethodMetaData = new MethodMetaData();
            } else {
                deleteMethodMetaData = ResourceUtil.getMethodMetaData(
                        command, habitat, RestService.logger);

                //In case of delete operation(command), do not  display/provide id attribute.
                deleteMethodMetaData.removeParamMetaData("id");
            }

            map.put("DELETE", deleteMethodMetaData);
        }
        return map;
    }
}
