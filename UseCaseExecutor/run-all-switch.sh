for f in `ls ../NoidAccessibility/TransDroid/test-guidelines/*.json`; do
	echo $f
	./run-switch.sh $f
done
