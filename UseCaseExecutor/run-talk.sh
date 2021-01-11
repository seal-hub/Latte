#!/bin/bash
TEST_GUIDELINE=$1
#adb shell settings put secure enabled_accessibility_services com.google.android.accessibility.talkback/com.android.switchaccess.SwitchAccessService:com.google.android.accessibility.talkback/com.google.android.marvin.talkback.TalkBackService
adb shell settings put secure enabled_accessibility_services com.google.android.accessibility.talkback/com.android.switchaccess.SwitchAccessService:com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService

sleep 1
./run-common.sh $TEST_GUIDELINE
sleep 2
./send-command.sh init
sleep 2
#./send-command.sh mask_context_xpath_resourceId
#./send-command.sh mask_xpath_resourceId
./send-command.sh mask_context_xpath_resourceId
sleep 2
./send-command.sh executor_talk
sleep 2
#./send-command.sh A11yReport_false
#sleep 2
./send-command.sh delay_2000
sleep 5
./send-command.sh start
./run-post.sh `basename $TEST_GUIDELINE` "talk"
