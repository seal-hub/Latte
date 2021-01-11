#!/bin/bash
TEST_GUIDELINE=$1
adb shell settings put secure enabled_accessibility_services com.google.android.accessibility.talkback/com.android.switchaccess.SwitchAccessService
sleep 1
./run-common.sh $TEST_GUIDELINE
sleep 2
./send-command.sh init
sleep 1
./send-command.sh mask_context
sleep 1
./send-command.sh executor_switch
sleep 1
./send-command.sh delay_2000
sleep 3
./send-command.sh start
./run-post.sh `basename $TEST_GUIDELINE` "switch"
