/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.ballerina.core.nativeimpl.connectors.file.server;

import org.wso2.ballerina.core.exception.BallerinaException;
import org.wso2.ballerina.core.interpreter.Context;
import org.wso2.ballerina.core.model.Annotation;
import org.wso2.ballerina.core.model.Service;
import org.wso2.ballerina.core.model.SymbolName;
import org.wso2.ballerina.core.nativeimpl.connectors.BallerinaConnectorManager;
import org.wso2.ballerina.core.runtime.dispatching.ServiceDispatcher;
import org.wso2.carbon.messaging.CarbonCallback;
import org.wso2.carbon.messaging.CarbonMessage;
import org.wso2.carbon.messaging.ServerConnector;
import org.wso2.carbon.messaging.exceptions.ServerConnectorException;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Service dispatcher for File server connector.
 */
public class FileServiceDispatcher implements ServiceDispatcher {

    Map<String, Service> uriToServiceMap = new HashMap<>();
    Map<String, ServerConnector> uriToConnectorMap = new HashMap<>();

    @Override
    public Service findService(CarbonMessage cMsg, CarbonCallback callback, Context balContext) {
        String serviceName = (String) cMsg.getProperty(Constants.TRANSPORT_PROPERTY_SERVICE_NAME);
        return uriToServiceMap.get(serviceName);
    }

    @Override
    public String getProtocol() {
        return Constants.PROTOCOL_FILE;
    }

    @Override
    public void serviceRegistered(Service service) {

        for (Annotation annotation : service.getAnnotations()) {
            if (annotation.getName().equals(Constants.ANNOTATION_NAME_SOURCE)) {
                Map elementsMap = annotation.getElementPairs();

                Object protocolObj = elementsMap.get(new SymbolName(Constants.ANNOTATION_PROTOCOL));
                if (protocolObj == null) {
                    throw new BallerinaException("Mandatory annotation element '" + Constants.ANNOTATION_PROTOCOL
                            + "' is not found in Service '" + service.getSymbolName().getName() + "'");
                }
                if (!(protocolObj instanceof String)) {
                    throw new BallerinaException("Annotation element '" + Constants.ANNOTATION_PROTOCOL
                            + "' in Service " + service.getSymbolName().getName() + "' should be of string literal.");
                }
                String protocol = ((String) protocolObj);
                if (!(protocol.equals(Constants.PROTOCOL_FILE))) {
                    return;
                }

                String serviceName = service.getSymbolName().getName();

                ServerConnector fileServerConnector = BallerinaConnectorManager.getInstance()
                        .createServerConnector(Constants.PROTOCOL_FILE, serviceName);

                try {
                    fileServerConnector.start(getServerConnectorParamMap(elementsMap));
                    uriToServiceMap.put(serviceName, service);
                    uriToConnectorMap.put(serviceName, fileServerConnector);
                } catch (ServerConnectorException e) {
                    throw new BallerinaException("Could not start File Server Connector for service: "
                            + serviceName + ". Reason: " + e.getMessage());
                }
            }
        }
    }

    @Override
    public void serviceUnregistered(Service service) {
        String serviceName = service.getSymbolName().getName();
        if (uriToServiceMap.get(serviceName) != null) {
            uriToServiceMap.remove(serviceName);
            try {
                uriToConnectorMap.get(serviceName).stop();
            } catch (ServerConnectorException e) {
                throw new BallerinaException("Could not stop file server connector for " +
                        "service: " + serviceName + ". Reason: " + e.getMessage());
            }
            uriToConnectorMap.remove(serviceName);
        }
    }

    private static Map<String, String> getServerConnectorParamMap(Map map) {
        Map<String, String> convertedMap = new HashMap<>();
        Set<Map.Entry> entrySet = map.entrySet();
        for (Map.Entry entry : entrySet) {
            convertedMap.put(((SymbolName) entry.getKey()).getName(), (String) entry.getValue());
        }
        return convertedMap;
    }
}
