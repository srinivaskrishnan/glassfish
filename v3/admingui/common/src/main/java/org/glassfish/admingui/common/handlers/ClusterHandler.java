/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010 Oracle and/or its affiliates. All rights reserved.
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

/*
 * ClusterHandler.java
 *
 * Created on July 1,2010  9:32 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
/**
 *
 * @author anilam
 */
package org.glassfish.admingui.common.handlers;

import com.sun.jsftemplating.annotation.Handler;
import com.sun.jsftemplating.annotation.HandlerInput;
import com.sun.jsftemplating.annotation.HandlerOutput;

import com.sun.jsftemplating.layout.descriptors.handler.HandlerContext;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import java.util.Map;
import java.util.List;
import javax.faces.context.FacesContext;
import org.glassfish.admingui.common.util.GuiUtil;
import org.glassfish.admingui.common.util.RestUtil;

public class ClusterHandler {

    /** Creates a new instance of InstanceHandler */
    public ClusterHandler() {
    }
    
    /**
     * This method takes in a list of instances with status, which is the output of list-instances
     * and count the # of instance that is running and non running.
     * @param handlerCtx
     */
    @Handler(id = "gf.getClusterStatusSummary",
        input = {
            @HandlerInput(name = "statusMap", type = Map.class, required = true)
        },
        output = {
            @HandlerOutput(name = "numRunning", type = String.class),
            @HandlerOutput(name = "numNotRunning", type = String.class),
            @HandlerOutput(name = "status", type = String.class)
        })
    public static void getClusterStatusSummary(HandlerContext handlerCtx) {
        Map statusMap = (Map) handlerCtx.getInputValue("statusMap");
        int running=0;
        int notRunning=0;
        try{

            for (Iterator it=statusMap.values().iterator(); it.hasNext(); ) {
                Object value = it.next();
                if (value.toString().equals(RUNNING)){
                    running++;
                }else{
                    notRunning++;
                }
            }

            handlerCtx.setOutputValue( "numRunning" , GuiUtil.getMessage(CLUSTER_RESOURCE_NAME, "cluster.number.instance.running", new String[]{""+running}));
            handlerCtx.setOutputValue( "numNotRunning" , GuiUtil.getMessage(CLUSTER_RESOURCE_NAME, "cluster.number.instance.notRunning", new String[]{""+notRunning}));
        }catch(Exception ex){
            //Log exception ?
             handlerCtx.setOutputValue("status", GuiUtil.getMessage(CLUSTER_RESOURCE_NAME, "cluster.status.unknown"));
         }
     }


    @Handler(id = "gf.saveInstanceWeight",
        input = {
            @HandlerInput(name = "rows", type = List.class, required = true)})
    public static void saveInstanceWeight(HandlerContext handlerCtx) {
        List<Map> rows =  (List<Map>) handlerCtx.getInputValue("rows");
        List errorInstances = new ArrayList();
        Map response = null;

        String prefix = GuiUtil.getSessionValue("REST_URL") + "/servers/server/";
        for (Map oneRow : rows) {
            String instanceName = (String) oneRow.get("Name");
            String endpoint = GuiUtil.getSessionValue("REST_URL") + "/servers/server/instanceName" ;
            Map attrsMap = new HashMap();
            attrsMap.put("lbWeight", oneRow.get("LbWeight"));
            try{
                response = RestUtil.restRequest( prefix+instanceName , attrsMap, "post" , null, false);
            }catch (Exception ex){
                GuiUtil.getLogger().severe(
                        GuiUtil.getCommonMessage("LOG_SAVE_INSTANCE_WEIGHT_ERROR" ,  new Object[]{prefix+instanceName, attrsMap}));
                response = null;
            }
            if (response ==null){
                errorInstances.add(instanceName);
            }
        }
        if (errorInstances.size() > 0){
            String details = GuiUtil.getMessage(CLUSTER_RESOURCE_NAME, "instance.error.updateWeight" , new String[]{""+errorInstances});
            GuiUtil.handleError(handlerCtx, details);
        }
     }


