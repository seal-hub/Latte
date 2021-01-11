package com.google.android.accessibility.switchaccess.latte;

import android.view.accessibility.AccessibilityNodeInfo;

import java.util.HashMap;
import java.util.Map;

public class Command {


    WidgetInfo widgetInfo;
    
    String action;
    String actionExtra;
    boolean skip;
    int reportedAccessibilityIssues = 0;
    boolean hasWidgetA11YIssue = false;
    long startTime = -1;
    long endTime = -1;

    public long getTime(){
        return (endTime - startTime);
    }


    int executionState = 0;
    public final static int NOT_STARTED = 0;
    public final static int SEARCH = 1;
    public final static int COMPLETED = 2;
    public final static int FAILED = 3;
    public final static int COMPLETED_BY_REGULAR = 4;
    public final static int COMPLETED_BY_REGULAR_UNABLE_TO_DETECT = 5;
    public static String getActionStr(int action){
        switch (action){
            case NOT_STARTED:
                return "NOT_STARTED";
            case SEARCH:
                return "SEARCH";
            case COMPLETED:
                return "COMPLETED";
            case FAILED:
                return "FAILED";
            case COMPLETED_BY_REGULAR:
                return "COMPLETED_BY_REGULAR";
            case COMPLETED_BY_REGULAR_UNABLE_TO_DETECT:
                return "COMPLETED_BY_REGULAR_UNABLE_TO_DETECT";
            default:
                return "UNKNOWN";
        }
    }

    public final static String CMD_CLICK = "CLICK";
    public final static String CMD_TYPE = "TYPE";
    public final static String CMD_ASSERT = "ASSERT";

    public int numberOfAttempts = 0;
    private int numberOfActions = 0;
    private Map<String, Integer> visitedWidgets = new HashMap<>();
    public int trackAction(AccessibilityNodeInfo node){
        WidgetInfo widgetInfo = WidgetInfo.create(node);
        numberOfActions++;
        int visitedWidgetCount = visitedWidgets.getOrDefault(widgetInfo.getXpath(), 0);
        visitedWidgetCount++;
        visitedWidgets.put(widgetInfo.getXpath(), visitedWidgetCount);
        return visitedWidgetCount;
    }

    public void setReportedAccessibilityIssues(int reportedAccessibilityIssues) {
        this.reportedAccessibilityIssues = Integer.max(this.reportedAccessibilityIssues, reportedAccessibilityIssues);
    }

    public int getReportedAccessibilityIssues() {
        return reportedAccessibilityIssues;
    }


    public void setHasWidgetA11YIssue(boolean hasWidgetA11YIssue) {
        this.hasWidgetA11YIssue = hasWidgetA11YIssue;
    }

    public boolean isHasWidgetA11YIssue() {
        return hasWidgetA11YIssue;
    }

    public int getNumberOfActions() {
        return numberOfActions;
    }

    public final static int MAX_ATTEMPT = 4;
    public final static int MAX_VISITED_WIDGET = 4;
    public final static int MAX_INTERACTION = 50;

    public Command(WidgetInfo widgetInfo, String action) {
        this(widgetInfo, action, null);
    }

    public Command(WidgetInfo widgetInfo, String action, String actionExtra) {
        this(widgetInfo, action, actionExtra, false);
    }

    public Command(WidgetInfo widgetInfo, String action, String actionExtra, boolean skip) {
        this.widgetInfo = widgetInfo;
        this.action = action;
        this.actionExtra = actionExtra;
        this.skip = skip;
    }

    private long sleepTime = 0;
    public Command(long sleepTime){
        this(null, "sleep", "");
        this.sleepTime = sleepTime;
    }

    public boolean isSleep(){
        return action.equals("sleep");
    }

    public long getSleepTime() {
        return sleepTime;
    }

    public boolean shouldSkip() {
        return skip;
    }

    public WidgetInfo getTargetWidgetInfo() {
        return widgetInfo;
    }

    public int getExecutionState() {
        return executionState;
    }

    public void setExecutionState(int executionState) {
        this.executionState = executionState;
        if(executionState == NOT_STARTED){
            startTime = endTime = -1;
        }
        else if(executionState == SEARCH) {
            startTime = System.currentTimeMillis();
            endTime = -1;
        }
        else if(executionState == COMPLETED
                || executionState == COMPLETED_BY_REGULAR_UNABLE_TO_DETECT
                || executionState == COMPLETED_BY_REGULAR
                || executionState == FAILED){
            endTime = System.currentTimeMillis();
        }
    }

    public String getAction() {
        return action;
    }

    public String getActionExtra() {
        return actionExtra;
    }
}
