package com.google.android.accessibility.switchaccess.latte;

import androidx.annotation.NonNull;

import com.google.android.accessibility.switchaccess.SwitchAccessNodeCompat;


public class SwitchAccessWidgetInfo extends WidgetInfo {
    boolean isMenu = false;
    boolean isLastNode = false;
    SwitchAccessNodeCompat switchAccessNodeCompat;

    public SwitchAccessWidgetInfo(String resourceId) {
        super(resourceId);
    }

    public SwitchAccessWidgetInfo(String resourceId, String contentDescription, String text, String clsName) {
        super(resourceId, contentDescription, text, clsName);
    }

    public static SwitchAccessWidgetInfo create(boolean isMenu,
                                                boolean isLastNode,
                                                SwitchAccessNodeCompat nodeCompat){
        if(isMenu || isLastNode) {
            SwitchAccessWidgetInfo widgetInfo = new SwitchAccessWidgetInfo("");
            widgetInfo.isLastNode = isLastNode;
            widgetInfo.isMenu = isMenu;
            return widgetInfo;
        }
        if (nodeCompat == null){
            return new SwitchAccessWidgetInfo("");
        }
        WidgetInfo superWidgetInfo = WidgetInfo.create(nodeCompat.unwrap());
        SwitchAccessWidgetInfo newWidgetInfo = new SwitchAccessWidgetInfo(
                superWidgetInfo.getAttr("resourceId"),
                superWidgetInfo.getAttr("contentDescription"),
                superWidgetInfo.getAttr("text"),
                superWidgetInfo.getAttr("class"));
        newWidgetInfo.setLocatedBy("xpath");
        newWidgetInfo.switchAccessNodeCompat = nodeCompat;
        newWidgetInfo.setXpath(superWidgetInfo.getXpath());
        return newWidgetInfo;
    }

    @Override
    public boolean isSimilar(WidgetInfo other) {
        return isSimilarAttribute(other, "xpath");
    }

    public SwitchAccessNodeCompat getSwitchAccessNodeCompat() {
        return switchAccessNodeCompat;
    }

    @NonNull
    @Override
    public String toString() {
        if(isMenu)
            return "WidgetInfo: Menu";
        if(isLastNode)
            return "WidgetInfo: LastNode";
        return super.toString();
    }
}
