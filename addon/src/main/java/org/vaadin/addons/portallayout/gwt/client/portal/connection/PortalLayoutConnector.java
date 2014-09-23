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
package org.vaadin.addons.portallayout.gwt.client.portal.connection;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.vaadin.addons.portallayout.gwt.client.dnd.StackPortalDropController;
import org.vaadin.addons.portallayout.gwt.client.portal.PortalLayoutUtil;
import org.vaadin.addons.portallayout.gwt.client.portal.PortalView;
import org.vaadin.addons.portallayout.gwt.client.portal.strategy.PortalHeightRedistributionStrategy;
import org.vaadin.addons.portallayout.gwt.client.portal.strategy.StackHeightRedistributionStrategy;
import org.vaadin.addons.portallayout.gwt.client.portlet.PortletChrome;
import org.vaadin.addons.portallayout.gwt.client.portlet.PortletConnector;
import org.vaadin.addons.portallayout.gwt.shared.portal.PortalLayoutState;
import org.vaadin.addons.portallayout.gwt.shared.portal.rpc.PortalServerRpc;

import com.allen_sauer.gwt.dnd.client.PickupDragController;
import com.allen_sauer.gwt.dnd.client.drop.DropController;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.user.client.ui.AbsolutePanel;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.RootPanel;
import com.vaadin.client.ComponentConnector;
import com.vaadin.client.ConnectorHierarchyChangeEvent;
import com.vaadin.client.ServerConnector;
import com.vaadin.client.Util;
import com.vaadin.client.communication.StateChangeEvent;
import com.vaadin.client.ui.AbstractLayoutConnector;
import com.vaadin.client.ui.layout.ElementResizeEvent;
import com.vaadin.client.ui.layout.ElementResizeListener;
import com.vaadin.shared.ComponentConstants;
import com.vaadin.shared.communication.URLReference;

/**
 * PortalWithExtensionConnector.
 */
public abstract class PortalLayoutConnector extends AbstractLayoutConnector implements PortalView.Presenter
{

  /**
   * PortalPickupDragController.
   */
  private static final class PortalPickupDragController extends PickupDragController
  {
    String moveablePanelStyleHack = null;

    private PortalPickupDragController(final AbsolutePanel boundaryPanel,
        final boolean allowDroppingOnBoundaryPanel)
    {
      super(boundaryPanel, allowDroppingOnBoundaryPanel);
    }

    protected void setMoveablePanelStyleName(final String styleName)
    {
      moveablePanelStyleHack = styleName;
    }

    @Override
    public void dragStart()
    {
      super.dragStart();
      context.selectedWidgets.get(0).getParent().addStyleName("v-app");
      context.selectedWidgets.get(0).getParent().addStyleName(moveablePanelStyleHack);
    }

    public boolean isMoveablePanelStyleSet()
    {
      return moveablePanelStyleHack != null;
    }
  }

  private PortalServerRpc                         rpc;

  private final static PortalPickupDragController commonDragController = new PortalPickupDragController(
                                                                           RootPanel.get(), false);

  private ComponentConnector                      incomingPortletCandidate;

  private ComponentConnector                      outcomingPortletCandidate;

  private PortalView                              view;

  private DropController                          dropController;

  private PortalHeightRedistributionStrategy      heightRedistributionStrategy;

  protected final List<ComponentConnector>        headerConnectors     = new ArrayList<ComponentConnector>();

  protected final ElementResizeListener           slotResizeListener   = new ElementResizeListener()
                                                                       {
                                                                         @Override
                                                                         public void onElementResize(
                                                                             final ElementResizeEvent event)
                                                                         {
                                                                           /**
                                                                            * We defer recalculation of
                                                                            * heights so that other listeners
                                                                            * first update the size values.
                                                                            */
                                                                           Scheduler
                                                                               .get()
                                                                               .scheduleDeferred(
                                                                                   new Scheduler.ScheduledCommand()
                                                                                   {
                                                                                     @Override
                                                                                     public void execute()
                                                                                     {
                                                                                       recalculateHeights();
                                                                                     }
                                                                                   });
                                                                         }
                                                                       };

  @Override
  protected void init()
  {
    super.init();
    rpc = initRpc();
    heightRedistributionStrategy = initHeightRedistributionStrategy();
    getLayoutManager().addElementResizeListener(getWidget().getElement(), new ElementResizeListener()
    {
      @Override
      public void onElementResize(final ElementResizeEvent e)
      {
      }
    });
  }

  protected abstract PortalServerRpc initRpc();

  protected PortalHeightRedistributionStrategy initHeightRedistributionStrategy()
  {
    return new StackHeightRedistributionStrategy();
  }

  protected PortalServerRpc getServerRpc()
  {
    return rpc;
  }

  @Override
  public void onStateChanged(final StateChangeEvent stateChangeEvent)
  {
    super.onStateChanged(stateChangeEvent);
    if (!commonDragController.isMoveablePanelStyleSet())
    {
      final String themeUri = getConnection().getThemeUri();
      commonDragController.setMoveablePanelStyleName(themeUri.substring(themeUri.lastIndexOf("/") + 1));
    }

    final boolean spacing = getState().spacing;
    getWidget().setStyleName("v-portal-layout-no-spacing", !spacing);
  }

