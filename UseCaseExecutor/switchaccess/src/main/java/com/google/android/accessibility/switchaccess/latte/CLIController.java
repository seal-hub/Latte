package com.google.android.accessibility.switchaccess.latte;


import android.os.Handler;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.android.switchaccess.SwitchAccessService;
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityHierarchyCheckResult;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Scanner;

public class CLIController {
    public static boolean isPolling = false;
    private static boolean updated = false;
    private static long delayBetweenCommands = 500;
    private static String commandFilePath = "/data/local/tmp/command.txt";

    public static void readInput(){
        isPolling = true;
        SwitchAccessService service = SwitchAccessService.getInstance();
        if(service == null || !service.connected) {
            isPolling = false;
            return;
        }
        File file = new File(commandFilePath);
        try {
            FileInputStream fileInputStream = new FileInputStream(file);
            Scanner scanner = new Scanner(fileInputStream);
            String nextCommand = "NONE";
            if(scanner.hasNext())
                nextCommand = scanner.next();
            fileInputStream.close();
            if(nextCommand.equals("NONE") || nextCommand.equals("")){

            }
            else {
                WidgetInfo.createAll(AccessibilityUtil.getAllA11yNodeInfo(false));
                if (nextCommand.equals("log")) {
                    Log.i(AccessibilityUtil.TAG, "CMD: log");
                    logCurrentState();
                    clearCommandFile();
                } else if (nextCommand.equals("a11y_scan")) {
                    Log.i(AccessibilityUtil.TAG, "CMD: a11y_scan");
                    AccessibilityNodeInfo rootNode = SwitchAccessService.getInstance().getRootInActiveWindow();
                    List<AccessibilityHierarchyCheckResult> errors = AccessibilityUtil.getA11yIssues(rootNode);
                    for(AccessibilityHierarchyCheckResult error : errors)
                        Log.i(AccessibilityUtil.TAG, " A11y-Error " + error.toString());
                    clearCommandFile();
                } else if (nextCommand.equals("log_widgets")) {
                    Log.i(AccessibilityUtil.TAG, "CMD: log_widgets");
                    for (AccessibilityNodeInfo node : AccessibilityUtil.getAllA11yNodeInfo(false))
                        Log.i(AccessibilityUtil.TAG,  "  " + WidgetInfo.getWidget(node));
                    clearCommandFile();
                } else if (nextCommand.startsWith("mask_")) {
                    Log.i(AccessibilityUtil.TAG, "CMD: mask");
                    String[] tokens = nextCommand.split("_");
                    Log.i(AccessibilityUtil.TAG, "tokens " + tokens);
                    WidgetInfo.maskedAttributes.clear();
                    for (int i = 1; i < tokens.length; i++) {
                        Log.i(AccessibilityUtil.TAG, "  token " + tokens[i]);
                        WidgetInfo.maskedAttributes.add(tokens[i]);
                    }
                    clearCommandFile();
                }
                else if (nextCommand.startsWith("talk_")) {
                    Log.i(AccessibilityUtil.TAG, "CMD: talk");
                    String talk_cmd = nextCommand.substring("talk_".length());
                    if(talk_cmd.equals("top")){
                        // TODO: Not implemented
                    }
                    else if(talk_cmd.equals("next")){
                        TalkBackCommandExecutor.performNext(null);
                    }
                    else if(talk_cmd.equals("stop")){
                        // TODO: Not implemented
                    }
                    clearCommandFile();
                }
                else if (nextCommand.equals("init")) {
                    Log.i(AccessibilityUtil.TAG, "CMD: init");
                    CommandManager.initCommands();
                    clearCommandFile();
                } else if (nextCommand.startsWith("goto_")) {
                    Log.i(AccessibilityUtil.TAG, "CMD: goto");
                    CommandManager.setLastCommandIndex(Integer.parseInt(nextCommand.substring("goto_".length())));
                    clearCommandFile();
                } else if (nextCommand.equals("next")) {
                    Log.i(AccessibilityUtil.TAG, "CMD: next");
                    CommandManager.manageCommands();
                    clearCommandFile();
                } else if (nextCommand.equals("start")) {
                    Log.i(AccessibilityUtil.TAG, "CMD: start");
                    if (!CommandManager.manageCommands())
                        clearCommandFile();
                } else if (nextCommand.equals("prev")) {
                    Log.i(AccessibilityUtil.TAG, "CMD: prev");
                    CommandManager.manageCommands(false);
                    clearCommandFile();
                }
                else if (nextCommand.startsWith("delay_")) {
                    Log.i(AccessibilityUtil.TAG, "CMD: set delay");
                    delayBetweenCommands = Long.parseLong(nextCommand.substring("delay_".length()));
                    Log.i(AccessibilityUtil.TAG, "CMD: the delay between each command is " + delayBetweenCommands);
                    clearCommandFile();
                }
                else if (nextCommand.startsWith("A11yReport_")) {
                    Log.i(AccessibilityUtil.TAG, "CMD: set A11yReport");
                    AccessibilityUtil.enableA11yReport = Boolean.parseBoolean(nextCommand.substring("A11yReport_".length()));
                    Log.i(AccessibilityUtil.TAG, "CMD: A11yReport enabled: " + AccessibilityUtil.enableA11yReport);
                    clearCommandFile();
                }
                else if (nextCommand.startsWith("executor_")) {
                    Log.i(AccessibilityUtil.TAG, "CMD: set executor");
                    String executor = nextCommand.substring("executor_".length());
                    if(executor.equals("switch")) {
                        Log.i(AccessibilityUtil.TAG, "CMD: The current executor is set to SwitchAccess");
                        CommandManager.setTestExecutor(SwitchAccessCommandExecutor::executeCommand);
                        CommandManager.setA11yIssueReporter(SwitchAccessCommandExecutor::getA11yIssues);
                    }
                    else if(executor.equals("regular")) {
                        Log.i(AccessibilityUtil.TAG, "CMD: The current executor is set to Regular");
                        CommandManager.setTestExecutor(RegularCommandExecutor::executeCommand);
//                        CommandManager.setA11yIssueReporter(RegularCommandExecutor::getA11yIssues);
                        CommandManager.setA11yIssueReporter(RegularCommandExecutor::getA11yIssues);
                    }
                    else if(executor.equals("talk")) {
                        Log.i(AccessibilityUtil.TAG, "CMD: The current executor is set to TalkBack");
                        CommandManager.setTestExecutor(TalkBackCommandExecutor::executeCommand);
                        CommandManager.setA11yIssueReporter(TalkBackCommandExecutor::getA11yIssues);
                    }
                    else
                        Log.i(AccessibilityUtil.TAG, "CMD: The requested executor is unknown! " + executor);
                    clearCommandFile();
                }
                Log.i(AccessibilityUtil.TAG,"--------- Complete command " + nextCommand);
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        new Handler().postDelayed(CLIController::readInput, delayBetweenCommands);
    }


    public static void clearCommandFile(){
        File file = new File(commandFilePath);
        try {
            FileWriter fileWriter = new FileWriter(file);
            fileWriter.write("NONE\n");
            fileWriter.close();
        }  catch (IOException e) {
            e.printStackTrace();
        }

    }


    public static void onAccessibilityEvent(AccessibilityEvent event)
    {
        updated = true;
        if(event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED
        ||event.getEventType() == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
            Log.i(AccessibilityUtil.TAG, "Event " + AccessibilityEvent.eventTypeToString(event.getEventType()));
        if(event.getEventType() == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
            TalkBackCommandExecutor.setFocusedNode(event.getSource());

    }

    public static void logCurrentState(){
        AccessibilityNodeInfo rootNode = com.android.switchaccess.SwitchAccessService.getInstance().getRootInActiveWindow();
        Log.i(AccessibilityUtil.TAG, "RootNode: "+ rootNode);
        AccessibilityUtil.getAllA11yNodeInfo(rootNode, true);
    }
}
