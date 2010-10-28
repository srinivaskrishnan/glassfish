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

/*
 * InstanceHandler.java
 *
 * Created on August 10, 2006, 2:32 PM
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

import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;

import org.glassfish.admingui.common.util.GuiUtil;
import org.glassfish.admingui.common.util.RestUtil;


public class LoggingHandlers {

    /** Creates a new instance of InstanceHandler */
    public LoggingHandlers() {
    }


    @Handler(id = "getLoggerLevels",
    input = {
        @HandlerInput(name = "loggerLevels", type = Map.class, required = true)},
    output = {
        @HandlerOutput(name = "loggerList", type = List.class)
    })
    public static void getLoggerLevels(HandlerContext handlerCtx) {

        Map<String, String> loggerLevels = (Map) handlerCtx.getInputValue("loggerLevels");
        List result = new ArrayList();
        if (loggerLevels != null)    {
            for(String oneLogger:  loggerLevels.keySet()){
                    Map oneRow = new HashMap();
                    oneRow.put("loggerName", oneLogger);
                    oneRow.put("level", loggerLevels.get(oneLogger));
                    oneRow.put("selected", false);
                    result.add(oneRow);
            }
        }
        handlerCtx.setOutputValue("loggerList",  result);
     }


    @Handler(id = "changeLoggerLevels",
    input = {
        @HandlerInput(name = "newLogLevel", type = String.class, required = true),
        @HandlerInput(name = "allRows", type = List.class, required = true)},
    output = {
        @HandlerOutput(name = "newList", type = List.class)})
    public static void changeLoggerLevels(HandlerContext handlerCtx) {
        String newLogLevel = (String) handlerCtx.getInputValue("newLogLevel");
        List obj = (List) handlerCtx.getInputValue("allRows");
        List<Map> allRows = (List) obj;
        if (GuiUtil.isEmpty(newLogLevel)){
            handlerCtx.setOutputValue("newList",  allRows);
            return;
        }
        for(Map oneRow : allRows){
            boolean selected = (Boolean) oneRow.get("selected");
            if (selected){
                oneRow.put("level", newLogLevel);
                oneRow.put("selected", false);
            }
        }
        handlerCtx.setOutputValue("newList",  allRows);
     }



    @Handler(id = "updateLoggerLevels",
    input = {
        @HandlerInput(name = "allRows", type = List.class, required = true),
        @HandlerInput(name = "config", type = String.class, required = true)})
    public static void updateLoggerLevels(HandlerContext handlerCtx) {
        List<Map<String,Object>> allRows = (List<Map<String,Object>>) handlerCtx.getInputValue("allRows");
        String config = (String)handlerCtx.getInputValue("config");
        Map<String, Object> props = new HashMap();
        for(Map<String, Object> oneRow : allRows){
            props.put("id", oneRow.get("loggerName") + "=" + oneRow.get("level"));
            props.put("target", config);
            RestUtil.restRequest((String)GuiUtil.getSessionValue("REST_URL") + "/set-log-levels.json",
                    props, "POST", null, true);
        }

     }

    @Handler(id = "saveLoggingAttributes",
    input = {
        @HandlerInput(name = "attrs", type = Map.class, required=true),
        @HandlerInput(name = "attrsInUI", type = String.class, required=true), // added because of Logging backend bug
        @HandlerInput(name = "config", type = String.class, required=true)
    })

    public static void saveLoggingAttributes(HandlerContext handlerCtx) {
        Map<String,Object> attrs = (Map<String,Object>) handlerCtx.getInputValue("attrs");
        String attrsInUI = (String)handlerCtx.getInputValue("attrsInUI");
        String[] attrNames = attrsInUI.split(",");
        String config = (String)handlerCtx.getInputValue("config");
        Map<String, Object> props = new HashMap();
        for(String key : attrNames){
            props.put("id", key + "=" + attrs.get(key));
            props.put("target", config);
            RestUtil.restRequest((String)GuiUtil.getSessionValue("REST_URL") + "/set-log-attributes.json",
                    props, "POST", null, true);
        }
     }

    @Handler(id = "getValidLogLevels",
    output = {
        @HandlerOutput(name = "loggerList", type = List.class)
    })
    public static void getValidLogLevels(HandlerContext handlerCtx) {
        handlerCtx.setOutputValue("loggerList",  levels);
     }
    
    final private static List<String> levels= new ArrayList();
    static{
        levels.add("OFF");
        levels.add("SEVERE");
        levels.add("WARNING");
        levels.add("INFO");
        levels.add("CONFIG");
        levels.add("FINE");
        levels.add("FINER");
        levels.add("FINEST");
    }
}
        
 
