package com.google.android.accessibility.switchaccess.latte;

import android.os.Handler;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;


import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.android.switchaccess.SwitchAccessService;
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityHierarchyCheckResult;

public class CommandManager {
    public static int lastCommandIndex = -1;
    public static List<Command> commandList = new ArrayList<>();
    private static long startTime = -1;
    private static long endTime = -1;

    public interface TestExecutor{
        int executeCommand(Command cmd);
    }

    public interface A11yIssueReporter{
        List<AccessibilityHierarchyCheckResult> getA11yIssues(AccessibilityNodeInfo rootNode);
    }

    public static void setTestExecutor(TestExecutor testExecutor) {
        CommandManager.testExecutor = testExecutor;
    }

    public static void setA11yIssueReporter(A11yIssueReporter a11yIssueReporter) {
        CommandManager.a11yIssueReporter = a11yIssueReporter;
    }

    private static TestExecutor testExecutor;
    private static A11yIssueReporter a11yIssueReporter;

    public static boolean manageCommands(){
        return manageCommands(true);
    }

    private static boolean sleepLock = false;

    public static boolean manageCommands(boolean isNext){
        if(sleepLock) {
            Log.i(AccessibilityUtil.TAG, "I'm sleeping!");
            return true;
        }
        if(lastCommandIndex < 0) {
            return false;
        }
        if(lastCommandIndex == 0)
            startTime = System.currentTimeMillis();
        if(!isNext)
            lastCommandIndex--;
        if(lastCommandIndex >= commandList.size()) {
            Log.i(AccessibilityUtil.TAG, "----------The test case is completed!---------");
            if(endTime == -1)
                endTime = System.currentTimeMillis();
            writeResult();
            return false;
        }
        Log.i(AccessibilityUtil.TAG, "Executing command " + (lastCommandIndex+1) + " Masks " + WidgetInfo.maskedAttributes);
        Command currentCommand = commandList.get(lastCommandIndex);
        if(!isNext)
            currentCommand.setExecutionState(Command.NOT_STARTED);
        if(currentCommand.getExecutionState() == Command.FAILED)
            currentCommand.setExecutionState(Command.NOT_STARTED);
        currentCommand.setReportedAccessibilityIssues(reportA11yIssues());
        if(currentCommand.isSleep()){
            sleepLock = true;
            Log.i(AccessibilityUtil.TAG, "Sleep Command " + currentCommand.getSleepTime());
            new Handler().postDelayed(()->{
                sleepLock = false;
                currentCommand.setExecutionState(Command.COMPLETED);
                Log.i(AccessibilityUtil.TAG, "Command " + (lastCommandIndex+1) + " is completed!");
                lastCommandIndex++;
            }, currentCommand.getSleepTime()*1000);
            return true;
        }
//        if(currentCommand.shouldSkip()){
//            int result = RegularCommandExecutor.executeInTalkBack(currentCommand);
//            if(result == Command.COMPLETED) {
//                Log.i(AccessibilityUtil.TAG, "Command " + (lastCommandIndex+1) + " is completed!");
//                lastCommandIndex++;
//            }
//            else if(result == Command.FAILED) {
//                Log.i(AccessibilityUtil.TAG, "Command " + (lastCommandIndex + 1) + " is failed!");
//                lastCommandIndex++;
//            }
//        }
//        else {
            int result = executeCommand(currentCommand);
            if (result == Command.COMPLETED
                    || result == Command.COMPLETED_BY_REGULAR
                    || result == Command.COMPLETED_BY_REGULAR_UNABLE_TO_DETECT) {
                Log.i(AccessibilityUtil.TAG, "Command " + (lastCommandIndex + 1) + " is completed!");
                lastCommandIndex++;
            } else if (result == Command.FAILED) {
                Log.i(AccessibilityUtil.TAG, "Command " + (lastCommandIndex + 1) + " is failed!");
                lastCommandIndex++;
            }
//        }
        return true;
    }

