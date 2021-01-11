#!/bin/bash
CMD=$1
CMD_FILE=/data/local/tmp/command.txt
echo "Set command to $CMD"
adb exec-out touch $CMD_FILE
adb exec-out sh -c "echo '$CMD' > $CMD_FILE"