  @Override
  public void updateCaption(final ComponentConnector connector)
  {
    if (getState().contentToPortlet.get(connector) != null)
    {
      final PortletConnector pc = (PortletConnector) getState().contentToPortlet.get(connector);
      pc.setCaption(connector.getState().caption);
      final URLReference iconRef = connector.getState().resources.get(ComponentConstants.ICON_RESOURCE);
      pc.setIcon(iconRef != null ? iconRef.getURL() : null);
    }
  }

  @Override
  public void onConnectorHierarchyChange(final ConnectorHierarchyChangeEvent event)
  {
    final List<ComponentConnector> children = getChildComponents();
    final List<ComponentConnector> oldChildren = event.getOldChildren();
    oldChildren.removeAll(children);
    headerConnectors.clear();
    for (final ComponentConnector cc : oldChildren)
    {
      final PortletConnector pc = PortalLayoutUtil.getPortletConnectorForContent(cc);
      if (pc != null)
      {
        view.removePortlet(pc.getPortletChrome());
      }
    }

    final Iterator<ComponentConnector> it = children.iterator();
    while (it.hasNext())
    {
      final ComponentConnector cc = it.next();
      if (getState().contentToPortlet.get(cc) != null)
      {
        final PortletConnector pc = (PortletConnector) getState().contentToPortlet.get(cc);
        final PortletChrome portletWidget = pc.getPortletChrome();
        cc.getLayoutManager().addElementResizeListener(portletWidget.getAssociatedSlot().getElement(),
            slotResizeListener);

        getView().addPortlet(portletWidget, getView().getWidgetCount());
      }
      else
      {
        headerConnectors.add(cc);
      }
    }
  }

  public void setIncomingPortletCandidate(final PortletChrome portletWidget)
  {
    assert portletWidget != null;
    final ComponentConnector pc = Util.findConnectorFor(portletWidget.getContentWidget());
    if (outcomingPortletCandidate == pc)
    {
      outcomingPortletCandidate = null;
    }
    else if (!getChildComponents().contains(pc))
    {
      incomingPortletCandidate = pc;
    }
  }

  public void setOutcomingPortletCandidate(final PortletChrome portletWidget)
  {
    assert portletWidget != null;
    final ComponentConnector pc = Util.findConnectorFor(portletWidget.getContentWidget());
    if (incomingPortletCandidate == pc)
    {
      incomingPortletCandidate = null;
    }
    else if (getChildComponents().contains(pc))
    {
      outcomingPortletCandidate = pc;
    }
  }

  @Override
  public Panel getWidget()
  {
    return (Panel) super.getWidget();
  }

  @Override
  public PortalView getView()
  {
    return view;
  }

  @Override
  public void setParent(final ServerConnector parent)
  {
    super.setParent(parent);
  }

  @Override
  protected Panel createWidget()
  {
    view = initView();
    dropController = initDropController();
    commonDragController.registerDropController(dropController);
    // The code block commented out, because it causes an NPE on Detach. It seems that it does not do anything
    // useful anywyas: note the double-commented line.
    // view.asWidget().addAttachHandler(new AttachEvent.Handler() {
    // @Override
    // public void onAttachOrDetach(AttachEvent event) {
    // getLayoutManager().addElementResizeListener(((ComponentConnector)getParent()).getWidget().getElement(),
    // new ElementResizeListener() {
    // @Override
    // public void onElementResize(ElementResizeEvent e) {
    // LayoutManager lm = e.getLayoutManager();
    // if (lm.getOuterHeight(e.getElement()) > lm.getOuterHeight(getWidget().getElement())) {
    // //getWidget().getElement().getStyle().setProperty("height", lm.getOuterHeight(e.getElement()) + "px");
    // }
    // }
    // });
    // }
    // });
    return view.asWidget();
  }

  protected DropController initDropController()
  {
    return new StackPortalDropController(this);
  }

  protected abstract PortalView initView();

  @Override
  public PortalLayoutState getState()
  {
    return (PortalLayoutState) super.getState();
  }

  @Override
  public void recalculateHeights()
  {
    getHeightRedistributionStrategy().redistributeHeights(this);
  }

  public void propagateHierarchyChangesToServer()
  {
    if (outcomingPortletCandidate != null)
    {
      rpc.removePortlet(outcomingPortletCandidate);
      outcomingPortletCandidate = null;
    }

    if (incomingPortletCandidate != null)
    {
      updatePortletPositionOnServer(incomingPortletCandidate);
      incomingPortletCandidate = null;
    }
  }

  public abstract void updatePortletPositionOnServer(ComponentConnector cc);

  public List<ComponentConnector> getCurrentChildren()
  {
    final List<ComponentConnector> result = new ArrayList<ComponentConnector>(getChildComponents())
    {
      @Override
      public boolean add(final ComponentConnector cc)
      {
        return (cc != null) && super.add(cc);
      }
    };
    result.removeAll(headerConnectors);
    result.remove(outcomingPortletCandidate);
    result.add(incomingPortletCandidate);
    return result;
  }

  public PortalHeightRedistributionStrategy getHeightRedistributionStrategy()
  {
    return heightRedistributionStrategy;
  }

  @Override
  public void onUnregister()
  {
    super.onUnregister();
    final PickupDragController dragController = getDragController();
    for (final ComponentConnector cc : getCurrentChildren())
    {
      PortalLayoutUtil.lockPortlet((PortletConnector) cc);
    }
    dragController.unregisterDropController(dropController);
  }

  public PickupDragController getDragController()
  {
    return commonDragController;
  }
}