    public static void initCommands(){
        commandList.clear();
        startTime = -1;
        endTime = -1;
        {
            String resultFileName = "test_result.txt";
            // TODO: check context.getFilesDir().getPath();
            String resultDir = com.android.switchaccess.SwitchAccessService.getInstance().getBaseContext().getFilesDir().getPath();
//        String dir = "/data/local/tmp/alaki";
//            String dir = context.getFilesDir().getPath();
            Log.i(AccessibilityUtil.TAG, "Path: " + resultDir);
            File resultFile = new File(resultDir, resultFileName);
            resultFile.delete();
        }
        String fileName = "test_guideline.json";
        // TODO: check context.getFilesDir().getPath();
        String dir = "/data/local/tmp/";
//            String dir = context.getFilesDir().getPath();
        Log.i(AccessibilityUtil.TAG, "Path: " + dir);
        File file = new File(dir, fileName);
        //JSON parser object to parse read file
        JSONParser jsonParser = new JSONParser();
        JSONArray commandsJson = null;
        try (FileReader reader = new FileReader(file))
        {
            // TODO: tons of refactor!
            //Read JSON file
            Object obj = jsonParser.parse(reader);
            commandsJson = (JSONArray) obj;
            for(int i=0; i<commandsJson.size(); i++){
//                for(int i=0; i<5; i++){
                JSONObject cmd = (JSONObject) commandsJson.get(i);
                if(cmd == null){
                    Log.i(AccessibilityUtil.TAG, "Json Command is null!");
                    continue;
                }
                long sleepTime = Long.parseLong((String) cmd.getOrDefault("sleep", "-1"));
                if(sleepTime > -1) {
                    Log.i(AccessibilityUtil.TAG, "Json " + "sleep" + " " + sleepTime);
                    commandList.add(new Command(sleepTime));
                }
                else {
                    boolean skip = (boolean) cmd.getOrDefault("skip", false);
                    String action = (String) cmd.getOrDefault("action", "UNKNOWN");
                    if (action.equals("click"))
                        action = Command.CMD_CLICK;
                    else if (action.equals("send_keys"))
                        action = Command.CMD_TYPE;
                    else
                        action = "UNKNOWN";
                    WidgetInfo widgetInfo = WidgetInfo.createFromJson(cmd);
                    String message = "";
                    if (cmd.containsKey("action_args")) {
                        JSONArray args = (JSONArray) cmd.get("action_args");
                        if (action.equals(Command.CMD_TYPE))
                            message = String.valueOf(args.get(0));
                        else
                            Log.i(AccessibilityUtil.TAG, "-------> Args: " + args);
                    }
                    Log.i(AccessibilityUtil.TAG, "Json " + action + " " + message + " " + widgetInfo);
                    commandList.add(new Command(widgetInfo, action, message, skip));
                }

            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        lastCommandIndex = 0;
    }

    public static void writeResult(){
        String fileName = "test_result.txt";
        // TODO: check context.getFilesDir().getPath();
        String dir = com.android.switchaccess.SwitchAccessService.getInstance().getBaseContext().getFilesDir().getPath();
//        String dir = "/data/local/tmp/alaki";
//            String dir = context.getFilesDir().getPath();
        Log.i(AccessibilityUtil.TAG, "Path: " + dir);
        File file = new File(dir, fileName);
        FileWriter myWriter = null;
        try {
            myWriter = new FileWriter(file);
            long totalTime = endTime -  startTime;
            long totalEvents = 0;
            int completeCount = 0;
            int unlocatedCount = 0;
            int unreachableCount = 0;
            int firstProbelmaticCommand = -1;
            int failedCount = 0;
            int reportedA11yIssues = 0;
            for(int i=0; i<commandList.size(); i++) {
                Command cmd = commandList.get(i);
                totalEvents += cmd.getNumberOfActions();
                if(cmd.getExecutionState() != Command.COMPLETED && firstProbelmaticCommand < 0)
                    firstProbelmaticCommand = i+1;
                if(cmd.getExecutionState() == Command.COMPLETED)
                    completeCount++;
                else if(cmd.getExecutionState() == Command.COMPLETED_BY_REGULAR)
                    unreachableCount++;
                else if(cmd.getExecutionState() == Command.COMPLETED_BY_REGULAR_UNABLE_TO_DETECT)
                    unlocatedCount++;
                else if(cmd.getExecutionState() == Command.FAILED)
                    failedCount++;
                reportedA11yIssues += cmd.getReportedAccessibilityIssues();
                String message = String.format("   CMD: %d $ State: %s $ #Events: %d $ Time: %d $ A11yIssueCount: %d $ WidgetIssue: %s",
                        i + 1, Command.getActionStr(cmd.getExecutionState()),
                        cmd.getNumberOfActions(),
                        cmd.getTime(),
                        cmd.getReportedAccessibilityIssues(),
                        cmd.isHasWidgetA11YIssue());
                Log.i(AccessibilityUtil.TAG+"_RESULT", message);
                myWriter.write(message+'\n');
            }
            String message = String.format("Result: %s $ Steps: %d $ Completed: %d $ Failed: %d $ Unlocatable: %d $ Unreachable: %d $ FirstProblem: %d $ ReportedA11y: %d $ TotalEvents: %d $ TotalTime: %d",
                    completeCount == commandList.size(),
                    commandList.size(),
                    completeCount,
                    failedCount,
                    unlocatedCount,
                    unreachableCount,
                    firstProbelmaticCommand,
                    reportedA11yIssues,
                    totalEvents,
                    totalTime);
            Log.i(AccessibilityUtil.TAG+"_RESULT", message);
            myWriter.write(message+'\n');
            myWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.i(AccessibilityUtil.TAG+"_RESULT", "Error: " + e.getMessage());
        }
    }

    public static void setLastCommandIndex(int lastCommandIndex) {
        CommandManager.lastCommandIndex = lastCommandIndex;
        Log.i(AccessibilityUtil.TAG, "CMD: the command index is " + lastCommandIndex);
    }

    public static int executeCommand(Command command){
        if(testExecutor == null)
            return RegularCommandExecutor.executeCommand(command);
        return testExecutor.executeCommand(command);
    }

    public static int reportA11yIssues(){
        AccessibilityNodeInfo root = SwitchAccessService.getInstance().getRootInActiveWindow();
        if(a11yIssueReporter == null){
            return AccessibilityUtil.getA11yIssues(root).size();
        }
        return a11yIssueReporter.getA11yIssues(root).size();
    }
}
