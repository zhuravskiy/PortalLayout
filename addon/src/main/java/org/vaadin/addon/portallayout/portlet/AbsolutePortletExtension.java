/*
 * Copyright 2013 Vaadin Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.vaadin.addon.portallayout.portlet;


import org.vaadin.addon.portallayout.gwt.shared.portlet.AbsolutePositionPortletState;
import org.vaadin.addon.portallayout.gwt.shared.portlet.rpc.AbsolutePortletServerRpc;

import com.vaadin.server.AbstractExtension;
import com.vaadin.server.ClientConnector;

/**
 * AbsolutePortlet.
 */
public class AbsolutePortletExtension extends AbstractExtension {

    public AbsolutePortletExtension(Portlet p) {
        extend(p);
        registerRpc(new AbsolutePortletServerRpc() {
            @Override
            public void updateCoordinates(int x, int y) {
                getState().x = x;
                getState().y = y;
            }
        });
    }
    
    @Override
    protected Class<Portlet> getSupportedParentType() {
        return Portlet.class;
    }
    
    @Override
    public Portlet getParent() {
        ClientConnector cc = super.getParent();
        return cc == null ? null : (Portlet)cc;
    }

    @Override
    protected AbsolutePositionPortletState getState() {
        return (AbsolutePositionPortletState)super.getState();
    }
}