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
package org.vaadin.addon.portallayout.gwt.client.dnd;

import org.vaadin.addon.portallayout.gwt.client.portal.connection.AbsolutePortalConnector;
import org.vaadin.addon.portallayout.gwt.client.portal.connection.PortalLayoutConnector;
import org.vaadin.addon.portallayout.gwt.client.portlet.PortletChrome;
import org.vaadin.addon.portallayout.gwt.client.portlet.PortletSlot;

import com.allen_sauer.gwt.dnd.client.DragContext;
import com.allen_sauer.gwt.dnd.client.VetoDragException;
import com.allen_sauer.gwt.dnd.client.drop.AbstractPositioningDropController;
import com.allen_sauer.gwt.dnd.client.util.DOMUtil;
import com.allen_sauer.gwt.dnd.client.util.DragClientBundle;
import com.allen_sauer.gwt.dnd.client.util.WidgetLocation;
import com.google.gwt.user.client.ui.AbsolutePanel;
import com.google.gwt.user.client.ui.InsertPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.vaadin.client.ComponentConnector;
import com.vaadin.client.Util;

/**
 * AbsolutePortalDropController.
 */
public class AbsolutePortalDropController extends AbstractPositioningDropController {

    private final PortalLayoutConnector portalConnector;

    private final InsertPanel panel;

    static class Draggable {

        public int desiredX;

        public int desiredY;

        public int relativeX;

        public int relativeY;

        final int offsetHeight;

        final int offsetWidth;

        Widget positioner = null;

        final Widget widget;

        public Draggable(Widget widget) {
            this.widget = widget;
            offsetWidth = widget.getOffsetWidth();
            offsetHeight = widget.getOffsetHeight();
        }
    }

    private static final Label DUMMY_LABEL_IE_QUIRKS_MODE_OFFSET_HEIGHT = new Label("x");

    private Draggable draggable;

    private final AbsolutePanel dropTarget;

    int dropTargetClientHeight;

    int dropTargetClientWidth;

    int dropTargetOffsetX;

    int dropTargetOffsetY;

    public AbsolutePortalDropController(AbsolutePortalConnector portal) {
        super((AbsolutePanel) portal.getView());
        this.portalConnector = portal;
        this.panel = portal.getView();
        this.dropTarget = (AbsolutePanel) portal.getView();
    }

    @Override
    public void onPreviewDrop(DragContext context) throws VetoDragException {
        super.onPreviewDrop(context);
    }

    /**
     * Programmatically drop a widget on our drop target while obeying the
     * constraints of this controller.
     * 
     * @param widget
     *            the widget to be dropped
     * @param left
     *            the desired absolute horizontal location relative to our drop
     *            target
     * @param top
     *            the desired absolute vertical location relative to our drop
     *            target
     */
    public void drop(Widget widget, int left, int top) {
        left = Math.max(0, Math.min(left, dropTarget.getOffsetWidth() - widget.getOffsetWidth()));
        top = Math.max(0, Math.min(top, dropTarget.getOffsetHeight() - widget.getOffsetHeight()));
        dropTarget.add(widget, left, top);
    }

    @Override
    public void onDrop(DragContext context) {

        //draggable.positioner.removeFromParent();
        //dropTarget.add(draggable.widget, draggable.desiredX, draggable.desiredY);
        super.onDrop(context);

        PortletChrome portletWidget = (PortletChrome) context.selectedWidgets.get(0);
        portletWidget.getAssociatedSlot().setWidget(portletWidget);
        ComponentConnector parentConnector = Util.findConnectorFor(portletWidget.getContentWidget());
        PortalLayoutConnector originalConnector = ((PortalLayoutConnector) Util
                .findConnectorFor(portletWidget.getContentWidget()).getParent());
        if (originalConnector != portalConnector) {
            portalConnector.propagateHierarchyChangesToServer();
            originalConnector.propagateHierarchyChangesToServer();
        } else {
            portalConnector.updatePortletPositionOnServer(parentConnector);
        }
    }