    @Handler(id = "gf.clusterAction",
        input = {
            @HandlerInput(name = "rows", type = List.class, required = true),
            @HandlerInput(name = "action", type = String.class, required = true),
            @HandlerInput(name = "extraInfo", type = Object.class) })
    public static void clusterAction(HandlerContext handlerCtx) {
        String action = (String) handlerCtx.getInputValue("action");
        List<Map> rows =  (List<Map>) handlerCtx.getInputValue("rows");
        String  errorMsg = null;
        String prefix = GuiUtil.getSessionValue("REST_URL") + "/clusters/cluster/";

        for (Map oneRow : rows) {
            String clusterName = (String) oneRow.get("name");
            if (action.equals("delete-cluster")){
                //need to delete the clustered instance first
                Map clusterInstanceMap = (Map)handlerCtx.getInputValue("extraInfo");
                List<String> instanceNameList = (List) clusterInstanceMap.get(clusterName);
                for(String instanceName : instanceNameList){
                    errorMsg = deleteInstance(instanceName);
                    if (errorMsg != null){
                        GuiUtil.prepareAlert("error", GuiUtil.getMessage("msg.Error"), errorMsg);
                        return;
                    }
                }
            }
            try{
                RestUtil.restRequest( prefix + clusterName + "/" + action, null, "post" ,null, false);
            }catch (Exception ex){
                GuiUtil.prepareAlert("error", GuiUtil.getMessage("msg.Error"), ex.getMessage());
                return;
            }
        }
     }


