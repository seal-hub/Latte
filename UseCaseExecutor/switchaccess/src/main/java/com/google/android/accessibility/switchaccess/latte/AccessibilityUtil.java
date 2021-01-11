package com.google.android.accessibility.switchaccess.latte;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.AccessibilityServiceCompatUtils;
import com.google.android.accessibility.utils.traversal.OrderedTraversalStrategy;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckPreset;
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResult;
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResultUtils;
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityHierarchyCheck;
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityHierarchyCheckResult;
import com.google.android.apps.common.testing.accessibility.framework.checks.SpeakableTextPresentCheck;
import com.google.android.apps.common.testing.accessibility.framework.uielement.AccessibilityHierarchy;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class AccessibilityUtil {
    AccessibilityService accessibilityService;
    AccessibilityService.GestureResultCallback defaultCallBack;
    public AccessibilityUtil(AccessibilityService accessibilityService) {
        this.accessibilityService = accessibilityService;
        defaultCallBack = new AccessibilityService.GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                Log.i(TAG, "Complete Gesture " + gestureDescription.toString());
                super.onCompleted(gestureDescription);
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                Log.i(TAG, "Cancel Gesture " + gestureDescription.toString());
                super.onCancelled(gestureDescription);
            }
        };
    }

    void logTestGuideline(AccessibilityEvent event) throws JSONException {
        if(event == null)
            return;
        if(event.getEventType() != AccessibilityEvent.TYPE_VIEW_CLICKED
                && event.getEventType() != AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED
                && event.getEventType() != AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED
        )
            return;

        JSONObject guiEvent = new JSONObject();
        guiEvent.put("PackageName", event.getPackageName());
        guiEvent.put("EventText", event.getText());
        guiEvent.put("EventType", AccessibilityEvent.eventTypeToString(event.getEventType()));
        guiEvent.put("EventClass", event.getClassName());
        AccessibilityNodeInfo currentNode = event.getSource();
        if(currentNode != null){
            guiEvent.put("ViewID", currentNode.getViewIdResourceName());
            guiEvent.put("ContentDescription", currentNode.getContentDescription());
            guiEvent.put("Text", currentNode.getText());
            Rect box = new Rect();
            currentNode.getBoundsInScreen(box);
            guiEvent.put("Box", box);
        }
        else{
            guiEvent.put("ViewID", "");
            guiEvent.put("ContentDescription", "");
            guiEvent.put("Text", "");
            guiEvent.put("Box", "");
        }
        AccessibilityNodeInfo rootNode = this.accessibilityService.getRootInActiveWindow();
        if(rootNode != null ){
            guiEvent.put("Root", rootNode);
        }
        else{
            guiEvent.put("Root", "");
        }
        Log.i(AccessibilityUtil.INSTRUMENT_TAG, guiEvent.toString());
    }


    public static final String TAG = "LATTE_SA_2";
    public static final String INSTRUMENT_TAG = "LATTE_INSTRUMENT";
    public static final int tapDuration = 100;

    public boolean performTap(int x, int y){ return performTap(x, y, tapDuration); }
    public boolean performTap(int x, int y, int duration){ return performTap(x, y, 0, duration); }
    public boolean performTap(int x, int y, int startTime, int duration){ return performTap(x, y, startTime, duration, defaultCallBack); }
    public boolean performTap(int x, int y, int startTime, int duration, AccessibilityService.GestureResultCallback callback){
        if(x < 0 || y < 0)
            return false;
        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        Path swipePath = new Path();
        swipePath.moveTo(x, y);
        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(swipePath, startTime, duration));
        GestureDescription gestureDescription = gestureBuilder.build();
        Log.i(TAG, "Execute Gesture " + gestureDescription.toString());
        return accessibilityService.dispatchGesture(gestureDescription, callback, null);
    }

    public static boolean performType(AccessibilityNodeInfo node, String message){
        Log.i(TAG, "performType");
        Bundle arguments = new Bundle();
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, message);
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
    }


    public boolean performDoubleTap(){
        Log.i(TAG, "performDoubleTap");
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return performDoubleTap(0, 0); }
    public boolean performDoubleTap(int x, int y){ return performDoubleTap(x, y, tapDuration); }
    public boolean performDoubleTap(int x, int y, int duration){ return performDoubleTap(x, y, 0, duration); }
    public boolean performDoubleTap(int x, int y, int startTime, int duration){ return performDoubleTap(x, y, startTime, duration, defaultCallBack); }
    public boolean performDoubleTap(final int x, final int y, final int startTime, final int duration, final AccessibilityService.GestureResultCallback callback){
        AccessibilityService.GestureResultCallback newClickCallBack = new AccessibilityService.GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                Log.i(TAG, "Complete Gesture " + gestureDescription.getStrokeCount());
                super.onCompleted(gestureDescription);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                performTap(x, y, startTime, duration, callback);
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                Log.i(TAG, "Cancel Gesture");
                super.onCancelled(gestureDescription);
            }
        };
        return performTap(x, y, startTime, duration, newClickCallBack);
    }


    public static List<AccessibilityNodeInfo> getAllA11yNodeInfo(boolean log){
        return getAllA11yNodeInfo(com.android.switchaccess.SwitchAccessService.getInstance().getRootInActiveWindow(), " /", log);
    }

    public static List<AccessibilityNodeInfo> getAllA11yNodeInfo(AccessibilityNodeInfo rootNode, boolean log){
        return getAllA11yNodeInfo(rootNode, " /", log);
    }

    private static List<AccessibilityNodeInfo> getAllA11yNodeInfo(AccessibilityNodeInfo rootNode, String prefix, boolean log){
        if(rootNode == null) {
            Log.i(AccessibilityUtil.TAG, "The root node is null!");
            return new ArrayList<>();
        }
        List<AccessibilityNodeInfo> result = new ArrayList<>();
        result.add(rootNode);
        if(log)
            Log.i(AccessibilityUtil.TAG, prefix + "/" + rootNode.getClassName() + " " + rootNode.getViewIdResourceName() + " " + rootNode.getText() + " " + rootNode.getContentDescription());
        for(int i=0; i<rootNode.getChildCount(); i++)
            result.addAll(getAllA11yNodeInfo(rootNode.getChild(i), " " +prefix+rootNode.getClassName()+"/", log));
        return result;
    }

    public static AccessibilityNodeInfo getNextFocusedNode(AccessibilityNodeInfo currentNodeInfo){
        Log.i(TAG, "In getNextFocusedNode: ");
        AccessibilityNodeInfoCompat currentNode = AccessibilityNodeInfoUtils.toCompat(currentNodeInfo);
        AccessibilityNodeInfoCompat rootNode = AccessibilityServiceCompatUtils.getRootInActiveWindow(com.android.switchaccess.SwitchAccessService.getInstance());
        Log.i(TAG, "Root Node : " + rootNode);
        if(rootNode == null)
            return null;
        AccessibilityNodeInfoCompat nextNode = currentNode;
        TraversalStrategy traversal = (TraversalStrategy) new OrderedTraversalStrategy(rootNode);
        Log.i(TAG, "Traversal : " + traversal);
        try {
            while ( nextNode != null) {
                nextNode = traversal.findFocus(nextNode, TraversalStrategy.SEARCH_FOCUS_FORWARD);
                if(AccessibilityNodeInfoUtils.shouldFocusNode(nextNode))
                    break;
            }
        } finally {
            Log.i(TAG, "Here");
            traversal.recycle();
        }
        Log.i(TAG, "Next Focousable Node: " + nextNode);
        if(nextNode != null) {
            Rect nextBox = new Rect();
            nextNode.getBoundsInScreen(nextBox);
            Rect currentBox = new Rect();
            currentNode.getBoundsInScreen(currentBox);
            if(currentBox.contains(nextBox.centerX(), nextBox.centerY()))
                return null;
            return nextNode.unwrap();
        }
        return null;
    }

    public static boolean enableA11yReport = true;

    public static List<AccessibilityHierarchyCheckResult> getA11yIssues(AccessibilityNodeInfo rootNode){
        return getA11yIssues(rootNode, true);
    }

    public static List<AccessibilityHierarchyCheckResult> getA11yIssues(AccessibilityNodeInfo rootNode, boolean justError){
        Set<AccessibilityHierarchyCheck> checks =
                AccessibilityCheckPreset.getAccessibilityHierarchyChecksForPreset(
                        AccessibilityCheckPreset.LATEST);
        return getA11yIssues(rootNode, justError, checks);
    }

    public static List<AccessibilityHierarchyCheckResult> getA11yIssues(AccessibilityNodeInfo rootNode, boolean justError, Set<AccessibilityHierarchyCheck> checks){
        if(rootNode == null || !enableA11yReport)
            return Collections.emptyList();
        Context context = com.android.switchaccess.SwitchAccessService.getInstance().getApplicationContext();
        AccessibilityCheckPreset.getHierarchyCheckForClass(SpeakableTextPresentCheck.class);
        AccessibilityHierarchy hierarchy = AccessibilityHierarchy.newBuilder(rootNode, context).build();
        List<AccessibilityHierarchyCheckResult> results = new ArrayList<>();
        for (AccessibilityHierarchyCheck check : checks) {
            results.addAll(check.runCheckOnHierarchy(hierarchy));
        }
        List<AccessibilityHierarchyCheckResult> returnedResult = AccessibilityCheckResultUtils.getResultsForType(
                results, AccessibilityCheckResult.AccessibilityCheckResultType.ERROR);
        if(!justError)
            returnedResult.addAll(AccessibilityCheckResultUtils.getResultsForType(
                    results, AccessibilityCheckResult.AccessibilityCheckResultType.WARNING));
        return returnedResult;
    }

    public static List<AccessibilityNodeInfo> findNodesWithoutMasks(WidgetInfo target){
        return privateFindNodes(target, new ArrayList<>());
    }

    public static List<AccessibilityNodeInfo> findNodes(WidgetInfo target){
        return privateFindNodes(target, WidgetInfo.maskedAttributes);
    }

    private static List<AccessibilityNodeInfo> privateFindNodes(WidgetInfo target, List<String> myMaskedAttributes){
        List<AccessibilityNodeInfo> result = new ArrayList<>();
        List<AccessibilityNodeInfo> nonVisibleResult = new ArrayList<>();
//        Log.i(TAG, " Looking for Widget: " + target + " Masks: " + WidgetInfo.maskedAttributes);
        for(AccessibilityNodeInfo node : getAllA11yNodeInfo(false)) {
            if(!node.isVisibleToUser())
                continue;
            WidgetInfo currentNodeInfo = WidgetInfo.create(node);
//            Log.i(TAG, " Is similar?" + currentNodeInfo);
            if (target.isSimilarWithoutContext(currentNodeInfo, myMaskedAttributes)) {
//                Log.i(TAG, " \tYes");
                if(node.isVisibleToUser())
                    result.add(node);
                else
                    nonVisibleResult.add(node);
            }
        }
        if(result.size() == 0)
            result.addAll(nonVisibleResult);
        if(result.size() > 1){
            List<AccessibilityNodeInfo> filteredResult = new ArrayList<>();
            for(AccessibilityNodeInfo node : result){
                WidgetInfo currentNodeInfo = WidgetInfo.create(node);
                if(target.isSimilarContext(currentNodeInfo))
                    filteredResult.add(node);
            }
            result = filteredResult;
        }
        return result;
    }

}