    @Override
    public void onEnter(DragContext context) {
        super.onEnter(context);
        //assert draggable != null;

        dropTargetClientWidth = DOMUtil.getClientWidth(dropTarget.getElement());
        dropTargetClientHeight = DOMUtil.getClientHeight(dropTarget.getElement());
        calcDropTargetOffset();

        int draggableAbsoluteLeft = context.draggable.getAbsoluteLeft();
        int draggableAbsoluteTop = context.draggable.getAbsoluteTop();
        Widget widget = context.selectedWidgets.get(0);
        if (widget != null) {
            draggable = new Draggable(widget);
            draggable.positioner = makePositioner(widget);
            draggable.relativeX = widget.getAbsoluteLeft() - draggableAbsoluteLeft;
            draggable.relativeY = widget.getAbsoluteTop() - draggableAbsoluteTop;
        }
    }

    @Override
    public void onLeave(DragContext context) {
        //draggable.positioner.removeFromParent();
        draggable = null;
        super.onLeave(context);
    }

    @Override
    public void onMove(DragContext context) {
        super.onMove(context);
        draggable.desiredX = context.desiredDraggableX - dropTargetOffsetX + draggable.relativeX;
        draggable.desiredY = context.desiredDraggableY - dropTargetOffsetY + draggable.relativeY;
        draggable.desiredX = Math.max(0, Math.min(draggable.desiredX, dropTargetClientWidth - draggable.offsetWidth));
        draggable.desiredY = Math.max(0, Math.min(draggable.desiredY, dropTargetClientHeight - draggable.offsetHeight));
        dropTarget.add(draggable.positioner, draggable.desiredX, draggable.desiredY);
        if (context.dragController.getBehaviorScrollIntoView()) {
            draggable.positioner.getElement().scrollIntoView();
        }

        // may have changed due to scrollIntoView() or user driven scrolling
        calcDropTargetOffset();

        PortletChrome portletWidget = (PortletChrome) context.selectedWidgets.get(0);
        PortletSlot slot = portletWidget.getAssociatedSlot();
        if (panel != slot.getParent()) {
            PortalLayoutConnector originalConnector = ((PortalLayoutConnector) Util.findConnectorFor(slot.getParent()));
            originalConnector.setOutcomingPortletCandidate(portletWidget);
            portalConnector.setIncomingPortletCandidate(portletWidget);
        }
    }

    
    public Widget makePositioner(Widget reference) {
        // Use two widgets so that setPixelSize() consistently affects
        // dimensions
        // excluding positioner border in quirks and strict modes
        SimplePanel outer = new SimplePanel();
        outer.addStyleName(DragClientBundle.INSTANCE.css().positioner());
        outer.getElement().getStyle().setProperty("margin", "0px");

        // place off screen for border calculation
        RootPanel.get().add(outer, -500, -500);

        // Ensure IE quirks mode returns valid outer.offsetHeight, and thus
        // valid
        // DOMUtil.getVerticalBorders(outer)
        outer.setWidget(DUMMY_LABEL_IE_QUIRKS_MODE_OFFSET_HEIGHT);

        SimplePanel inner = new SimplePanel();
        inner.getElement().getStyle().setProperty("margin", "0px");
        inner.getElement().getStyle().setProperty("border", "none");
        int offsetWidth = reference.getOffsetWidth() - DOMUtil.getHorizontalBorders(outer);
        int offsetHeight = reference.getOffsetHeight() - DOMUtil.getVerticalBorders(outer);
        inner.setPixelSize(offsetWidth, offsetHeight);

        outer.setWidget(inner);

        return ((PortletChrome)reference).getAssociatedSlot();
    }

    private void calcDropTargetOffset() {
        WidgetLocation dropTargetLocation = new WidgetLocation(dropTarget, null);
        dropTargetOffsetX = dropTargetLocation.getLeft() + DOMUtil.getBorderLeft(dropTarget.getElement());
        dropTargetOffsetY = dropTargetLocation.getTop() + DOMUtil.getBorderTop(dropTarget.getElement());
        // System.out.println(dropTargetOffsetX + ", " + dropTargetOffsetY);
    }

}