    @Handler(id = "gf.instanceAction",
        input = {
            @HandlerInput(name = "rows", type = List.class, required = true),
            @HandlerInput(name = "action", type = String.class, required = true)})
    public static void instanceAction(HandlerContext handlerCtx) {
        String action = (String) handlerCtx.getInputValue("action");
        List<Map> rows =  (List<Map>) handlerCtx.getInputValue("rows");
        String prefix = GuiUtil.getSessionValue("REST_URL") + "/servers/server/";

        for (Map oneRow : rows) {
            String instanceName = (String) oneRow.get("name");
            if(action.equals("delete-instance")){
                String errorMsg = deleteInstance(instanceName);
                if (errorMsg != null){
                    GuiUtil.prepareAlert("error", GuiUtil.getMessage("msg.Error"), errorMsg);
                    return;
                }
            }else{
                try {
                   RestUtil.restRequest(prefix + instanceName + "/" + action , null, "post" ,null, false);
                } catch (Exception ex){
                    String endpoint=prefix + instanceName + "/" + action;
                    GuiUtil.getLogger().severe(
                        GuiUtil.getCommonMessage("LOG_ERROR_INSTANCE_ACTION", new Object[]{endpoint, "null"}));
                    GuiUtil.prepareAlert("error", GuiUtil.getMessage("msg.Error"), ex.getMessage());
                    return;
                }
            }
        }
        if(action.equals("stop-instance")){
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                //e.printStackTrace();
            }
        }
     }


    @Handler(id = "gf.nodeAction",
        input = {
            @HandlerInput(name = "rows", type = List.class, required = true),
            @HandlerInput(name = "action", type = String.class, required = true),
            @HandlerInput(name = "nodeInstanceMap", type = Map.class)})
    public static void nodeAction(HandlerContext handlerCtx) {
        String action = (String) handlerCtx.getInputValue("action");
        Map nodeInstanceMap = (Map) handlerCtx.getInputValue("nodeInstanceMap");
        if (nodeInstanceMap == null){
            nodeInstanceMap=new HashMap();
        }
        List<Map> rows =  (List<Map>) handlerCtx.getInputValue("rows");
        List errorInstances = new ArrayList();
        Map response = null;
        String prefix = GuiUtil.getSessionValue("REST_URL") + "/nodes/node/";

        for (Map oneRow : rows) {
            int code = 500;
            String nodeName = (String) oneRow.get("name");
            if (nodeName.equals("localhost")){
                GuiUtil.prepareAlert("error",  GuiUtil.getMessage("msg.Error"),
                        GuiUtil.getMessage(CLUSTER_RESOURCE_NAME, "node.error.removeLocalhost"));
                return;
            }
            List instancesList = (List)nodeInstanceMap.get(nodeName);
            if ( instancesList!= null && (instancesList.size()) != 0){
                GuiUtil.prepareAlert("error",  GuiUtil.getMessage("msg.Error"),
                        GuiUtil.getMessage(CLUSTER_RESOURCE_NAME, "nodes.instanceExistError", new String[]{ nodeName, nodeInstanceMap.get(nodeName).toString()}));
                return;
            }
            if(action.equals("delete-node")){
                try{
                       response = RestUtil.restRequest(prefix + nodeName + "/" + action + ".json" , null, "post" ,null, false);
                }catch (Exception ex){
                    GuiUtil.getLogger().severe(
                            GuiUtil.getCommonMessage("LOG_NODE_ACTION_ERROR", new Object[]{prefix + nodeName, action , "null"}));
                    GuiUtil.prepareAlert("error", GuiUtil.getMessage("msg.Error"), ex.getMessage());
                    return;
                }
            }
        }
     }



    @Handler(id = "gf.createClusterInstances",
        input = {
            @HandlerInput(name = "clusterName", type = String.class, required = true),
            @HandlerInput(name = "instanceRow", type = List.class, required = true)})
    public static void createClusterInstances(HandlerContext handlerCtx) {
        String clusterName = (String) handlerCtx.getInputValue("clusterName");
        List<Map> instanceRow =  (List<Map>) handlerCtx.getInputValue("instanceRow");
        Map attrsMap = new HashMap();
        Map response = null;
        String endpoint = GuiUtil.getSessionValue("REST_URL") + "/create-instance";
        for (Map oneInstance : instanceRow) {
            attrsMap.put("name", oneInstance.get("name"));
            attrsMap.put("cluster", clusterName);
            attrsMap.put("node", oneInstance.get("node"));
            //ignore for now till issue# 12646 is fixed
            //attrsMap.put("weight", oneInstance.get("weight"));
            try{
                response = RestUtil.restRequest( endpoint , attrsMap, "post" ,null, false);
            }catch (Exception ex){
                GuiUtil.getLogger().severe(
                    GuiUtil.getCommonMessage("LOG_CREATE_CLUSTER" ,  new Object[]{endpoint, attrsMap}));
            }
        }

    }


    /*
     * getDeploymentTargets takes in a list of cluster names, and an list of Properties that is returned from the
     * list-instances --standaloneonly=true.  Extract the instance name from this properties list.
     * The result list will include "server",  clusters and standalone instances,  suitable for deployment or create resources.
     *
     */
    @Handler(id = "gf.getDeploymentTargets",
        input = {
            @HandlerInput(name = "clusterList", type = List.class), // TODO: Should this be a map too?
            @HandlerInput(name = "listInstanceProps", type = List.class)
        },
        output = {
            @HandlerOutput(name = "result", type = List.class)
        })
    public static void getDeploymentTargets(HandlerContext handlerCtx) {
        List<String> result = new ArrayList();
        result.add("server");
        try{
            List<String> clusterList = (List) handlerCtx.getInputValue("clusterList");
            if (clusterList != null){
                for(String oneCluster : clusterList){
                    result.add(oneCluster);
                }
            }

            List<Map> instances = (List<Map>) handlerCtx.getInputValue("listInstanceProps");
            if (instances != null) {
                for (Map instance : instances) {
                    result.add((String)instance.get("name"));
                    //result.addAll(props.keySet());
                }
            }
         }catch(Exception ex){
             GuiUtil.getLogger().severe(ex.getLocalizedMessage());//"getDeploymentTargets failed.");
             //print stacktrace ??
         }
        handlerCtx.setOutputValue("result", result);
     }

    // If successfully deleted the instance, null will be returned, otherwise, return the error string to be displayed to user.
    private static String deleteInstance(String instanceName){
        try{
            RestUtil.restRequest( GuiUtil.getSessionValue("REST_URL") + "/servers/server/" + instanceName + "/delete-instance", null ,"post", null, false );
            return null;
        }catch(Exception ex){
            String endpoint = GuiUtil.getSessionValue("REST_URL") + "/servers/server/" + instanceName + "/delete-instance\n";
            GuiUtil.getLogger().severe(
                    GuiUtil.getCommonMessage("LOG_DELETE_INSTANCE", new Object[]{endpoint, "null"}));
            return ex.getMessage();
        }
    }


    @Handler(id = "gf.listInstances",
        input = {
            @HandlerInput(name="optionKeys", type=List.class, required=true),
            @HandlerInput(name="optionValues", type=List.class, required=true)
        },
        output = {
            @HandlerOutput(name = "instances", type = List.class),
            @HandlerOutput(name = "statusMap", type = Map.class),
            @HandlerOutput(name = "uptimeMap", type = Map.class),
            @HandlerOutput(name = "listEmpty", type = Boolean.class)
        })
    public static void listInstances(HandlerContext handlerCtx) {

        List instances = new ArrayList();
        Map statusMap = new HashMap();
        Map uptimeMap = new HashMap();
        List<String> keys = (List<String>) handlerCtx.getInputValue("optionKeys");
        List values = (List) handlerCtx.getInputValue("optionValues");
        Map<String, Object> attrs = new HashMap<String, Object>();
        if (keys != null && values != null) {
            for (int i = 0; i < keys.size(); i++) {
                attrs.put(keys.get(i), values.get(i));
            }
        }
        String endpoint = GuiUtil.getSessionValue("REST_URL")+"/list-instances";
        try{
            Map responseMap = RestUtil.restRequest( endpoint , attrs, "GET" , handlerCtx, false);
            Map extraPropertiesMap = (Map)((Map)responseMap.get("data")).get("extraProperties");
            if (extraPropertiesMap != null){
                List<Map> instanceList = (List)extraPropertiesMap.get("instanceList");
                for(Map oneInstance : instanceList){
                    instances.add(oneInstance.get("name"));
                    statusMap.put(oneInstance.get("name"), oneInstance.get("status"));
                    uptimeMap.put(oneInstance.get("name"), oneInstance.get("uptime"));
                }
            }
        }catch (Exception ex){
            GuiUtil.getLogger().severe(
                 GuiUtil.getCommonMessage("LOG_LIST_INSTANCES", new Object[]{endpoint, attrs}));
            //we don't need to call GuiUtil.handleError() because thats taken care of in restRequest() when we pass in the handler.
        }
        handlerCtx.setOutputValue("instances", instances);
        handlerCtx.setOutputValue("statusMap", statusMap);
        handlerCtx.setOutputValue("uptimeMap", uptimeMap);
        handlerCtx.setOutputValue("listEmpty", instances.isEmpty());
    }

    @Handler(id = "gf.getInstanceInfo",
        input = {
            @HandlerInput(name="instanceName", type=String.class, required=true)
        },
        output = {
            @HandlerOutput(name = "info", type = Map.class)
        })
    public static void getInstanceInfoHandler(HandlerContext handlerCtx) {
        String instanceName = (String)handlerCtx.getInputValue("instanceName");

        handlerCtx.setOutputValue("info", getInstanceInfo(instanceName));
    }

    public static Map<String, Object> getInstanceInfo(final String instanceName) {
        Map<String, Object> info = new HashMap<String, Object>();
        final String REST_URL = (String)FacesContext.getCurrentInstance().getExternalContext().getSessionMap().get("REST_URL");
        final String instanceUrl = REST_URL + "/servers/server/" + instanceName;
        Map<String, Object> result = RestUtil.restRequest(instanceUrl, null, "get", null, false);
        String instanceConfig = (String)((Map)getExtraPropertiesEntry(result, "entity")).get("configRef");

        // Server status
        String serverStatus = "RUNNING";
        if (!"server".equals(instanceName)) {
            result = RestUtil.restRequest(REST_URL+"/list-instances", new HashMap<String, Object>() {{ put ("id", instanceName); }}, "get", null, false);
            List instanceList = (List)getExtraPropertiesEntry(result, "instanceList");
            serverStatus = (String) ((Map)instanceList.get(0)).get("status");
        }

        // Config object
        String configUrl = REST_URL+ "/configs/config/" + instanceConfig;
        result = RestUtil.restRequest(configUrl, null, "get", null, false);
        Map<String, Object> config = (Map<String, Object>)((Map<String, Object>)result.get("data")).get("extraProperties");

        // Server version
        result = RestUtil.restRequest(configUrl + "/java-config/generate-jvm-report", null, "post", null, false);
        Map<String, String> jvmReport = buildExtraProperties((String)((Map<String, Object>)result.get("data")).get("message"));
        String version = (String)jvmReport.get("glassfish.version");
        String configRoot = (String)jvmReport.get("com.sun.aas.configRoot");

        // Debug
        result = RestUtil.restRequest(configUrl + "/java-config", null, "get", null, false);
        Map<String, String> entity = (Map)getExtraPropertiesEntry(result, "entity");

        // http ports
        result = RestUtil.restRequest(configUrl + "/network-config/network-listeners/network-listener", null, "get", null, false);
        Map<String, String> children = (Map<String, String>)getExtraPropertiesEntry(result, "childResources");
        if ((children != null) && (!children.isEmpty())) {
            List<String> httpPorts = new ArrayList<String>();
            for (String child : children.values()) {
                result = RestUtil.restRequest(child, null, "get", null, false);
                Map<String, String> iiopListener = (Map<String, String>)getExtraPropertiesEntry(result, "entity");
                httpPorts.add(iiopListener.get("port"));
            }
            info.put("httpPorts", httpPorts);
        }

        //iiop ports
        result = RestUtil.restRequest(configUrl + "/iiop-service/iiop-listener", null, "get", null, false);
        children = (Map<String, String>)getExtraPropertiesEntry(result, "childResources");
        if ((children != null) && (!children.isEmpty())) {
            List<String> iiopPorts = new ArrayList<String>();
            for (String child : children.values()) {
                result = RestUtil.restRequest(child, null, "get", null, false);
                Map<String, String> iiopListener = (Map<String, String>)getExtraPropertiesEntry(result, "entity");
                iiopPorts.add(iiopListener.get("port"));
            }
            info.put("iiopPorts", iiopPorts);
        }

        info.put("config", instanceConfig);
        info.put("status", serverStatus);
        info.put("version", version);
        info.put("configRoot", configRoot);
        info.put("debugEnabled", (String)entity.get("debugEnabled"));
        return info;
    }

    protected static Object getExtraPropertiesEntry(Map<String, Object> responseMap, String epKey) {
        Map<String, Object> data = (Map<String, Object>)responseMap.get("data");
        Map<String, Object> ep =  (Map<String, Object>)data.get("extraProperties");
        return ep.get(epKey);
    }

    private static Map<String, String> buildExtraProperties(String jvmReport) {
        final String SEP = System.getProperty("line.separator");
        Map<String, String> report = new HashMap<String, String>();
        String lines[] = jvmReport.split(SEP);
        for (String line : lines) {
            int valueSepIdx = line.indexOf("=");
            if (valueSepIdx == -1) {
                valueSepIdx = line.indexOf(":");
            }
            if (valueSepIdx > -1) {
                String key = line.substring(0, valueSepIdx).trim();
                String value = line.substring(valueSepIdx+1).trim();
                report.put(key, value);
            }
        }

        return report;
    }

    @Handler(id = "gf.getClusterNameForInstance",
        input = {
            @HandlerInput(name="instanceName", type=String.class, required=true)},
        output = {
            @HandlerOutput(name = "clusterName", type = String.class)})
    public static void getClusterNameForInstance(HandlerContext handlerCtx) {

        String instanceName = (String) handlerCtx.getInputValue("instanceName");
        try{
            List<String> clusterList = new ArrayList(RestUtil.getChildMap(GuiUtil.getSessionValue("REST_URL")+"/clusters/cluster").keySet());
            for(String oneCluster : clusterList){
                String encodedClusterName = URLEncoder.encode(oneCluster, "UTF-8");
                List<String> serverRefs = new ArrayList (RestUtil.getChildMap(GuiUtil.getSessionValue("REST_URL")+ "/clusters/cluster/" +
                        URLEncoder.encode(oneCluster, "UTF-8") + "/server-ref").keySet());
                if (serverRefs.contains(instanceName)){
                    handlerCtx.setOutputValue("clusterName", oneCluster);
                    return;
                }
            }
        }catch(Exception ex){
            GuiUtil.getLogger().info(GuiUtil.getCommonMessage("LOG_GET_CLUSTERNAME_FOR_INSTANCE"));
            ex.printStackTrace();
        }
    }

    public static final String CLUSTER_RESOURCE_NAME = "org.glassfish.cluster.admingui.Strings";

    //The following is defined in v3/cluster/admin/src/main/java/..../cluster/Constants.java
    public static final String RUNNING = "RUNNING";
    public static final String NOT_RUNNING = "NOT_RUNNING";
    public static final String PARTIALLY_RUNNING = "PARTIALLY_RUNNING";
}
