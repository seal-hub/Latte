package com.google.android.accessibility.switchaccess.latte;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.RequiresApi;

import com.android.switchaccess.SwitchAccessService;
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckPreset;
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityHierarchyCheck;
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityHierarchyCheckResult;
import com.google.android.apps.common.testing.accessibility.framework.checks.DuplicateSpeakableTextCheck;
import com.google.android.apps.common.testing.accessibility.framework.checks.EditableContentDescCheck;
import com.google.android.apps.common.testing.accessibility.framework.checks.RedundantDescriptionCheck;
import com.google.android.apps.common.testing.accessibility.framework.checks.SpeakableTextPresentCheck;
import com.google.android.apps.common.testing.accessibility.framework.checks.TraversalOrderCheck;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class TalkBackCommandExecutor {
    private static AccessibilityNodeInfo focusedNode;
    private static AccessibilityNodeInfo lastFocusedNode;
    private static boolean changedFocused = false;
    private static int waitingForFocusChange = 0;
    private static final int MAX_WAIT_FOR_CHANGE = 3;
    public static Map<Integer, String> pendingActions = new HashMap<>();
    public static int pendingActionId = 0;
    public static interface MyCallBack{
        void callback();
    }
    public static void setFocusedNode(AccessibilityNodeInfo node){
        focusedNode = node;
        changedFocused = true;
    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    public static int executeCommand(Command command){
        Log.i(AccessibilityUtil.TAG, String.format("TalkBack Command state: %s, action: %s, actionExtra: %s, target: %s, skip: %s.",
                command.getExecutionState(),
                command.getAction(),
                command.getActionExtra(),
                command.getTargetWidgetInfo(),
                command.skip));
        if(command.shouldSkip()){
            int result = RegularCommandExecutor.executeInTalkBack(command);
            return result;
        }
        Log.i(AccessibilityUtil.TAG, "-- Current Focused Node" + focusedNode);
        if(focusedNode == null){
            waitingForFocusChange++;
            if(waitingForFocusChange < MAX_WAIT_FOR_CHANGE) {
                Log.i(AccessibilityUtil.TAG, "Ignore this manageCommand since no node has been changed " + waitingForFocusChange);
                return command.getExecutionState();
            }
            waitingForFocusChange = 0;
        }
        if(pendingActions.size() > 0){
            Log.i(AccessibilityUtil.TAG, "Ignore this manageCommand since we're waiting for another action");
            return command.getExecutionState();
        }
        AccessibilityUtil accessibilityUtil = new AccessibilityUtil(SwitchAccessService.getInstance());
        switch (command.getExecutionState()){
            case Command.NOT_STARTED:
                Log.i(AccessibilityUtil.TAG, "--- Change to SEARCH");
                command.setExecutionState(Command.SEARCH);
            case Command.SEARCH:
                List<AccessibilityNodeInfo> matchedNodes = AccessibilityUtil.findNodesWithoutMasks(command.getTargetWidgetInfo());
                if(matchedNodes.size() != 1){
                    Log.i(AccessibilityUtil.TAG, "The target widget is not unique. " + matchedNodes.size());
                    command.setExecutionState(Command.FAILED);
                    return command.getExecutionState();
                }
                command.setHasWidgetA11YIssue(getA11yIssues(matchedNodes.get(0)).size() > 0);
                if(command.getNumberOfActions() >= Command.MAX_INTERACTION){
                    Log.i(AccessibilityUtil.TAG, "Max interaction threashold reached. Go with regular");
                    RegularCommandExecutor.executeInTalkBack(command);
                    if(RegularCommandExecutor.executeInTalkBack(command) == Command.COMPLETED)
                        command.setExecutionState(Command.COMPLETED_BY_REGULAR);
                    return command.getExecutionState();
                }
                int visitedWidget = command.trackAction(focusedNode);
                if(focusedNode == null) {
                    Log.i(AccessibilityUtil.TAG, "--- Node is not focused. Do NEXT");
                    performNext(null);
                    return command.getExecutionState();
                }
                Log.i(AccessibilityUtil.TAG, String.format("The following widget has been visited %d times: %s", visitedWidget, WidgetInfo.create(focusedNode)));
                if(visitedWidget > Command.MAX_VISITED_WIDGET){
                    Log.i(AccessibilityUtil.TAG, "Max Visitied Widget threshold. Execute the command using regular test executor");
                    if(RegularCommandExecutor.executeInTalkBack(command) == Command.COMPLETED)
                        command.setExecutionState(Command.COMPLETED_BY_REGULAR);
                    return command.getExecutionState();
                }
                List<AccessibilityNodeInfo> similarNodes = AccessibilityUtil.findNodes(command.getTargetWidgetInfo());
                if(similarNodes.size() == 0){
                    Log.i(AccessibilityUtil.TAG, "--- No similar widget found, continue exploration");
                    performNext(null);
                    return command.getExecutionState();
                }
                AccessibilityNodeInfo targetNode = similarNodes.get(0);
                AccessibilityNodeInfo firstReachableNode = targetNode;
                boolean isSimilar = firstReachableNode != null && firstReachableNode.equals(focusedNode);
                if(!isSimilar) {
                    AccessibilityNodeInfo it = targetNode;
                    while (it != null) {
                        if (it.isClickable()) {
                            firstReachableNode = it;
                            break;
                        }
                        it = it.getParent();
                    }
                    Log.i(AccessibilityUtil.TAG, "-- FIRST REACHABLE NODE IS " + firstReachableNode);
                    isSimilar = firstReachableNode != null && firstReachableNode.equals(focusedNode);
                }
                if (!isSimilar){
                    Log.i(AccessibilityUtil.TAG, "--- Continue the navigation");
                    performNext(null);
                    return command.getExecutionState();
                }
                if(!targetNode.equals(matchedNodes.get(0))){
                    Log.i(AccessibilityUtil.TAG, "The located widget is not correct, use regular executor");
                    if(RegularCommandExecutor.executeInTalkBack(command) == Command.COMPLETED)
                        command.setExecutionState(Command.COMPLETED_BY_REGULAR);
                    return command.getExecutionState();
                }
                switch (command.getAction()) {
                    case Command.CMD_ASSERT:
                        Log.i(AccessibilityUtil.TAG, "--- Do ASSERT");
                        break;
                    case Command.CMD_CLICK:
                        Log.i(AccessibilityUtil.TAG, "--- Do CLICK");
                        accessibilityUtil.performDoubleTap();
                        changedFocused = false;
                        break;
                    case Command.CMD_TYPE:
                        Log.i(AccessibilityUtil.TAG, "--- Do TYPE AND NEXT");
                        AccessibilityUtil.performType(focusedNode, command.getActionExtra());
                        break;
                    default:
                        Log.i(AccessibilityUtil.TAG, "Command's action is unknown " + command.getAction());
                        break;
                }
                command.setExecutionState(Command.COMPLETED);
                break;
            default:
                Log.i(AccessibilityUtil.TAG, "What I'm doing here?");
        }
        return command.getExecutionState();
    }

    private static boolean randomExplore() {
        Log.i(AccessibilityUtil.TAG, "Focus on next leaf element");
        AccessibilityNodeInfo nextLeafNode = null;
        List<AccessibilityNodeInfo> allNodes = AccessibilityUtil.getAllA11yNodeInfo(false);
        for(int i=0; i<allNodes.size(); i++){
            if(allNodes.get(i).equals(focusedNode)){
                Log.i(AccessibilityUtil.TAG, "Found it");
                for(int j=i+1; j<allNodes.size(); j++){
                    String cls = String.valueOf(allNodes.get(j).getClassName());
                    if(cls.endsWith("TextView") || cls.endsWith("ImageView") || cls.endsWith("ImageButton") || cls.endsWith(".EditText")) {
                        nextLeafNode = allNodes.get(j);
                        break;
                    }
                }
                break;
            }
        }
        if(nextLeafNode != null){
            Log.i(AccessibilityUtil.TAG, "Next Leaf Node: " + nextLeafNode);
            AccessibilityUtil autil = new AccessibilityUtil(SwitchAccessService.getInstance());
            Rect box = new Rect();
            nextLeafNode.getBoundsInScreen(box);
            int x = (box.left + box.right) / 2;
            int y = (box.top + box.bottom) / 2;
            if(x >= 0 && y >= 0) {
                autil.performTap(x, y);
                return true;
            }
        }
        return false;
    }

    public static boolean  performNext(AccessibilityService.GestureResultCallback callback){
        Log.i(AccessibilityUtil.TAG, "performNext");
        final int thisActionId = pendingActionId;
        pendingActionId++;
                pendingActions.put(thisActionId, "Pending: I'm going to do NEXT");
        long gestureDuration = 400;
        new Handler().postDelayed(() -> {
            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
            Path swipePath = new Path();
            Random random = new Random();
            int BASE = 5;
            int dx1 = random.nextInt(2 * BASE) - BASE;
            int dx2 = random.nextInt(2 * BASE) - BASE;
            int dy1 = random.nextInt(2 * BASE) - BASE;
            int dy2 = random.nextInt(2 * BASE) - BASE;
            int x1 = 50 + dx1;
            int x2 = 500 + dx2;
            int y1 = 500 + dy1;
            int y2 = 600 + dy2;
            swipePath.moveTo(x1, y1);
            swipePath.lineTo(x2, y2);
            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(swipePath, 0, gestureDuration));
            GestureDescription gestureDescription = gestureBuilder.build();
            Log.i(AccessibilityUtil.TAG, "Execute Gesture " + gestureDescription.toString());
            SwitchAccessService.getInstance().dispatchGesture(gestureDescription, callback, null);
        }, 10);
    new Handler().postDelayed(() -> pendingActions.remove(thisActionId), gestureDuration);
changedFocused = false;
        return false;
    }

    public static List<AccessibilityHierarchyCheckResult> getA11yIssues(AccessibilityNodeInfo rootNode){
        Set<AccessibilityHierarchyCheck> checks = new HashSet<>(Arrays.asList(
                AccessibilityCheckPreset.getHierarchyCheckForClass(TraversalOrderCheck.class),
                AccessibilityCheckPreset.getHierarchyCheckForClass(SpeakableTextPresentCheck.class),
                AccessibilityCheckPreset.getHierarchyCheckForClass(EditableContentDescCheck.class),
                AccessibilityCheckPreset.getHierarchyCheckForClass(DuplicateSpeakableTextCheck.class),
                AccessibilityCheckPreset.getHierarchyCheckForClass(RedundantDescriptionCheck.class)
        ));
        return AccessibilityUtil.getA11yIssues(rootNode, true, checks);
    }



}
